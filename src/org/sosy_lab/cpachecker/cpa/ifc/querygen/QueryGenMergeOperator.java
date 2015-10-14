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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
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
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

/**
 * Created by bnord on 02.09.15.
 */

@Options(prefix = "ifc.querygen")
public class QueryGenMergeOperator implements MergeOperator {
  MergeOperator mergeOperator;
  LogManager logManager;
  String programNames;
  String simpleName;
  String output;
  @Option(description = "Maximum number of queries to run.")
  int maxQueries = 1000;
  @Option(description = "Seed for random selection if more than maxQueries of variable pairs found.")
  int seed = 42;
  public QueryGenMergeOperator(MergeOperator pMergeOperator,LogManager pLogManager,Configuration config)
      throws InvalidConfigurationException {
    programNames = config.getProperty("analysis.programNames");
    String [] p = programNames.split("/");
    simpleName =  p[p.length - 1];
    output = config.getProperty("output.path");
    this.mergeOperator = pMergeOperator;
    this.logManager = pLogManager;
    config.inject(this);
  }

  @Override
  public AbstractState merge(AbstractState state1, AbstractState state2, Precision precision)
      throws CPAException, InterruptedException {
    AbstractState res = mergeOperator.merge(state1,state2,precision);
    if(state2.equals(res)) {
      QueryGenState s = (QueryGenState)res;
      Set<Variable> srcs = FluentIterable.from(s.getReads()).transform(Pair.<Variable>getProjectionToSecond()).toSet();
      Set<Variable> snks = Sets.union(srcs, Sets.newHashSet(ReturnVariable.instance()));
      List<Pair<Variable,Variable>> queries = Lists.newArrayList();
      for (Variable src : srcs) {
        for (Variable snk : snks) {
          String fnc = src.getQualifiedName().split(":")[0];
          if ((snk.getQualifiedName().startsWith(fnc) && !src.equals(snk)) ||
              snk.equals(ReturnVariable.instance())) {
            queries.add(Pair.of(src,snk));
          }
        }
      }

      // Pick random sample if more than maxQueries variable pairs.
      if(queries.size()>maxQueries) {
        Collections.shuffle(queries,new java.util.Random(seed));
        queries = queries.subList(0,maxQueries);
      }

      try {
        PrintStream out = new PrintStream(new BufferedOutputStream(new FileOutputStream(output + File.separator + simpleName + ".ifc.sh")));
        try {
          out.println("#!/bin/bash");
          out.println("# Variables in cosideration: ");
          for(Variable v : snks) {
            out.println("# " + v.getQualifiedName());
          }
          out.println("#########################");
          out.println("if [ -z \"$1\" ]; then resfile='result.txt'; else resfile=\"$1\"; fi");
          out.println("csvfile=\"${resfile}.csv\"");
          out.println("secure=0");
          out.println("insecure=0");
          out.println("error=0");
          out.println("secure0=0");
          out.println("insecure0=0");
          out.println("error0=0");
          int num = queries.size() * 2;
          int count = 0;
          for(Pair<Variable,Variable> pair: queries) {
            count ++;
            Variable src = pair.getFirst();
            Variable snk = pair.getSecond();
            String qry = sanitize(simpleName + " from " + src.getQualifiedName() + " to " + snk.getQualifiedName());
            { // With predicate analysis
              String proc = "["+count + "/" + num + "]" ;
              String outdir = sanitize(output + "/ifc_" +
                  simpleName + "_" +
                  src.getQualifiedName() + "_" +
                  snk.getQualifiedName());
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
              out.print("\techo " + proc + " No flow in " + qry);
              out.println(" found using predicate analysis \\( $secure / $insecure / $error \\).");
              out.println("\techo \""+simpleName+","+src.getQualifiedName()+","+snk.getQualifiedName()+",PREDICATE,SECURE\" >> $csvfile");
              out.println("else");
              out.println("\tif grep --quiet FALSE " + outdir + "/result.txt; then");
              out.println("\t\t((insecure++))");
              out.print("\t\techo " + proc + " Flow in " + qry);
              out.println(" found using predicate analysis \\( $secure / $insecure / $error \\).");
              out.println("\t\techo \""+simpleName+","+src.getQualifiedName()+","+snk.getQualifiedName()+",PREDICATE,FLOW\" >> $csvfile");
              out.println("\telse");
              out.println("\t\t((error++))");
              out.print("\t\techo " + proc + " Error checking for flow in " + qry);
              out.println(" using predicate analysis \\( $secure / $insecure / $error \\).");
              out.println("\t\techo \""+simpleName+","+src.getQualifiedName()+","+snk.getQualifiedName()+",PREDICATE,ERROR\" >> $csvfile");
              out.println("\tfi");
              out.println("fi");
              out.println("#");
            }
            count++;
            {  // Without predicate analysis
              String proc = "["+count + "/" + num + "]" ;
              String outdir0 = sanitize(output + "/ifc0_" +
                  simpleName + "_" +
                  src.getQualifiedName() + "_" +
                  snk.getQualifiedName());
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
              out.print("\techo " + proc + " No flow in " + qry);
              out.println(" found using location analysis \\( $secure0 / $insecure0 / $error0 \\).");
              out.println("\techo \""+simpleName+","+src.getQualifiedName()+","+snk.getQualifiedName()+",LOCATION,SECURE\" >> $csvfile");
              out.println("else");
              out.println("\tif grep --quiet FALSE " + outdir0 + "/result.txt; then");
              out.println("\t\t((insecure0++))");
              out.print("\t\techo " + proc + " Flow in " + qry);
              out.println(" found using location analysis \\( $secure0 / $insecure0 / $error0 \\).");
              out.println("\t\techo \""+simpleName+","+src.getQualifiedName()+","+snk.getQualifiedName()+",LOCATION,FLOW\" >> $csvfile");
              out.println("\telse");
              out.println("\t\t((error0++))");
              out.print("\t\techo "+ proc + " Error checking for flow in "+qry);
              out.println(" using location analysis \\( $secure0 / $insecure0 / $error0 \\).");
              out.println("\t\techo \""+simpleName+","+src.getQualifiedName()+","+snk.getQualifiedName()+",LOCATION,ERROR\" >> $csvfile");
              out.println("\tfi");
              out.println("fi");
              out.println("#");
            }
          }
          out.println("echo Analysis done!");
          out.println("echo Predicate analysis:  $secure secure / $insecure insecure / $error error / " + num + " total");
          out.println("echo Location analysis:   $secure0 secure / $insecure0 insecure / $error0 error / " + num + " total");
          out.println("echo Writing results to file: $resfile");
          out.println("echo " + num + "\" $secure $insecure $error $secure0 $insecure0 $error0\" >> \"$resfile\"");

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
  private String sanitize(String str) {
    return str.replace("::", ".").replace(
        "<", "").replace(">", "");
  }
}
