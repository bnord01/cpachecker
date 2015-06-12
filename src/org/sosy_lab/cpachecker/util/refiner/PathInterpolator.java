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
package org.sosy_lab.cpachecker.util.refiner;

import java.util.Map;

import org.sosy_lab.common.Pair;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.cpa.arg.ARGPath;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.arg.MutableARGPath;
import org.sosy_lab.cpachecker.exceptions.CPAException;

import com.google.common.collect.Multimap;

/**
 * Classes implementing this interface derive interpolants of a whole path.
 *
 * @param <I> the type of interpolant created by the implementation
 * @param <P> the type of the computed precisions' elements
 */
public interface PathInterpolator<I extends Interpolant<?>, P> extends Statistics {

   Map<ARGState, I> performInterpolation(
      ARGPath errorPath,
      I interpolant
  ) throws CPAException;

  Multimap<CFANode, P> determinePrecisionIncrement(MutableARGPath errorPath)
      throws CPAException;

  Pair<ARGState, CFAEdge> determineRefinementRoot(
      MutableARGPath errorPath,
      Multimap<CFANode, P> increment,
      boolean isRepeatedRefinement
  ) throws CPAException;
}