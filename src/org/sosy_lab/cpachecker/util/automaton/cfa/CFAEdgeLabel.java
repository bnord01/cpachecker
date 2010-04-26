/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker. 
 *
 *  Copyright (C) 2007-2010  Dirk Beyer
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
/**
 * 
 */
package org.sosy_lab.cpachecker.util.automaton.cfa;

import org.sosy_lab.cpachecker.cfa.objectmodel.CFAEdge;
import org.sosy_lab.cpachecker.util.automaton.Label;

/**
 * @author holzera
 *
 */
public class CFAEdgeLabel implements Label<CFAEdge> {

  CFAEdge mEdge;
  
  public CFAEdgeLabel(CFAEdge pEdge) {
    assert(pEdge != null);
    
    mEdge = pEdge;
  }
  
  @Override
  public boolean matches(CFAEdge pE) {
    return mEdge.equals(pE);
  }
  
  @Override
  public boolean equals(Object pOther) {
    if (pOther == null) {
      return false;
    }
    
    if (!(pOther instanceof CFAEdgeLabel)) {
      return false;
    }
    
    CFAEdgeLabel lLabel = (CFAEdgeLabel)pOther;
    
    return mEdge.equals(lLabel.mEdge);
  }
  
  @Override
  public int hashCode() {
    return mEdge.hashCode();
  }
  
  @Override
  public String toString() {
    String lResult = "(";
    
    lResult += mEdge.getPredecessor().toString();
    
    lResult += " --[" + mEdge.getRawStatement() + "]-> ";
    
    lResult += mEdge.getSuccessor().toString();
    
    lResult += ")";
    
    return lResult;
  }
}
