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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Set;
import java.util.logging.Level;

import org.sosy_lab.common.Pair;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.MergeOperator;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.cpa.ifc.ReturnVariable;
import org.sosy_lab.cpachecker.cpa.ifc.Variable;
import org.sosy_lab.cpachecker.exceptions.CPAException;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.Sets;

/**
 * Created by bnord on 02.09.15.
 */


public class QueryGenMergeOperator implements MergeOperator {
  MergeOperator mergeOperator;
  LogManager logManager;
  String programNames;
  String simpleName;
  String output;
  public QueryGenMergeOperator(MergeOperator pMergeOperator,LogManager pLogManager,Configuration config)
      throws InvalidConfigurationException {
    programNames = config.getProperty("analysis.programNames");
    String [] p = programNames.split("/");
    simpleName =  p[p.length - 1];
    output = config.getProperty("output.path");
    this.mergeOperator = pMergeOperator;
    this.logManager = pLogManager;
  }

  @Override
  public AbstractState merge(AbstractState state1, AbstractState state2, Precision precision)
      throws CPAException, InterruptedException {
    AbstractState res = mergeOperator.merge(state1,state2,precision);
    if(state2.equals(res)) {
      QueryGenState s = (QueryGenState)res;
      Set<Variable> srcs = FluentIterable.from(s.getReads()).transform(Pair.<Variable>getProjectionToSecond()).toSet();
      Set<Variable> snks = Sets.union(srcs,Sets.newHashSet(ReturnVariable.instance()));
      try {
        PrintStream out = new PrintStream(new BufferedOutputStream(new FileOutputStream(output + File.separator + simpleName + ".ifc.sh")));
        try {
          out.println("# Variables in cosideration: ");
          for(Variable v : snks) {
            out.println("# " + v.getQualifiedName());
          }
          out.println("#########################");
          out.println("secure=0");
          out.println("insecure=0");
          out.println("secure0=0");
          out.println("insecure0=0");

          for (Variable src : srcs) {
            for (Variable snk : snks) {
              String fnc = src.getQualifiedName().split(":")[0];
              if((snk.getQualifiedName().startsWith(fnc) && !src.equals(snk)) ||
                  snk.equals(ReturnVariable.instance()))
              {
                { // With predicate analysis
                  String outdir = (output + "/ifc_" +
                      src.getQualifiedName() + "_" +
                      snk.getQualifiedName()).replace("::", ".").replace(
                      "<", "").replace(">", "");
                  out.println("mkdir -p \"" + outdir + "\"");
                  out.print("./scripts/cpa.sh -ifc ");
                  out.print("\\\n\t");
                  out.print("-setprop cpa.ifc.highVariable=");
                  out.print("\"" + src.getQualifiedName() + "\" ");
                  out.print("\\\n\t");
                  out.print("-setprop cpa.ifc.lowVariable=");
                  out.print("\"" + snk.getQualifiedName() + "\" ");
                  out.print("\\\n\t");
                  out.print("-outputpath \"");
                  out.print(outdir);
                  out.print("\" ");
                  out.print("\\\n\t");
                  out.print(programNames);
                  out.print(" \\\n\t");
                  out.println(" > " + outdir + "/result.txt");
                  out.println("if grep --quiet TRUE " + outdir + "/result.txt; then");
                  out.println("\t((secure++))");
                  out.println("else");
                  out.println("\t((insecure++))");
                  out.println("fi");
                  out.println("echo Secure queries:   $secure");
                  out.println("echo Insecure queries: $insecure");
                  out.println("#");
                }

                {  // Without predicate analysis

                  String outdir0 = (output + "/ifc0_" +
                      src.getQualifiedName() + "_" +
                      snk.getQualifiedName()).replace("::", ".").replace(
                      "<", "").replace(">", "");
                  out.println("mkdir -p \"" + outdir0 + "\"");
                  out.print("./scripts/cpa.sh -ifc0 ");
                  out.print("\\\n\t");
                  out.print("-setprop cpa.ifc.highVariable=");
                  out.print("\"" + src.getQualifiedName() + "\" ");
                  out.print("\\\n\t");
                  out.print("-setprop cpa.ifc.lowVariable=");
                  out.print("\"" + snk.getQualifiedName() + "\" ");
                  out.print("\\\n\t");
                  out.print("-outputpath \"");
                  out.print(outdir0);
                  out.print("\" ");
                  out.print("\\\n\t");
                  out.print(programNames);
                  out.print(" \\\n\t");
                  out.println(" > " + outdir0 + "/result.txt");
                  out.println("if grep --quiet TRUE " + outdir0 + "/result.txt; then");
                  out.println("\t((secure0++))");
                  out.println("else");
                  out.println("\t((insecure0++))");
                  out.println("fi");
                  out.println("echo Secure0 queries:   $secure0");
                  out.println("echo Insecure0 queries: $insecure0");
                  out.println("#");
                }
              } else {
                logManager.log(Level.WARNING,"Procedural program found ", src, snk);
              }
            }
          }
          out.println("echo Analysis done!");
          out.println("echo Secure queries:   $secure / $secure0" );
          out.println("echo Insecure queries: $insecure / $insecure0" );
          logManager.log(Level.WARNING, "Program name: ", programNames);
          logManager.log(Level.WARNING, "Simple name: ", simpleName);
          logManager.log(Level.WARNING, "output: ", output);
          logManager.log(Level.WARNING, "Result: ", srcs);
        } finally {
          out.close();
        }
      } catch (FileNotFoundException e) {
        logManager.log(Level.SEVERE,e,e.fillInStackTrace());
      }
    }
    return res;
  }
}
