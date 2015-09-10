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
package org.sosy_lab.cpachecker.cpa.ifc.dcd;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sosy_lab.cpachecker.cfa.ast.c.CAssignment;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.c.CDeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.core.defaults.SingleEdgeTransferRelation;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.cpa.ifc.SimpleVariable;
import org.sosy_lab.cpachecker.cpa.ifc.Variable;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.cpa.ifc.Util;


import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;

/**
 * Created by bnord on 21.07.15.
 */
public class DCDCollectorTransferRelation extends SingleEdgeTransferRelation {

  private SetMultimap<CFANode,CFANode> pdOfMap= null;

  public DCDCollectorTransferRelation(SetMultimap<CFANode,CFANode> pdMap) {
    pdOfMap = HashMultimap.create();
    for(CFANode n : pdMap.keySet()) {
      for(CFANode p: pdMap.get(n))
        if(!p.equals(n))
          pdOfMap.put(p, n);
    }
  }

  @Override
  public Collection<? extends AbstractState> getAbstractSuccessorsForEdge(
      AbstractState element, Precision prec, CFAEdge cfaEdge)
      throws CPATransferException {
    DCDCollectorState state = (DCDCollectorState) element;
    Set<Variable> vars = Sets.newHashSet();
    switch (cfaEdge.getEdgeType()) {
      case StatementEdge: {
        CStatementEdge statementEdge = (CStatementEdge) cfaEdge;
        CStatement expression = statementEdge.getStatement();
        if(expression instanceof CAssignment) {
          vars.addAll(Util.collectVariablesFromLeftHandSide(((CAssignment)expression).getLeftHandSide()));
        }
        if(expression instanceof CExpressionAssignmentStatement)
          vars.addAll(Util.collectVariablesFromLeftHandSide(((CExpressionAssignmentStatement)expression).getLeftHandSide()));

        break;
      }
      case DeclarationEdge: {
        CDeclarationEdge declarationEdge = (CDeclarationEdge) cfaEdge;
        if(declarationEdge.getDeclaration() instanceof CVariableDeclaration)
          vars.add(new SimpleVariable(declarationEdge.getDeclaration()));
        break;

      }

    }
    return Collections.singleton(
        state.successor(cfaEdge.getPredecessor(), vars, pdOfMap.get(cfaEdge.getSuccessor())));
  }

  @Override
  public Collection<? extends AbstractState> strengthen(AbstractState element,
      List<AbstractState> otherElements, CFAEdge cfaEdge,
      Precision precision) {
    return null;
  }
}