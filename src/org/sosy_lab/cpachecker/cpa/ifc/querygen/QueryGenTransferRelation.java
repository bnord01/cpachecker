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

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.ast.c.CAssignment;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CParameterDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CRightHandSide;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.c.CAssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CDeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CReturnStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.core.defaults.SingleEdgeTransferRelation;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.cpa.ifc.SimpleVariable;
import org.sosy_lab.cpachecker.cpa.ifc.Util;
import org.sosy_lab.cpachecker.cpa.ifc.Variable;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;

/**
 * Created by bnord on 21.07.15.
 */
public class QueryGenTransferRelation implements TransferRelation {

  @Override
  public Collection<QueryGenState> getAbstractSuccessorsForEdge(
      AbstractState element, Precision prec, CFAEdge cfaEdge)
      throws CPATransferException {
    QueryGenState state = (QueryGenState) element;
    Set<Variable> vars = Sets.newHashSet();
    switch (cfaEdge.getEdgeType()) {
      case StatementEdge: {
        CStatementEdge statementEdge = (CStatementEdge) cfaEdge;
        CStatement statement = statementEdge.getStatement();
        if(statement instanceof CExpressionStatement)
          vars.addAll(Util.collectVariablesFromExpression(((CExpressionStatement)statement).getExpression()));
        if(statement instanceof CExpressionAssignmentStatement)
          vars.addAll(Util.collectVariablesFromExpression(((CExpressionAssignmentStatement)statement).getRightHandSide()));
        if(statement instanceof CAssignment) {
          CRightHandSide rhs = ((CAssignment)statement).getRightHandSide();
          if(rhs instanceof CExpression)
            vars.addAll(Util.collectVariablesFromExpression((CExpression)rhs));
          vars.addAll(Util.collectVariablesFromLeftHandSide(((CAssignment)statement).getLeftHandSide()));
        }


        break;
      }
      case DeclarationEdge: {
        CDeclarationEdge declarationEdge = (CDeclarationEdge) cfaEdge;
        if(declarationEdge.getDeclaration() instanceof CVariableDeclaration) {
          vars.add(new SimpleVariable(declarationEdge.getDeclaration()));
          if (((CVariableDeclaration)declarationEdge.getDeclaration())
              .getInitializer() instanceof CInitializerExpression)
            vars.addAll(Util.collectVariablesFromExpression(
                ((CInitializerExpression)((CVariableDeclaration)declarationEdge.getDeclaration()).getInitializer())
                    .getExpression()));
        }
        if(declarationEdge.getDeclaration() instanceof CFunctionDeclaration) {
          for( CParameterDeclaration p:  ((CFunctionDeclaration)declarationEdge.getDeclaration()).getParameters()) {
            vars.add(new SimpleVariable(p));
          }
        }
        break;

      }
      case ReturnStatementEdge: {
        CReturnStatementEdge returnStatementEdge = (CReturnStatementEdge) cfaEdge;
        if(returnStatementEdge.getExpression().isPresent())
          vars.addAll(Util.collectVariablesFromExpression(returnStatementEdge.getExpression().get()));
        break;
      }
      case AssumeEdge: {
        CAssumeEdge assumeEdge = (CAssumeEdge)cfaEdge;
        vars.addAll(Util.collectVariablesFromExpression(assumeEdge.getExpression()));
        break;
      }
    }
    QueryGenState successor = state.successor(cfaEdge.getPredecessor(),vars,cfaEdge.getSuccessor());
    return Collections.singleton(successor);
  }

  @Override
  public Collection<? extends AbstractState> strengthen(AbstractState element,
      List<AbstractState> otherElements, CFAEdge cfaEdge,
      Precision precision) {
    return null;
  }

  @Override
  public Collection<QueryGenState> getAbstractSuccessors(AbstractState pState, Precision pPrecision)
      throws CPATransferException, InterruptedException {

    QueryGenState state = (QueryGenState) pState;
    QueryGenState res = state;
    for(CFANode n:state.getVisited()) {
      int numsuc = n.getNumLeavingEdges();
      for(int i = 0; i< numsuc; i++) {
        for(QueryGenState q : getAbstractSuccessorsForEdge(state,pPrecision,n.getLeavingEdge(i)))
          res = res.join(q);
      }
    }
    return Sets.newHashSet(res);
  }
}