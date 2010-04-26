/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker. 
 *
 *  Copyright (C) 2007-2010  Dirk Beyer
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
package org.sosy_lab.cpachecker.fllesh.fql.fllesh.reachability;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.Set;

import org.sosy_lab.common.LogManager;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFANode;

import org.sosy_lab.cpachecker.cpa.composite.CompositeCPA;
import org.sosy_lab.cpachecker.cpa.composite.CompositeElement;
import org.sosy_lab.cpachecker.cpa.composite.CompositePrecision;

import org.sosy_lab.cpachecker.core.ReachedElements;
import org.sosy_lab.cpachecker.core.algorithm.CPAAlgorithm;
import org.sosy_lab.cpachecker.core.interfaces.AbstractElement;
import org.sosy_lab.cpachecker.core.interfaces.CPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.ReachedElements.TraversalMethod;
import org.sosy_lab.cpachecker.cpa.location.LocationCPA;
import org.sosy_lab.cpachecker.cpa.mustmay.MustMayAnalysisCPA;
import org.sosy_lab.cpachecker.cpa.mustmay.MustMayAnalysisElement;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.fllesh.fql.backend.pathmonitor.Automaton;
import org.sosy_lab.cpachecker.fllesh.fql.fllesh.cpa.QueryCPA;
import org.sosy_lab.cpachecker.fllesh.fql.fllesh.cpa.QueryStandardElement;

public class StandardQuery extends AbstractQuery {

  private LinkedList<Waypoint> lNextWaypoints;

  // TODO: configure in the Factory the data space analyses such that they can be
  // given from outside
  public static class Factory {
    private LogManager mLogManager;
    
    private MustMayAnalysisCPA mMustMayAnalysisCPA;
    
    public Factory(LogManager pLogManager, ConfigurableProgramAnalysis pMustAnalysis, ConfigurableProgramAnalysis pMayAnalysis) {
      assert(pLogManager != null);
      assert(pMustAnalysis != null);
      assert(pMayAnalysis != null);
      
      mLogManager = pLogManager;
      
      mMustMayAnalysisCPA = new MustMayAnalysisCPA(pMustAnalysis, pMayAnalysis);
    }
    
    public StandardQuery create(Automaton pFirstAutomaton, Automaton pSecondAutomaton, CompositeElement pSourceElement, CompositePrecision pSourcePrecision, Set<Integer> pSourceStatesOfFirstAutomaton, Set<Integer> pSourceStatesOfSecondAutomaton, CFANode pCFANode, Set<Integer> pTargetStatesOfFirstAutomaton, Set<Integer> pTargetStatesOfSecondAutomaton) {
      StandardQuery lQuery = new StandardQuery(pFirstAutomaton, pSecondAutomaton, mLogManager, mMustMayAnalysisCPA);
      
      Waypoint lSource = new Waypoint(lQuery, pSourceElement, pSourcePrecision, pSourceStatesOfFirstAutomaton, pSourceStatesOfSecondAutomaton);
      
      // TODO support for predicates is missing
      // TODO support for call stack missing
      TargetPoint lTarget = new TargetPoint(pCFANode, pFirstAutomaton, pTargetStatesOfFirstAutomaton, pSecondAutomaton, pTargetStatesOfSecondAutomaton);
      
      lQuery.mSource = lSource;
      lQuery.mTarget = lTarget;
      
      return lQuery;
    }
  }
  
  private Waypoint mSource;
  private TargetPoint mTarget;
  
  private boolean mExplorationFinished;
  private ReachedElements mReachedElements;
  
  private ConfigurableProgramAnalysis mCPA;
  
  private LogManager mLogManager;
  
  private StandardQuery(Automaton pFirstAutomaton, Automaton pSecondAutomaton, LogManager pLogManager, MustMayAnalysisCPA pMustMayAnalysisCPA) {
    super(pFirstAutomaton, pSecondAutomaton);
    
    assert(pLogManager != null);
    
    mLogManager = pLogManager;
    
    lNextWaypoints = new LinkedList<Waypoint>();
    
    CPAFactory lCPAFactory = CompositeCPA.factory();
    
    CPAFactory lLocationCPAFactory = LocationCPA.factory();
    
    QueryCPA lQueryCPA = new QueryCPA(this, pMustMayAnalysisCPA);
    
    
    try {
      ConfigurableProgramAnalysis lLocationCPA = lLocationCPAFactory.createInstance();
      
      LinkedList<ConfigurableProgramAnalysis> lComponentAnalyses = new LinkedList<ConfigurableProgramAnalysis>();
      
      lComponentAnalyses.add(lLocationCPA);
      lComponentAnalyses.add(lQueryCPA);
      
      lCPAFactory.setChildren(lComponentAnalyses);
      
      mCPA = lCPAFactory.createInstance();
      
      
    } catch (Exception e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    
    // TODO what about other traversal types?
    mReachedElements = new ReachedElements(TraversalMethod.DFS, true);
  }
  
  public Waypoint getSource() {
    return mSource;
  }
  
  //public Waypoint getTarget() {
  public TargetPoint getTarget() {
    return mTarget;
  }
  
  private void explore() {
    
    if (mReachedElements.isEmpty()) {
      // initialization
      
      // TODO we can just use the precision of the source as currently QueryCPA has no own precision
      //Precision lLocationPrecision = mSource.getPrecision().get(0);
      //Precision lMustMayPrecision = mSource.getPrecision().get(1);
      
      mSource.getElement().get(0);
      
      MustMayAnalysisElement lDataSpace = (MustMayAnalysisElement)mSource.getElement().get(1);
      
      for (Integer lFirstState : mSource.getStatesOfFirstAutomaton()) {
        for (Integer lSecondState : mSource.getStatesOfSecondAutomaton()) {
          QueryStandardElement lNewElement = new QueryStandardElement(lFirstState, true, lSecondState, true, lDataSpace);
          
          LinkedList<AbstractElement> lContainedElements = new LinkedList<AbstractElement>();
          
          lContainedElements.add(mSource.getElement().get(0));
          
          lContainedElements.add(lNewElement);
          
          CompositeElement lCompositeElement = new CompositeElement(lContainedElements, mSource.getElement().getCallStack());
          
          mReachedElements.add(lCompositeElement, mSource.getPrecision());
        }
      }
      
      System.out.println("Initial elements: " + mReachedElements);
      
      // apply state space exploration
      CPAAlgorithm lAlgorithm = new CPAAlgorithm(mCPA, mLogManager);
      
      // TODO we should be able to use the error feature as a way to enumerate and refine elements in a different way than it is done now 
      try {
        lAlgorithm.run(mReachedElements, false);
      } catch (CPAException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      
      System.out.println("Explored elements: " + mReachedElements);
    }
    else {
      // refinement necessary 
      // TODO implement
      
      // apply state space exploration
      // TODO implement
      
    }
    
    // evaluate reached elements with regard to new waypoints
    // Here, the initial state can be treated as a waypoint, too. 
    // Do we need a set with already seen waypoints to exclude 
    // reinvestigating waypoints?
    
    
    // TODO derive from composite element in order to introduce a structure
    
    
    //Set<AbstractElement> lPotentialTargets = mReachedElements.getReached(mTarget.getElement());
    // TODO support for predicates is missing
    Set<AbstractElement> lPotentialTargets = mReachedElements.getReached(mTarget.getCFANode());
    
    //CallStack lTargetCallStack = mTarget.getElement().getCallStack();
    
    Set<QueryStandardElement> lPotentialTargetsForRefinement = new HashSet<QueryStandardElement>();
    Set<QueryStandardElement> lDefiniteTargets = new HashSet<QueryStandardElement>();
    
    for (AbstractElement lPotentialTarget : lPotentialTargets) {
      CompositeElement lElement = (CompositeElement)lPotentialTarget;
      
      QueryStandardElement lQueryElement = lElement.retrieveWrappedElement(QueryStandardElement.class);
      
      assert(lQueryElement != null);
      
      if (mTarget.satisfiesTarget(lQueryElement)) {
        if (lQueryElement.getMustState1() && lQueryElement.getMustState2()) {
          lDefiniteTargets.add(lQueryElement);
        }
        else {
          lPotentialTargetsForRefinement.add(lQueryElement);
        }
      }
    }
    
    
    // TODO process targets and potential targets for refinement and add to lNextWaypoints
    System.out.println("CFA node: " + mTarget.getCFANode());
    System.out.println("Definite Targets: " + lDefiniteTargets);
    System.out.println("Potential Targets: " + lPotentialTargetsForRefinement);
    
    
    if (lNextWaypoints.isEmpty()) {
      // we have not found any new waypoints
      mExplorationFinished = true;
    }
  }
  
  @Override
  public boolean hasNext() {
    if (mExplorationFinished && lNextWaypoints.isEmpty()) {
      return false;
    }
    else {
      if (lNextWaypoints.isEmpty()) {
        
        explore();
        
        return hasNext();        
      }
      else {
        return true;
      }
    }
  }

  @Override
  public Waypoint next() {
    
    if (lNextWaypoints.isEmpty()) {
      if (mExplorationFinished) {
        throw new NoSuchElementException();
      }
      
      explore();
      
      return next();
    }
    else {
      return lNextWaypoints.removeFirst();
    }
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException("remove is not supported!");
  }

}
