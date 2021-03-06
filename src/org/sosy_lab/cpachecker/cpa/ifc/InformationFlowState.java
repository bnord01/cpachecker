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


import static com.google.common.base.Preconditions.checkState;

import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.sosy_lab.cpachecker.cfa.ast.ASimpleDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.defaults.AbstractSingleWrapperState;

import org.sosy_lab.cpachecker.core.interfaces.AbstractState;

import com.google.common.base.Equivalence;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.base.Function;
import com.google.common.base.Predicate;

/**
 * Created by bnord on 15.06.15.
 */

enum InformationFlowStateType {
  INITIAL_STATE,SIMPLE_DATA_DEP_STATE,CONTROL_DEP_TYPE,DATA_CONTROL_DEP
}

abstract class InformationFlowState extends AbstractSingleWrapperState {

  public Function<AbstractState, InformationFlowState> getRewrapFunction() {
    return rewrapFunc;
  }

  private Function<AbstractState, InformationFlowState> rewrapFunc = new Function<AbstractState, InformationFlowState>() {
    public InformationFlowState apply(AbstractState pAbstractState) {
      return InformationFlowState.this.copyWith(pAbstractState);
    }
  };

  final static Function<AbstractState,AbstractState> unWrapFunc = new Function<AbstractState, AbstractState>() {
    public AbstractState apply(AbstractState pAbstractState) {
      return ((InformationFlowState)pAbstractState).getWrappedState();
    }
  };

  public Predicate<AbstractState> getEqualsExceptChildPredicate() {
    return equalsExceptChildPredicate;
  }

  private Predicate<AbstractState> equalsExceptChildPredicate = new Predicate<AbstractState>() {
    @Override
    public boolean apply(@Nullable AbstractState state) {
      return state instanceof InformationFlowState && equalsExceptChild((InformationFlowState)state);
    }
  };

  public InformationFlowState(AbstractState child) {
    super(child);
  }

  public InformationFlowState(AbstractState child,boolean pTarget) {
    super(child);
    this.target = pTarget;
  }

  public void setTarget(boolean pTarget) {
    this.target = pTarget;
  }
  private boolean target = false;

  @Override
  public boolean isTarget() {
    return target;
  }

  abstract public InformationFlowState copyWith(AbstractState newChild);

  abstract public InformationFlowStateType getType();

  abstract boolean equalsExceptChild(InformationFlowState other);

  @Override
  public String getViolatedPropertyDescription() throws IllegalStateException {
    checkState(isTarget());
    return "Illegal Information flow";
  }
}

class InitialFlowState extends InformationFlowState {
  public InitialFlowState(AbstractState child) {
    super(child);
  }
  private InitialFlowState(AbstractState child,boolean pTarget) {
    super(child, pTarget);
  }
  public InitialFlowState copyWith(AbstractState newChild) {
    return new InitialFlowState(newChild,isTarget());
  }
  public InformationFlowStateType getType() {return InformationFlowStateType.INITIAL_STATE;}

  @Override
  boolean equalsExceptChild(InformationFlowState other) {
    return other instanceof InitialFlowState && other.isTarget() == isTarget();
  }

  @Override
  public String toString(){
    return (isTarget()?"Targeted":"") + "InitialFlowState(child = " + super.toString() + ")";
  }

}

/**
 * State that tracks a control dependency from a CFANode
 */
class ControlDependencyState extends InformationFlowState {
  private CFANode controllingNode;
  public ControlDependencyState(AbstractState child, CFANode pControllingNode) {
    super(child);
    this.controllingNode = pControllingNode;
  }
  public ControlDependencyState(AbstractState child, CFANode pControllingNode, boolean pTarget) {
    super(child,pTarget);
    this.controllingNode = pControllingNode;
  }
  public ControlDependencyState copyWith(AbstractState newChild){
    return new ControlDependencyState(newChild, controllingNode,isTarget());
  }
  public CFANode getControllingNode() {
    return controllingNode;
  }
  public InformationFlowStateType getType() {return InformationFlowStateType.CONTROL_DEP_TYPE;}

  @Override
  boolean equalsExceptChild(InformationFlowState other) {
    return other instanceof ControlDependencyState &&
        getControllingNode().equals(((ControlDependencyState)other).getControllingNode()) &&
        other.isTarget() == isTarget();
  }

  @Override
  public String toString(){
    return (isTarget()?"Targeted":"") + "ControlDependencyState(controllingNode = " + controllingNode + " , child = " + super.toString() + ")";
  }


}




/**
 * State that tracks a data dependency on a simple variable.
 */
class SimpleDataDependencyState extends InformationFlowState {
  Variable variable;

  public SimpleDataDependencyState(AbstractState child,Variable pVariable) {
    super(child);
    this.variable = pVariable;
  }
  public SimpleDataDependencyState(AbstractState child,Variable pVariable,boolean pTarget) {
    super(child,pTarget);
    this.variable = pVariable;
  }
  public SimpleDataDependencyState(AbstractState child,ASimpleDeclaration pVariable) {
    this(child,new SimpleVariable(pVariable));
  }
  public SimpleDataDependencyState copyWith(AbstractState newChild) {
    return new SimpleDataDependencyState(newChild, variable,isTarget());
  }
  public InformationFlowStateType getType() {return InformationFlowStateType.SIMPLE_DATA_DEP_STATE;}

  @Override
  boolean equalsExceptChild(InformationFlowState other) {
    return other instanceof SimpleDataDependencyState &&
        getVariable().equals(((SimpleDataDependencyState)other).getVariable()) &&
        isTarget() == other.isTarget();
  }

  public Variable getVariable() {
    return variable;
  }

  @Override
  public String toString(){
    return (isTarget()?"Targeted":"") + "SimpleDataDependencyState(variable = " + variable + " , child = " + super.toString() + ")";
  }
}

class DataControlDependencyState extends InformationFlowState {
  Variable variable;
  Set<CFANode> pds;
  public DataControlDependencyState(AbstractState child, Variable pVariable, Set<CFANode> pPds) {
    super(child);
    this.variable = pVariable;
    this.pds = pPds;
    assert variable != null: "Variable must not be null";
    assert pPds != null : "Post dominators must not be null";

  }


  @Override
  public InformationFlowState copyWith(AbstractState newChild) {
    return new DataControlDependencyState(newChild,variable,pds);
  }

  @Override
  public InformationFlowStateType getType() {
    return InformationFlowStateType.DATA_CONTROL_DEP;
  }

  public Variable getVariable () {
    return variable;
  }

  public Set<CFANode> getPD () {
    return pds;
  }

  @Override
  boolean equalsExceptChild(InformationFlowState other) {
    return other instanceof DataControlDependencyState &&
        variable.equals(((DataControlDependencyState)other).variable) &&
        pds.equals(((DataControlDependencyState)other).pds);
  }
}