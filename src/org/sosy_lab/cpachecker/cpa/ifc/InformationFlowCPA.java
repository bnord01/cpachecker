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
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import org.sosy_lab.common.Pair;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.ConfigurationBuilder;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Options;
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
import org.sosy_lab.cpachecker.cpa.ifc.dcd.DCDCollectorCPA;
import org.sosy_lab.cpachecker.cpa.ifc.dcd.DCDCollectorState;
import org.sosy_lab.cpachecker.cpa.ifc.dcd.PostDominators;
import org.sosy_lab.cpachecker.cpa.location.LocationState;
import org.sosy_lab.cpachecker.exceptions.CPAEnabledAnalysisPropertyViolationException;
import org.sosy_lab.cpachecker.exceptions.CPAException;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.common.io.CharSource;

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
  private SetMultimap<CFANode,CFANode> postDominators;
  private LogManager logger;
  private ShutdownNotifier shutdownNotifier;
  private Set<Pair<CFANode,Variable>> dcd;

  public InformationFlowCPA(ConfigurableProgramAnalysis cpa, CFA pCFA, Configuration config, LogManager pLogger, ShutdownNotifier pShutdownNotifier)
      throws InvalidConfigurationException, CPAException {
    super(cpa);
    this.cpa = cpa;
    this.cfa = pCFA;
    this.logger = pLogger;
    this.shutdownNotifier = pShutdownNotifier;
    this.configuration = config;
    this.domain = new InformationFlowDomain(this.cpa.getAbstractDomain());
    this.precisionAdjustment = new InformationFlowPrecisionAdjustment(cpa.getPrecisionAdjustment());
    this.mergeOperator = new InformationFlowMergeOperator(cpa.getMergeOperator());
    this.stopOperator = new InformationFlowStopOperator(cpa.getStopOperator());
    this.postDominators = computePostDominators();
    this.dcd = computeDCDs();
    logger.log(Level.INFO,"Data control dependencies: ",dcd);
    this.transferRelation = new InformationFlowTransferRelation(config,this.cpa.getTransferRelation(),postDominators,logger);


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
    return new InformationFlowPrecision(cpa.getInitialPrecision(node, partition));
  }

  private SetMultimap<CFANode,CFANode> computePostDominators() throws InvalidConfigurationException, CPAException {
    try {
    String pdconf = "cpa = cpa.composite.CompositeCPA\n"
        + "CompositeCPA.cpas = cpa.dominator.PostDominatorCPA\n";
    Configuration pdconfig = Configuration.builder().loadFromSource(
        CharSource.wrap(pdconf), "", "PostDominatorConf").build();
    CPABuilder cpaBuilder = new CPABuilder(pdconfig,logger,shutdownNotifier,null);
    ConfigurableProgramAnalysis ccpa = cpaBuilder.buildCPAs(cfa, Lists.<Path>newArrayList());
    Algorithm p = CPAAlgorithm.create(ccpa, logger, pdconfig, shutdownNotifier);

    LocationMappedReachedSet l = new LocationMappedReachedSet(TraversalMethod.BFS);
    CFANode exit = cfa.getMainFunction().getExitNode();
    l.add(ccpa.getInitialState(exit, null),
        ccpa.getInitialPrecision(exit, null));

    p.run(l);
    SetMultimap<CFANode,CFANode> pds = HashMultimap.create();
    for( CFANode n : l.getLocations()) {
      for(AbstractState s: l.getReached(n)) {
        assert s instanceof CompositeState;
        Iterator it = ((DominatorState) ((CompositeState)s).get(0) ).getIterator();
        while(it.hasNext()) {
          LocationState pd = (LocationState) it.next();
          pds.put(n,pd.getLocationNode());
        }
      }
    }
    return pds;
    } catch (InterruptedException e) {
      throw new CPAException("Error computing postdominators",e);
    } catch (IOException e) {
      throw new CPAException("Error computing postdominators",e);
    }
  }

  private Set<Pair<CFANode,Variable>> computeDCDs() throws InvalidConfigurationException, CPAException {
    try {
      String dcdconf = "cpa = cpa.composite.CompositeCPA\n"
          + "CompositeCPA.cpas = cpa.location.LocationCPA, cpa.ifc.dcd.DCDCollectorCPA\n";
      Configuration config = Configuration.builder().loadFromSource(
          CharSource.wrap(dcdconf), "", "DCDCollectorConf").build();
      DCDCollectorCPA.postDominators = new PostDominators(postDominators);
      CPABuilder dcdcpaBuilder = new CPABuilder(config,logger,shutdownNotifier,null);
      ConfigurableProgramAnalysis ccpa = dcdcpaBuilder.buildCPAs(cfa, Lists.<Path>newArrayList());
      Algorithm p = CPAAlgorithm.create(ccpa, logger, config, shutdownNotifier);

      LocationMappedReachedSet l = new LocationMappedReachedSet(TraversalMethod.BFS);
      CFANode entry = cfa.getMainFunction();
      l.add(ccpa.getInitialState(entry, null),
          ccpa.getInitialPrecision(entry, null));

      p.run(l);
      for(AbstractState s: l.getReached(cfa.getMainFunction().getExitNode())) {
        assert s instanceof CompositeState;
        DCDCollectorState finalState = ((DCDCollectorState)((CompositeState)s).get(1));
        return finalState.getDCDs();
      }
      throw new CPAException("Error computing DCDs, didn't find final state.");
    } catch (InterruptedException e) {
      throw new CPAException("Error computing DCDs",e);
    } catch (IOException e) {
      throw new CPAException("Error computing DCDs",e);
    }
  }
}
