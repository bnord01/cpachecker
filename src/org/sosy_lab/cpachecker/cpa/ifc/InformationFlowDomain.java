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

import org.sosy_lab.cpachecker.core.interfaces.AbstractDomain;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.exceptions.CPAException;

/**
 * Created by bnord on 15.06.15.
 */
public class InformationFlowDomain implements AbstractDomain {
  private AbstractDomain childDomain;
  public InformationFlowDomain(AbstractDomain child) {
    this.childDomain = child;
  }

  @Override
  public boolean isLessOrEqual(AbstractState pElement1, AbstractState pElement2)
      throws CPAException, InterruptedException {
    assert pElement1 instanceof InformationFlowState : "InformationFlowDomain.isLessOrEqual called without InformationFlowState as first parameter";
    assert pElement2 instanceof InformationFlowState : "InformationFlowDomain.isLessOrEqual called without InformationFlowState as second parameter";
    return isLessOrEqual((InformationFlowState)pElement1, (InformationFlowState) pElement2);
  }

  public boolean isLessOrEqual(InformationFlowState e1, InformationFlowState e2)
      throws CPAException, InterruptedException {
    if(e1 instanceof InitialFlowState && e2 instanceof InitialFlowState)
      return e1.isTarget() == e2.isTarget() && childDomain.isLessOrEqual(e1.getWrappedState(), e2.getWrappedState());
    else if(e1 instanceof SimpleDataDependencyState && e2 instanceof SimpleDataDependencyState) {
      return ((SimpleDataDependencyState)e1).getVariable().equals(((SimpleDataDependencyState)e2).getVariable()) &&
          e1.isTarget() == e2.isTarget() &&
          childDomain.isLessOrEqual(e1.getWrappedState(), e2.getWrappedState());
    } else if(e1 instanceof ControlDependencyState && e2 instanceof ControlDependencyState) {
      return e1.isTarget() == e2.isTarget() &&
          ((ControlDependencyState)e1).getControllingNode() == ((ControlDependencyState)e2).getControllingNode() &&
          childDomain.isLessOrEqual(e1.getWrappedState(), e2.getWrappedState());
    } else
      return false;
  }

  @Override
  public AbstractState join(AbstractState pElement1, AbstractState pElement2)
      throws CPAException, InterruptedException {
    assert pElement1 instanceof InformationFlowState : "InformationFlowDomain.join called without InformationFlowState as first parameter";
    assert pElement2 instanceof InformationFlowState : "InformationFlowDomain.join called without InformationFlowState as second parameter";
    return join((InformationFlowState)pElement1,
        (InformationFlowState)pElement2);
  }

  public InformationFlowState join(InformationFlowState e1,
      InformationFlowState e2)
      throws CPAException, InterruptedException {
    //TODO will have to check this.
    throw new UnsupportedOperationException("Join not implemented for Information Flow States!");
  }
}
