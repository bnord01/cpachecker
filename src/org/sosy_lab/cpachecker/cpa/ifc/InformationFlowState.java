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


import javax.annotation.Nullable;

import org.sosy_lab.cpachecker.cfa.ast.ASimpleDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.defaults.AbstractSingleWrapperState;

import org.sosy_lab.cpachecker.core.interfaces.AbstractState;

import com.google.common.base.Equivalence;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.base.Function;

/**
 * Created by bnord on 15.06.15.
 */

enum InformationFlowStateType {
  INITIAL_STATE,SIMPLE_DATA_DEP_STATE,CONTROL_DEP_TYPE
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

  public InformationFlowState(AbstractState child) {
    super(child);
  }

  public InformationFlowState(AbstractState child,boolean pTarget) {
    super(child);
    this.target = pTarget;
  }

  private boolean target = false;

  @Override
  public boolean isTarget() {
    return target;
  }

  abstract public InformationFlowState copyWith(AbstractState newChild);

  abstract public InformationFlowStateType getType();
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
  public String toString(){
    return (isTarget()?"Targeted":"") + "ControlDependencyState(controllingNode = " + controllingNode + " , child = " + super.toString() + ")";
  }
}


/**
 * State that tracks a data dependency on a simple variable.
 */
class SimpleDataDependencyState extends InformationFlowState {
  public static final Equivalence<ASimpleDeclaration> DECL_EQUIVALENCE = new Equivalence<ASimpleDeclaration>() {

      @Override
      protected boolean doEquivalent(ASimpleDeclaration pA, ASimpleDeclaration pB) {
        if (pA instanceof CVariableDeclaration && pB instanceof CVariableDeclaration) {
          return ((CVariableDeclaration)pA).equalsWithoutStorageClass(pB);
        } else {
          return pA.equals(pB);
        }
      }

      @Override
      protected int doHash(ASimpleDeclaration pT) {
        if (pT instanceof CVariableDeclaration) {
          return ((CVariableDeclaration)pT).hashCodeWithOutStorageClass();
        } else {
          return pT.hashCode();
        }
      }
    };
  public final static Function<ASimpleDeclaration, Equivalence.Wrapper<ASimpleDeclaration>> TO_EQUIV_WRAPPER =
      new Function<ASimpleDeclaration, Equivalence.Wrapper<ASimpleDeclaration>>() {
        @Override
        public Equivalence.Wrapper<ASimpleDeclaration> apply(ASimpleDeclaration pInput) {
          return DECL_EQUIVALENCE.wrap(pInput);
        }};


  Wrapper<ASimpleDeclaration> variable;

  public SimpleDataDependencyState(AbstractState child,Wrapper<ASimpleDeclaration> pVariable) {
    super(child);
    this.variable = pVariable;
  }
  public SimpleDataDependencyState(AbstractState child,Wrapper<ASimpleDeclaration> pVariable,boolean pTarget) {
    super(child,pTarget);
    this.variable = pVariable;
  }
  public SimpleDataDependencyState(AbstractState child,ASimpleDeclaration pVariable) {
    this(child,DECL_EQUIVALENCE.wrap(pVariable));
  }
  public SimpleDataDependencyState copyWith(AbstractState newChild) {
    return new SimpleDataDependencyState(newChild, variable,isTarget());
  }
  public InformationFlowStateType getType() {return InformationFlowStateType.SIMPLE_DATA_DEP_STATE;}
  public Wrapper<ASimpleDeclaration> getVariable() {
    return variable;
  }

  @Override
  public String toString(){
    return (isTarget()?"Targeted":"") + "SimpleDataDependencyState(variable = " + variable + " , child = " + super.toString() + ")";
  }
}