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

import javax.annotation.Nonnull;

import org.sosy_lab.cpachecker.cfa.ast.ASimpleDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CVariableDeclaration;

/**
 * Created by bnord on 24.08.15.
 */
public class SimpleVariable implements Variable {
  private ASimpleDeclaration declaration;
  public SimpleVariable(@Nonnull ASimpleDeclaration pASimpleDeclaration) {
    assert pASimpleDeclaration != null : "Declaration must not be null.";
    this.declaration = pASimpleDeclaration;
  }

  @Override
  public boolean equals(Object other) {
    if(other instanceof SimpleVariable) {
      ASimpleDeclaration otherDeclaration = ((SimpleVariable) other).declaration;
      if (declaration instanceof CVariableDeclaration && otherDeclaration instanceof CVariableDeclaration) {
        return ((CVariableDeclaration)declaration).equalsWithoutStorageClass(otherDeclaration);
      } else {
        return declaration.equals(otherDeclaration);
      }
    } else
      return false;
  }
  @Override
  public int hashCode() {
    if (declaration instanceof CVariableDeclaration) {
      return ((CVariableDeclaration)declaration).hashCodeWithOutStorageClass();
    } else {
      return declaration.hashCode();
    }
  }

  @Override
  public String getName(){
    return declaration.getName();
  }
  @Override
  public String getQualifiedName() {
    return declaration.getQualifiedName();
  }
  @Override
  public String toString() {
    return "SimpleVariable(" + getQualifiedName() + ")";
  }
}
