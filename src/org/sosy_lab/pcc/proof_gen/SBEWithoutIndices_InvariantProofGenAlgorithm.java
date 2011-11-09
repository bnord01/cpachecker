/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2011  Dirk Beyer
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
package org.sosy_lab.pcc.proof_gen;

import java.util.HashSet;

import org.sosy_lab.common.LogManager;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFAEdge;
import org.sosy_lab.cpachecker.cpa.art.ARTElement;
import org.sosy_lab.cpachecker.cpa.predicate.PredicateAbstractElement;
import org.sosy_lab.cpachecker.util.predicates.interfaces.Formula;
import org.sosy_lab.pcc.common.Separators;

public class SBEWithoutIndices_InvariantProofGenAlgorithm extends
    SBE_InvariantProofGenAlgorithm {

  private HashSet<String> edges = new HashSet<String>();

  public SBEWithoutIndices_InvariantProofGenAlgorithm(Configuration pConfig,
      LogManager pLogger) throws InvalidConfigurationException {
    super(pConfig, pLogger);
  }

  @Override
  protected boolean addSingleOperation(ARTElement pNode, CFAEdge pEdge) {
    // build identification of edge
    String id =
        buildEdgeId(pNode.retrieveLocationElement().getLocationNode(), pEdge);
    // add operation
    if (!edges.contains(id)) {
      edges.add(id);
    }
    return true;
  }

  @Override
  protected String getAbstraction(PredicateAbstractElement pPredicate) {
    Formula f =
        fh.removeIndices(pPredicate.getAbstractionFormula().asFormula());
    if (f == null) { return null; }
    return f.toString();
  }

  @Override
  protected StringBuilder writeOperations() {
    StringBuilder output = new StringBuilder();
    for(String edge: edges){
      output.append(edge+Separators.commonSeparator);
    }
    return output;
  }

}
