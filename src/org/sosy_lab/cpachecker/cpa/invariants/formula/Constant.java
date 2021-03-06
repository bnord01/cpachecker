/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2014  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.invariants.formula;

import java.util.Objects;

import org.sosy_lab.cpachecker.cpa.invariants.BitVectorInfo;
import org.sosy_lab.cpachecker.cpa.invariants.BitVectorType;

import com.google.common.base.Preconditions;

/**
 * Instances of this class represent constants within invariants formulae.
 *
 * @param <T> the type of the constant value.
 */
public class Constant<T> extends AbstractFormula<T> implements NumeralFormula<T> {

  /**
   * The value of the constant.
   */
  private final T value;

  /**
   * Creates a new constant with the given value.
   *
   * @param pValue the value of the constant.
   */
  private Constant(BitVectorInfo pInfo, T pValue) {
    super(pInfo);
    Preconditions.checkNotNull(pValue);
    if (pValue instanceof BitVectorType) {
      Preconditions.checkArgument(pInfo.equals(((BitVectorType) pValue).getBitVectorInfo()));
    }
    this.value = pValue;
  }

  /**
   * Gets the value of the constant.
   *
   * @return the value of the constant.
   */
  public T getValue() {
    return this.value;
  }

  @Override
  public String toString() {
    return getValue().toString();
  }

  @Override
  public boolean equals(Object pOther) {
    if (this == pOther) {
      return true;
    }
    if (pOther instanceof Constant) {
      Constant<?> other = (Constant<?>) pOther;
      return getBitVectorInfo().equals(other.getBitVectorInfo())
          && getValue().equals(other.getValue());
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(getBitVectorInfo(), getValue());
  }

  @Override
  public <ReturnType> ReturnType accept(NumeralFormulaVisitor<T, ReturnType> pVisitor) {
    return pVisitor.visit(this);
  }

  @Override
  public <ReturnType, ParamType> ReturnType accept(
      ParameterizedNumeralFormulaVisitor<T, ParamType, ReturnType> pVisitor, ParamType pParameter) {
    return pVisitor.visit(this, pParameter);
  }

  /**
   * Gets a invariants formula representing a constant with the given value.
   *
   * @param pValue the value of the constant.
   *
   * @return a invariants formula representing a constant with the given value.
   */
  static <T> Constant<T> of(BitVectorInfo pInfo, T pValue) {
    return new Constant<>(pInfo, pValue);
  }

  /**
   * Gets a invariants formula representing a constant with the given value.
   *
   * @param pValue the value of the constant.
   *
   * @return a invariants formula representing a constant with the given value.
   */
  static <T extends BitVectorType> Constant<T> of(T pValue) {
    return new Constant<>(pValue.getBitVectorInfo(), pValue);
  }

}
