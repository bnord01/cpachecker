/*
 * CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2015  Dirk Beyer
 *  All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 *  CPAchecker web page:
 *    http://cpachecker.sosy-lab.org
 */
package org.sosy_lab.cpachecker.cpa.ifc;


import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.ConfigurationBuilder;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.io.Path;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.CPABuilder;
import org.sosy_lab.cpachecker.core.algorithm.Algorithm;
import org.sosy_lab.cpachecker.core.algorithm.Algorithm.AlgorithmStatus;
import org.sosy_lab.cpachecker.core.algorithm.CPAAlgorithm;
import org.sosy_lab.cpachecker.core.defaults.AbstractCPAFactory;
import org.sosy_lab.cpachecker.core.defaults.AbstractSingleWrapperCPA;
import org.sosy_lab.cpachecker.core.defaults.AutomaticCPAFactory;
import org.sosy_lab.cpachecker.core.defaults.MergeJoinOperator;
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
import org.sosy_lab.cpachecker.core.interfaces.StateSpacePartition;
import org.sosy_lab.cpachecker.core.interfaces.StopOperator;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.core.reachedset.LocationMappedReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSetFactory;
import org.sosy_lab.cpachecker.core.waitlist.Waitlist.TraversalMethod;
import org.sosy_lab.cpachecker.cpa.composite.CompositeCPA;
import org.sosy_lab.cpachecker.cpa.composite.CompositeState;
import org.sosy_lab.cpachecker.cpa.dominator.PostDominatorCPA;
import org.sosy_lab.cpachecker.cpa.dominator.parametric.DominatorState;
import org.sosy_lab.cpachecker.cpa.location.LocationState;
import org.sosy_lab.cpachecker.exceptions.CPAException;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.CharSource;
import com.sun.tools.javac.util.List;

/**
 * Implements an information flow analysis
 */
public class InformationFlowCPA extends AbstractSingleWrapperCPA {

  public static CPAFactory factory() {
    return AutomaticCPAFactory.forType(InformationFlowCPA.class);
  }
  private ConfigurableProgramAnalysis cpa;
  private CFA cfa;
  private Configuration configuration;
  private InformationFlowDomain domain;
  private InformationFlowTransferRelation transferRelation;
  private InformationFlowPrecisionAdjustment precisionAdjustment;
  private InformationFlowMergeOperator mergeOperator;
  private InformationFlowStopOperator stopOperator;
  private Map<CFANode,Set<CFANode>> postDominators;

  public InformationFlowCPA(ConfigurableProgramAnalysis cpa, CFA pCFA, Configuration config, LogManager logger, ShutdownNotifier pShutdownNotifier)
      throws InvalidConfigurationException, CPAException {
    super(cpa);
    this.cpa = cpa;
    this.cfa = pCFA;
    this.configuration = config;
    this.domain = new InformationFlowDomain(this.cpa.getAbstractDomain());
    this.precisionAdjustment = new InformationFlowPrecisionAdjustment(cpa.getPrecisionAdjustment());
    this.mergeOperator = new InformationFlowMergeOperator(cpa.getMergeOperator());
    this.stopOperator = new InformationFlowStopOperator(cpa.getStopOperator());
    this.postDominators = Maps.newHashMap();
    try {

      String pdconf = "cpa = cpa.composite.CompositeCPA\n"
          + "CompositeCPA.cpas = cpa.dominator.PostDominatorCPA\n";
      Configuration pdconfig = Configuration.builder().loadFromSource(
          CharSource.wrap(pdconf), "", "PostDominatorConf").build();
      CPABuilder cpaBuilder = new CPABuilder(pdconfig,logger,pShutdownNotifier,null);
      ConfigurableProgramAnalysis ccpa = cpaBuilder.buildCPAs(pCFA, List.<Path>nil());
      Algorithm p = CPAAlgorithm.create(ccpa, logger, pdconfig, pShutdownNotifier);

      LocationMappedReachedSet l = new LocationMappedReachedSet(TraversalMethod.DFS);
      CFANode exit = pCFA.getMainFunction().getExitNode();
      l.add(ccpa.getInitialState(exit, null),
          ccpa.getInitialPrecision(exit, null));

      p.run(l);

      for( CFANode n : l.getLocations()) {
        Set<CFANode> pds = Sets.newHashSet();
        for(AbstractState s: l.getReached(n)) {
          assert s instanceof CompositeState;
          Iterator it = ((DominatorState) ((CompositeState)s).get(0) ).getIterator();
          while(it.hasNext()) {
            LocationState pd = (LocationState) it.next();
            pds.add(pd.getLocationNode());
          }
        }
        postDominators.put(n,pds);
      }

      this.transferRelation = new InformationFlowTransferRelation(this.cpa.getTransferRelation(),postDominators,logger);

    } catch (InterruptedException e) {
      throw new CPAException("Error computing postdominators",e);
    } catch (IOException e) {
      throw new CPAException("Error computing postdominators",e);
    }
  }

  @Override
  public InformationFlowDomain getAbstractDomain() {
    return domain;
  }

  @Override
  public InformationFlowTransferRelation getTransferRelation() {
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
  public AbstractState getInitialState(CFANode node,
      StateSpacePartition partition) {
    return new InitialFlowState(this.cpa.getInitialState(node,partition));
  }

  @Override
  public Precision getInitialPrecision(CFANode node,
      StateSpacePartition partition) {
    return new InformationFlowPrecision(cpa.getInitialPrecision(node,partition));
  }
}
