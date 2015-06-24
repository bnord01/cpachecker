/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2014  Dirk Beyer
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

import static com.google.common.base.Predicates.*;
import static com.google.common.collect.Collections2.filter;
import static com.google.common.collect.FluentIterable.from;
import static org.sosy_lab.cpachecker.util.AbstractStates.extractStateByType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;


import javax.annotation.Nullable;

import org.sosy_lab.common.Pair;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.ast.AArraySubscriptExpression;
import org.sosy_lab.cpachecker.cfa.ast.AAssignment;
import org.sosy_lab.cpachecker.cfa.ast.ADeclaration;
import org.sosy_lab.cpachecker.cfa.ast.AExpression;
import org.sosy_lab.cpachecker.cfa.ast.AExpressionAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.AExpressionStatement;
import org.sosy_lab.cpachecker.cfa.ast.AFunctionCallAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.AFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.AFunctionDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.ALeftHandSide;
import org.sosy_lab.cpachecker.cfa.ast.AParameterDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.ASimpleDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.AStatement;
import org.sosy_lab.cpachecker.cfa.ast.AVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.model.ADeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.AReturnStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.AStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.AssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.BlankEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.defaults.ForwardingTransferRelation;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.AbstractStateWithLocation;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.cpa.livevar.DeclarationCollectingVisitor;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;

import com.google.common.base.Equivalence.Wrapper;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

//@Options(prefix="cpa.ifc")
public class InformationFlowTransferRelation extends ForwardingTransferRelation<Collection<InformationFlowState>,InformationFlowState,InformationFlowPrecision> {

  //@Option(secure=true, description = "The name of variable containing the secret information.")
  private String highVariable = "h";
  //@Option(secure=true, description = "The name of variable visible to the public.")
  private String lowVariable = "l";

  private TransferRelation childTR;
  private Map<CFANode,Set<CFANode>> postDominators;
  private InformationFlowStateType stateType;
  private boolean IS_INITIAL_STATE = false;
  private boolean IS_DATA_DEP_STATE = false;
  private boolean IS_CONTROL_DEP_STATE = false;
  private ControlDependencyState controlDependencyState;
  private SimpleDataDependencyState simpleDataDependencyState;
  private InitialFlowState initialFlowState;
  private LogManager logger;

  public InformationFlowTransferRelation(TransferRelation child, Map<CFANode,Set<CFANode>> pPostDominators,LogManager pLogger){
    this.logger = pLogger;
    this.childTR = child;
    this.postDominators = pPostDominators;
  }

  @Override
  protected void setInfo(AbstractState abstractState, Precision abstractPrecision, CFAEdge cfaEdge) {
    super.setInfo(abstractState, abstractPrecision, cfaEdge);
    stateType = state.getType();
    switch (stateType) {
      case INITIAL_STATE:
        initialFlowState = (InitialFlowState) state;
        IS_INITIAL_STATE = true;
        break;
      case SIMPLE_DATA_DEP_STATE:
        simpleDataDependencyState = (SimpleDataDependencyState) state;
        IS_DATA_DEP_STATE = true;
        break;
      case CONTROL_DEP_TYPE:
        controlDependencyState = (ControlDependencyState) state;
        IS_CONTROL_DEP_STATE = true;
        break;
      default:
        throw new IllegalArgumentException("Unknown state type: " + stateType);
    }
  }
  @Override
  protected void resetInfo() {
    super.resetInfo();
    stateType = null;
    initialFlowState = null;
    simpleDataDependencyState = null;
    controlDependencyState = null;
    IS_INITIAL_STATE = false;
    IS_DATA_DEP_STATE = false;
    IS_CONTROL_DEP_STATE = false;
  }


  @Override
  protected Collection<InformationFlowState> handleStatementEdge(AStatementEdge cfaEdge, AStatement statement)
      throws CPATransferException {
    if (statement instanceof AExpressionAssignmentStatement) {
      return handleAssignments((AAssignment) statement);
    } else {
      throw new CPATransferException("Missing case for statement: " + statement);
    }
  }

  @Override
  protected Collection<InformationFlowState> handleAssumption(AssumeEdge cfaEdge, AExpression expression, boolean truthAssumption)
      throws CPATransferException {
    Collection<InformationFlowState> res = Sets.newHashSet();
    if(readsCritical(expression)) {
      res.add(new ControlDependencyState(state.getWrappedState(),cfaEdge.getPredecessor()));
    }
    if(!IS_CONTROL_DEP_STATE || postDominators.get(controlDependencyState.getControllingNode()).contains(cfaEdge.getSuccessor())){
      res.add(state);
    }
    return res;
  }

  private Collection<InformationFlowState> handleAssignments(AAssignment assignment) {
    final Collection<Wrapper<ASimpleDeclaration>> usedVariables = Sets.newHashSet();
    final ALeftHandSide leftHandSide = assignment.getLeftHandSide();
    final Collection<Wrapper<ASimpleDeclaration>> assignedVariable = handleLeftHandSide(leftHandSide);
    final Collection<Wrapper<ASimpleDeclaration>> allLeftHandSideVariables = handleExpression(leftHandSide);
    final Collection<Wrapper<ASimpleDeclaration>> additionallyLeftHandSideVariables = filter(allLeftHandSideVariables, not(in(assignedVariable)));

    // all variables that occur in combination with the leftHandSide additionally
    // to the needed one (e.g. a[i] i is additionally) are added to the usedVariables
    usedVariables.addAll(additionallyLeftHandSideVariables);

    // check all variables of the rightHandsides, they should be live afterwards
    // if the leftHandSide is live
    if (! (assignment instanceof AExpressionAssignmentStatement)) {
      throw new AssertionError("Unhandled assignment type.");
    }

    usedVariables.addAll(handleExpression((AExpression)assignment.getRightHandSide()));

    if (assignedVariable.size() != 1) {
      throw new AssertionError("More than one variable assigned in " + assignment);
    }

    Wrapper<ASimpleDeclaration> variable = assignedVariable.iterator().next();

    Collection<InformationFlowState> resultStates = Sets.newHashSet();

    if(
        (IS_DATA_DEP_STATE && usedVariables.contains(simpleDataDependencyState.getVariable())) ||
            (isControlDependent())){
      resultStates.add(
          new SimpleDataDependencyState(state.getWrappedState(),variable));
    }

    if(!IS_DATA_DEP_STATE || !variable.equals(simpleDataDependencyState.getVariable())) {
      resultStates.add(state);
    }


    return resultStates;

    //else if (leftHandSide instanceof CFieldReference
      //  || leftHandSide instanceof AArraySubscriptExpression
      //  || leftHandSide instanceof CPointerExpression) {
      //

      //leftHandSide instanceof CFieldReference
      //  && (((CFieldReference)leftHandSide).isPointerDereference()
      //  || ((CFieldReference)leftHandSide).getFieldOwner() instanceof CPointerExpression))
      //  || leftHandSide instanceof AArraySubscriptExpression
      //  || leftHandSide instanceof CPointerExpression) {

  }

  private boolean isControlDependent() {
    return IS_CONTROL_DEP_STATE &&
        !(postDominators.get(controlDependencyState.getControllingNode()).contains(edge.getPredecessor()));
  }

  private boolean isLow(Wrapper<ASimpleDeclaration> var) {
    return lowVariable.equals(var.get().getName());
  }

  private boolean isHigh(Wrapper<ASimpleDeclaration> var) {
    return highVariable.equals(var.get().getName());
  }

  private boolean readsCritical(AExpression expression) {
    Collection<Wrapper<ASimpleDeclaration>> vars = handleExpression(expression);
    if(IS_DATA_DEP_STATE && vars.contains(simpleDataDependencyState.getVariable()))
      return true;
    if(IS_INITIAL_STATE && from(vars).anyMatch(new Predicate<Wrapper<ASimpleDeclaration>>(){
      @Override
      public boolean apply(Wrapper<ASimpleDeclaration> s) {
        return isHigh(s);
      }
    })) {
      return true;
    }
    return false;
  }

  @Override
  protected Collection<InformationFlowState> handleDeclarationEdge(ADeclarationEdge cfaEdge, ADeclaration decl)
      throws CPATransferException {
    Collection<InformationFlowState> res = Sets.newHashSet();
    //TODO check if flow ends here!
    res.add(state);

    if(IS_INITIAL_STATE) {
      if (decl instanceof AVariableDeclaration && highVariable.equals(decl.getName())) {
        res.add(new SimpleDataDependencyState(state.getWrappedState(), decl));
      }
      if(decl instanceof AFunctionDeclaration) {
        AFunctionDeclaration fdecl = (AFunctionDeclaration) decl;
        res.addAll(from(fdecl.getParameters()).filter(new Predicate<AParameterDeclaration>(){
          @Override
          public boolean apply(AParameterDeclaration pAParameterDeclaration) {
            return highVariable.equals(pAParameterDeclaration.getName());
          }
        }).transform(new Function<AParameterDeclaration, InformationFlowState>() {
          @Nullable
          @Override
          public InformationFlowState apply(AParameterDeclaration pAParameterDeclaration) {
            return new SimpleDataDependencyState(state.getWrappedState(),SimpleDataDependencyState.DECL_EQUIVALENCE.wrap(
                (ASimpleDeclaration)pAParameterDeclaration));
          }
        }).toSet());
      }
    }


    return res;

  }

  @Override
  protected Collection<InformationFlowState> handleBlankEdge(BlankEdge cfaEdge) {
    if(IS_CONTROL_DEP_STATE && postDominators.get(controlDependencyState.getControllingNode()).contains(cfaEdge.getSuccessor())) {
      return Sets.newHashSet();
    } else {
      return Collections.singleton(state);
    }
  }

  @Override
  protected Collection<InformationFlowState> handleReturnStatementEdge(AReturnStatementEdge cfaEdge)
      throws CPATransferException {
    if(IS_DATA_DEP_STATE && cfaEdge.getExpression().isPresent() && readsCritical(cfaEdge.getExpression().get())) {
      return Collections.<InformationFlowState>singleton(new SimpleDataDependencyState(state.getWrappedState(),simpleDataDependencyState.getVariable(),true));
    } else if (IS_CONTROL_DEP_STATE) {
      return Collections.<InformationFlowState>singleton(new ControlDependencyState(state.getWrappedState(),controlDependencyState.getControllingNode(),true));
    }
    else return Collections.EMPTY_LIST;
  }

  @Override
  protected Collection<InformationFlowState> postProcessing(Collection<InformationFlowState> successors) {
    Collection<? extends AbstractState> childSuccessors = null;
    try {


      childSuccessors = childTR.getAbstractSuccessorsForEdge(
          state.getWrappedState(), precision.getWrappedPrecision(), edge);

      Collection<InformationFlowState> res = Sets.newHashSet();


      for(InformationFlowState s : successors){
        for(AbstractState childState : childSuccessors) {
          InformationFlowState informationFlowState = s.copyWith(childState);
          Collection<? extends AbstractState>
              strengthendChilds = childTR.strengthen(childState, Lists.<AbstractState>newArrayList(informationFlowState), edge,
              precision.getWrappedPrecision());
          if(strengthendChilds == null) {
            res.add(informationFlowState);
          }else {
            res.addAll(Collections2.transform(strengthendChilds, informationFlowState.getRewrapFunction()));
          }
        }
      }

      return res;

    } catch (CPATransferException e) {
      throw new RuntimeException(e);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }


  /**
   * Returns a collection of all variable names which occur in expression
   */
  private Collection<Wrapper<ASimpleDeclaration>> handleExpression(AExpression expression) {
    return from(acceptAll(expression)).transform(SimpleDataDependencyState.TO_EQUIV_WRAPPER).toSet();
  }

  /**
   * Returns a collection of the variable names in the leftHandSide
   */
  private Collection<Wrapper<ASimpleDeclaration>> handleLeftHandSide(AExpression pLeftHandSide) {
    return from(acceptLeft(pLeftHandSide)).transform(SimpleDataDependencyState.TO_EQUIV_WRAPPER).toSet();
  }

  /**
   * This is a more specific version of the CIdExpressionVisitor. For ArraySubscriptexpressions
   * we do only want the IdExpressions inside the ArrayExpression.
   */
  private static final class LeftHandSideIdExpressionVisitor extends DeclarationCollectingVisitor {
    @Override
    public Set<ASimpleDeclaration> visit(AArraySubscriptExpression pE) {
      return pE.getArrayExpression().<Set<ASimpleDeclaration>,
          Set<ASimpleDeclaration>,
          Set<ASimpleDeclaration>,
          RuntimeException,
          RuntimeException,
          LeftHandSideIdExpressionVisitor>accept_(this);
    }
  }

  private static Set<ASimpleDeclaration> acceptLeft(AExpression exp) {
    return exp.<Set<ASimpleDeclaration>,
        Set<ASimpleDeclaration>,
        Set<ASimpleDeclaration>,
        RuntimeException,
        RuntimeException,
        LeftHandSideIdExpressionVisitor>accept_(new LeftHandSideIdExpressionVisitor());
  }

  private static Set<ASimpleDeclaration> acceptAll(AExpression exp) {
    return exp.<Set<ASimpleDeclaration>,
        Set<ASimpleDeclaration>,
        Set<ASimpleDeclaration>,
        RuntimeException,
        RuntimeException,
        DeclarationCollectingVisitor>accept_(new DeclarationCollectingVisitor());
  }


  @Override
  public Collection<InformationFlowState> strengthen(AbstractState pElement, List<AbstractState> pOtherElements,
      CFAEdge pCfaEdge, Precision pPrecision) throws CPATransferException, InterruptedException {

    assert pElement instanceof InformationFlowState: "pElement not instance of InformationFlowState";
    InformationFlowState e = (InformationFlowState) pElement;
    InformationFlowPrecision p = (InformationFlowPrecision)pPrecision;

    Collection<? extends AbstractState> fs = childTR.strengthen(
        e.getWrappedState(), pOtherElements, pCfaEdge, p);


    return Collections2.transform(fs, e.getRewrapFunction());
  }

  @Override
  public Collection<InformationFlowState> getAbstractSuccessors(AbstractState pElement, Precision pPrecision)
      throws CPATransferException, InterruptedException {

    AbstractStateWithLocation locState = extractStateByType(pElement, AbstractStateWithLocation.class);
    if (locState == null) {
      throw new CPATransferException("Analysis without any CPA tracking locations is not supported, please add one to the configuration (e.g., LocationCPA).");
    }

    Collection<InformationFlowState> res = new ArrayList<>(2);

    for (CFAEdge edge : locState.getOutgoingEdges()) {
      logger.log(Level.INFO, "Handeling edge %s", edge);
      Collection <InformationFlowState> succs = getAbstractSuccessorsForEdge(pElement, pPrecision, edge);
      res.addAll(succs);
      logger.log(Level.INFO, "Got successors: %s", succs);
    }

    return res;
  }

}