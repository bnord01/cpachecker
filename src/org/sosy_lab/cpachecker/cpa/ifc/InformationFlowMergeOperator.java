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

import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.MergeOperator;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.exceptions.CPAException;

/**
 * Created by bnord on 18.06.15.
 */
public class InformationFlowMergeOperator implements MergeOperator {
  private MergeOperator childMergeOperator;

  public InformationFlowMergeOperator(MergeOperator pChildMergeOperator) {
    this.childMergeOperator=pChildMergeOperator;
  }
  @Override
  public AbstractState merge(AbstractState state1, AbstractState state2,
      Precision precision) throws CPAException, InterruptedException {
    AbstractState childState1 = ((InformationFlowState)state1).getWrappedState();
    AbstractState childState2 = ((InformationFlowState)state2).getWrappedState();
    if(((InformationFlowState)state1).getType() != ((InformationFlowState)state2).getType())
      return state2;
    if(((InformationFlowState)state1).isTarget() != ((InformationFlowState)state2).isTarget())
      return state2;
    if(state1 instanceof SimpleDataDependencyState && !((SimpleDataDependencyState)state1).getVariable().equals(((SimpleDataDependencyState)state2).getVariable()))
      return state2;
    if(state1 instanceof ControlDependencyState && !((ControlDependencyState)state1).getControllingNode().equals(((ControlDependencyState)state2).getControllingNode()))
      return state2;

    //Information flow states are equal, just do child merge
    Precision childPrecision = ((InformationFlowPrecision)precision).getWrappedPrecision();
    AbstractState mergedState = childMergeOperator.merge(childState1,childState2,childPrecision);
    if(mergedState == childState1) {
      return state1;
    } else if (mergedState == childState2) {
      return state2;
    } else {
      return ((InformationFlowState)state2).copyWith(mergedState);
    }

  }
}
