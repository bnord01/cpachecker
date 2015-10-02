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
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustment;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustmentResult;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustmentResult.Action;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.cpa.composite.CompositePrecision;
import org.sosy_lab.cpachecker.cpa.composite.CompositeState;
import org.sosy_lab.cpachecker.exceptions.CPAException;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Optional;

/**
 * Created by bnord on 18.06.15.
 */
public class InformationFlowPrecisionAdjustment implements PrecisionAdjustment {
  private PrecisionAdjustment childPA;

  public InformationFlowPrecisionAdjustment(PrecisionAdjustment pChildPA) {
    this.childPA = pChildPA;
  }
  @Override
  public Optional<PrecisionAdjustmentResult> prec(AbstractState state,
      Precision precision, UnmodifiableReachedSet states,
      Function<AbstractState, AbstractState> stateProjection,
      AbstractState fullState) throws CPAException, InterruptedException {
    InformationFlowState ifs = (InformationFlowState) state;
    AbstractState childState = ifs.getWrappedState();
    InformationFlowPrecision ifp = (InformationFlowPrecision)precision;
    Precision childPrecision = ifp.getWrappedPrecision();
    Function<AbstractState, AbstractState> childProj = Functions.compose(
        InformationFlowState.unWrapFunc, stateProjection);

    Optional<PrecisionAdjustmentResult>
        out = childPA.prec(childState, childPrecision, states, childProj,
        fullState);

    if(!out.isPresent())
      return Optional.absent();

    PrecisionAdjustmentResult inner = out.get();
    AbstractState newChildState = inner.abstractState();
    Precision newChildPrecision = inner.precision();

    Action action = inner.action();
    if(ifs.isTarget())
      action = Action.BREAK;
    if ((newChildState != childState) || (newChildPrecision != childPrecision)) {
      AbstractState newState = ifs.copyWith(newChildState);
      Precision newPrecision = new InformationFlowPrecision(newChildPrecision);
      return Optional.of(PrecisionAdjustmentResult.create(newState,newPrecision,action));
    } else {
      return Optional.of(PrecisionAdjustmentResult.create(state,precision,action));
    }
  }
}
