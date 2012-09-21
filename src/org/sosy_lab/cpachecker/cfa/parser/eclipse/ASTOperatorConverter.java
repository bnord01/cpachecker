/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2012  Dirk Beyer
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
package org.sosy_lab.cpachecker.cfa.parser.eclipse;

import org.eclipse.cdt.core.dom.ast.IASTBinaryExpression;
import org.eclipse.cdt.core.dom.ast.IASTUnaryExpression;
import org.sosy_lab.common.Pair;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression.BinaryOperator;
import org.sosy_lab.cpachecker.cfa.ast.c.CUnaryExpression.UnaryOperator;

/** This Class contains functions,
 * that convert operators from C-source into CPAchecker-format. */
public class ASTOperatorConverter {

  static UnaryOperator convUnaryOperator(final IASTUnaryExpression e) {
    switch (e.getOperator()) {
    case IASTUnaryExpression.op_amper:
      return UnaryOperator.AMPER;
    case IASTUnaryExpression.op_minus:
      return UnaryOperator.MINUS;
    case IASTUnaryExpression.op_not:
      return UnaryOperator.NOT;
    case IASTUnaryExpression.op_plus:
      return UnaryOperator.PLUS;
    case IASTUnaryExpression.op_sizeof:
      return UnaryOperator.SIZEOF;
    case IASTUnaryExpression.op_star:
      return UnaryOperator.STAR;
    case IASTUnaryExpression.op_tilde:
      return UnaryOperator.TILDE;
    default:
      throw new CFAGenerationRuntimeException("Unknown unary operator", e);
    }
  }

  static Pair<BinaryOperator, Boolean> convBinaryOperator(final IASTBinaryExpression e) {
    boolean isAssign = false;
    final BinaryOperator operator;

    switch (e.getOperator()) {
    case IASTBinaryExpression.op_multiply:
      operator = BinaryOperator.MULTIPLY;
      break;
    case IASTBinaryExpression.op_divide:
      operator = BinaryOperator.DIVIDE;
      break;
    case IASTBinaryExpression.op_modulo:
      operator = BinaryOperator.MODULO;
      break;
    case IASTBinaryExpression.op_plus:
      operator = BinaryOperator.PLUS;
      break;
    case IASTBinaryExpression.op_minus:
      operator = BinaryOperator.MINUS;
      break;
    case IASTBinaryExpression.op_shiftLeft:
      operator = BinaryOperator.SHIFT_LEFT;
      break;
    case IASTBinaryExpression.op_shiftRight:
      operator = BinaryOperator.SHIFT_RIGHT;
      break;
    case IASTBinaryExpression.op_lessThan:
      operator = BinaryOperator.LESS_THAN;
      break;
    case IASTBinaryExpression.op_greaterThan:
      operator = BinaryOperator.GREATER_THAN;
      break;
    case IASTBinaryExpression.op_lessEqual:
      operator = BinaryOperator.LESS_EQUAL;
      break;
    case IASTBinaryExpression.op_greaterEqual:
      operator = BinaryOperator.GREATER_EQUAL;
      break;
    case IASTBinaryExpression.op_binaryAnd:
      operator = BinaryOperator.BINARY_AND;
      break;
    case IASTBinaryExpression.op_binaryXor:
      operator = BinaryOperator.BINARY_XOR;
      break;
    case IASTBinaryExpression.op_binaryOr:
      operator = BinaryOperator.BINARY_OR;
      break;
    case IASTBinaryExpression.op_logicalAnd:
      operator = BinaryOperator.LOGICAL_AND;
      break;
    case IASTBinaryExpression.op_logicalOr:
      operator = BinaryOperator.LOGICAL_OR;
      break;
    case IASTBinaryExpression.op_assign:
      operator = null;
      isAssign = true;
      break;
    case IASTBinaryExpression.op_multiplyAssign:
      operator = BinaryOperator.MULTIPLY;
      isAssign = true;
      break;
    case IASTBinaryExpression.op_divideAssign:
      operator = BinaryOperator.DIVIDE;
      isAssign = true;
      break;
    case IASTBinaryExpression.op_moduloAssign:
      operator = BinaryOperator.MODULO;
      isAssign = true;
      break;
    case IASTBinaryExpression.op_plusAssign:
      operator = BinaryOperator.PLUS;
      isAssign = true;
      break;
    case IASTBinaryExpression.op_minusAssign:
      operator = BinaryOperator.MINUS;
      isAssign = true;
      break;
    case IASTBinaryExpression.op_shiftLeftAssign:
      operator = BinaryOperator.SHIFT_LEFT;
      isAssign = true;
      break;
    case IASTBinaryExpression.op_shiftRightAssign:
      operator = BinaryOperator.SHIFT_RIGHT;
      isAssign = true;
      break;
    case IASTBinaryExpression.op_binaryAndAssign:
      operator = BinaryOperator.BINARY_AND;
      isAssign = true;
      break;
    case IASTBinaryExpression.op_binaryXorAssign:
      operator = BinaryOperator.BINARY_XOR;
      isAssign = true;
      break;
    case IASTBinaryExpression.op_binaryOrAssign:
      operator = BinaryOperator.BINARY_OR;
      isAssign = true;
      break;
    case IASTBinaryExpression.op_equals:
      operator = BinaryOperator.EQUALS;
      break;
    case IASTBinaryExpression.op_notequals:
      operator = BinaryOperator.NOT_EQUALS;
      break;
    default:
      throw new CFAGenerationRuntimeException("Unknown binary operator", e);
    }

    return Pair.of(operator, isAssign);
  }

}
