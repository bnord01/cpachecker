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
package org.sosy_lab.cpachecker.cpa.ifc.querygen;

import java.util.Set;

import org.sosy_lab.common.Pair;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.defaults.LatticeAbstractState;
import org.sosy_lab.cpachecker.cpa.ifc.Util;
import org.sosy_lab.cpachecker.cpa.ifc.Variable;

import com.google.common.collect.Sets;

/**
 * Created by bnord on 21.07.15.
 */
public class QueryGenState implements LatticeAbstractState {
  private Set<Pair<CFANode,Variable>> reads;

  public Set<CFANode> getVisited() {
    return visited;
  }

  private Set<CFANode> visited;
  public QueryGenState(Set<CFANode> pVisited, Set<Pair<CFANode, Variable>> pReads) {
    this.visited = pVisited;
    this.reads = pReads;
  }

  public QueryGenState successor(CFANode src, Set<Variable> readVars, CFANode snk) {
    Set<CFANode> newVisited = Util.insert(visited, snk);
    Set<Pair<CFANode,Variable>> newReads = reads;
    for(Variable v:readVars) {
      Pair<CFANode,Variable> read = Pair.of(src,v);
      newReads = Util.insert(newReads,read);
    }
    return new QueryGenState(newVisited,newReads);
  }

  @Override
  public QueryGenState join(LatticeAbstractState pOther) {
    QueryGenState other = (QueryGenState) pOther;
    if(other.isLessOrEqual(this)) return this;
    else if(isLessOrEqual(other)) return other;
    else return new QueryGenState(Sets.union(visited,other.visited),Sets.union(reads,other.reads));
  }

  @Override
  public boolean isLessOrEqual(LatticeAbstractState other) {
    if(!(other instanceof QueryGenState))
      return false;
    return ((QueryGenState)other).visited.containsAll(visited) && ((QueryGenState)other).reads.containsAll(reads);
  }

  @Override
  public boolean equals(Object other) {
    if(!(other instanceof QueryGenState)) return false;
    return visited.equals(((QueryGenState)other).visited) && reads.equals(((QueryGenState)other).reads);
  }

  @Override
  public int hashCode() {
    return reads.hashCode();
  }

  @Override
  public String toString() {
    return "QueryGenState(" + visited + ", " + reads + ")";
  }

  public Set<Pair<CFANode,Variable>> getReads(){
    return Sets.newHashSet(reads);
  }
}
