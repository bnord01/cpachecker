package org.sosy_lab.cpachecker.cpa.policyiteration;

import java.util.Collection;
import java.util.List;

import javax.annotation.Nullable;

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.AnalysisDirection;
import org.sosy_lab.cpachecker.core.ShutdownNotifier;
import org.sosy_lab.cpachecker.core.defaults.AutomaticCPAFactory;
import org.sosy_lab.cpachecker.core.defaults.MergeJoinOperator;
import org.sosy_lab.cpachecker.core.defaults.SingleEdgeTransferRelation;
import org.sosy_lab.cpachecker.core.defaults.SingletonPrecision;
import org.sosy_lab.cpachecker.core.defaults.StaticPrecisionAdjustment;
import org.sosy_lab.cpachecker.core.defaults.StopSepOperator;
import org.sosy_lab.cpachecker.core.interfaces.AbstractDomain;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.CPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.MergeOperator;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustment;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.StatisticsProvider;
import org.sosy_lab.cpachecker.core.interfaces.StopOperator;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.predicates.FormulaManagerFactory;
import org.sosy_lab.cpachecker.util.predicates.interfaces.FormulaManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.PathFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.view.FormulaManagerView;
import org.sosy_lab.cpachecker.util.predicates.pathformula.PathFormulaManagerImpl;

/**
 * New version of policy iteration, now with path focusing.
 */
@Options(prefix="cpa.policy")
public class PolicyCPA implements ConfigurableProgramAnalysis, StatisticsProvider {
  private final AbstractDomain abstractDomain;
  private final TransferRelation transferRelation;
  private final MergeOperator mergeOperator;
  private final StopOperator stopOperator;
  private final PrecisionAdjustment precisionAdjustment;
  private final PolicyIterationStatistics statistics;
  private final IPolicyIterationManager policyIterationManager;

  public static class DelegateAbstractDomain implements AbstractDomain {
    private final IPolicyIterationManager policyIterationManager;

    public DelegateAbstractDomain(IPolicyIterationManager pPolicyIterationManager) {
      policyIterationManager = pPolicyIterationManager;
    }

    @Override
    public AbstractState join(AbstractState state1, AbstractState state2)
        throws CPAException, InterruptedException {
     return policyIterationManager.join(
         (PolicyState) state1,
         (PolicyState) state2
     );
    }

    @Override
    public boolean isLessOrEqual(AbstractState state1, AbstractState state2)
        throws CPAException, InterruptedException {
      return policyIterationManager.isLessOrEqual(
          (PolicyState) state1,
          (PolicyState) state2
      );
    }
  }
  
  public static class DelegateTransferRelation extends SingleEdgeTransferRelation {
    private final IPolicyIterationManager policyIterationManager;

    public DelegateTransferRelation(
        IPolicyIterationManager pPolicyIterationManager) {
      policyIterationManager = pPolicyIterationManager;

    }


    @Override
    public Collection<? extends AbstractState> getAbstractSuccessorsForEdge(
        AbstractState state, Precision precision, CFAEdge cfaEdge)
        throws CPATransferException, InterruptedException {
      return policyIterationManager.getAbstractSuccessors(
          (PolicyState) state, cfaEdge
      );
    }

    @Override
    public Collection<? extends AbstractState> strengthen(AbstractState state,
        List<AbstractState> otherStates, @Nullable CFAEdge cfaEdge,
        Precision precision) throws CPATransferException, InterruptedException {
      return policyIterationManager.strengthen(
          (PolicyState) state, otherStates, cfaEdge);
    }
  }

  public static CPAFactory factory() {
    return AutomaticCPAFactory.forType(PolicyCPA.class);
  }

  @SuppressWarnings("unused")
  private PolicyCPA(
      Configuration config,
      LogManager logger,
      ShutdownNotifier shutdownNotifier,
      CFA cfa
  ) throws InvalidConfigurationException {
    config.inject(this);

    FormulaManagerFactory formulaManagerFactory = new FormulaManagerFactory(
        config, logger, shutdownNotifier);

    FormulaManager realFormulaManager = formulaManagerFactory.getFormulaManager();
    FormulaManagerView formulaManager = new FormulaManagerView(
        realFormulaManager, config, logger);
    PathFormulaManager pathFormulaManager = new PathFormulaManagerImpl(
        formulaManager, config, logger, shutdownNotifier, cfa,
        AnalysisDirection.FORWARD);
    LinearConstraintManager lcmgr = new LinearConstraintManager(formulaManager,
        logger);
    TemplateManager templateManager = new TemplateManager(
        logger, config, cfa);

    statistics = new PolicyIterationStatistics(config);
    ValueDeterminationFormulaManager valueDeterminationFormulaManager =
        new ValueDeterminationFormulaManager(
            pathFormulaManager, formulaManager, config, logger,
            cfa,
            realFormulaManager,
            lcmgr,
            formulaManagerFactory,
            shutdownNotifier,
            statistics
        );
    
    policyIterationManager = new PolicyIterationManager(
        config,
        formulaManager,
        cfa, pathFormulaManager, lcmgr,
        formulaManager.getBooleanFormulaManager(),
        formulaManagerFactory, logger, shutdownNotifier,
        formulaManager.getRationalFormulaManager(),
        templateManager, valueDeterminationFormulaManager,
        statistics);

    abstractDomain = new DelegateAbstractDomain(policyIterationManager);
    transferRelation = new DelegateTransferRelation(policyIterationManager);

    mergeOperator = new MergeJoinOperator(abstractDomain);
    stopOperator = new StopSepOperator(abstractDomain);
    precisionAdjustment = StaticPrecisionAdjustment.getInstance();
    
  }

  @Override
  public AbstractState getInitialState(CFANode node) {
    return policyIterationManager.getInitialState(node);
  }

  @Override
  public AbstractDomain getAbstractDomain() {
    return abstractDomain;
  }

  @Override
  public TransferRelation getTransferRelation() {
    return transferRelation;
  }

  @Override
  public MergeOperator getMergeOperator() {
    return mergeOperator;
  }

  @Override
  public StopOperator getStopOperator() {
    return stopOperator;
  }

  @Override
  public PrecisionAdjustment getPrecisionAdjustment() {
    return precisionAdjustment;
  }

  @Override
  public Precision getInitialPrecision(CFANode node) {
    return SingletonPrecision.getInstance();
  }

  @Override
  public void collectStatistics(Collection<Statistics> statsCollection) {
    statsCollection.add(statistics);
  }
}
