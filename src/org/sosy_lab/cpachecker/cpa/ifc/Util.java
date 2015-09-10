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

import static com.google.common.collect.FluentIterable.from;

import java.util.Collection;
import java.util.Set;

import javax.annotation.Nullable;

import org.sosy_lab.cpachecker.cfa.ast.AArraySubscriptExpression;
import org.sosy_lab.cpachecker.cfa.ast.AExpression;
import org.sosy_lab.cpachecker.cfa.ast.ASimpleDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CAssignment;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cpa.livevar.DeclarationCollectingVisitor;

import com.google.common.base.Function;
import com.google.common.collect.Sets;

/**
 * Created by bnord on 28.07.15.
 */
public class Util {

  public static <E> Set<E> insert(Set<E> set, E elem) {
    return set.contains(elem)?set:Sets.union(set, Sets.newHashSet(elem));
  }

  public static boolean isAssignment(CExpression pCExpression) {
    return pCExpression instanceof CAssignment;
  }

  /**
   * Returns a collection of all variable names which occur in expression
   */
  public static Set<Variable> collectVariablesFromExpression(AExpression expression) {
    return from(expression.<Set<ASimpleDeclaration>,
        Set<ASimpleDeclaration>,
        Set<ASimpleDeclaration>,
        RuntimeException,
        RuntimeException,
        DeclarationCollectingVisitor>accept_(new DeclarationCollectingVisitor())).transform(
        new Function<ASimpleDeclaration, Variable>() {
          @Nullable
          @Override
          public Variable apply(@Nullable ASimpleDeclaration pASimpleDeclaration) {
            return new SimpleVariable(pASimpleDeclaration);
          }
        }).toSet();
  }

  /**
   * Returns a collection of the variable names in the leftHandSide
   */
  public static Set<Variable> collectVariablesFromLeftHandSide(AExpression pLeftHandSide) {
    return from(pLeftHandSide.<Set<ASimpleDeclaration>,
        Set<ASimpleDeclaration>,
        Set<ASimpleDeclaration>,
        RuntimeException,
        RuntimeException,
        LeftHandSideIdExpressionVisitor>accept_(new LeftHandSideIdExpressionVisitor())).transform(
        new Function<ASimpleDeclaration, Variable>() {
          @Nullable
          @Override
          public Variable apply(@Nullable ASimpleDeclaration pASimpleDeclaration) {
            return new SimpleVariable(pASimpleDeclaration);
          }
        }).toSet();
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
}
