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
package org.sosy_lab.cpachecker.util.predicates.mathsat5;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sosy_lab.cpachecker.util.predicates.mathsat5.Mathsat5NativeApi.*;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import org.sosy_lab.common.Appender;
import org.sosy_lab.common.Appenders;
import org.sosy_lab.common.LogManager;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.FileOption;
import org.sosy_lab.common.configuration.FileOption.Type;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.io.Path;
import org.sosy_lab.common.io.Paths;
import org.sosy_lab.cpachecker.core.ShutdownNotifier;
import org.sosy_lab.cpachecker.util.predicates.interfaces.BooleanFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.Formula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.basicimpl.AbstractBitvectorFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.basicimpl.AbstractBooleanFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.basicimpl.AbstractFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.basicimpl.AbstractFunctionFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.basicimpl.AbstractRationalFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.basicimpl.AbstractUnsafeFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.basicimpl.FormulaCreator;
import org.sosy_lab.cpachecker.util.predicates.mathsat5.Mathsat5NativeApi.TerminationTest;

import com.google.common.base.Splitter;
import com.google.common.base.Splitter.MapSplitter;
import com.google.common.collect.ImmutableMap;

@Options(prefix="cpa.predicate.mathsat5")
public class Mathsat5FormulaManager extends AbstractFormulaManager<Long> implements AutoCloseable {

  @Options(prefix="cpa.predicate.mathsat5")
  private static class Mathsat5Settings {

    @Option(description = "List of further options which will be passed to Mathsat in addition to the default options. "
        + "Format is 'key1=value1,key2=value2'")
    private String furtherOptions = "";

    @Option(description = "Export solver queries in Smtlib format into a file (for Mathsat5).")
    private boolean logAllQueries = false;

    @Option(description = "Export solver queries in Smtlib format into a file (for Mathsat5).")
    @FileOption(Type.OUTPUT_FILE)
    private Path logfile = Paths.get("mathsat5.%03d.smt2");

    private final ImmutableMap<String, String> furtherOptionsMap ;

    private Mathsat5Settings(Configuration config) throws InvalidConfigurationException {
      config.inject(this);

      MapSplitter optionSplitter = Splitter.on(',').trimResults().omitEmptyStrings()
                      .withKeyValueSeparator(Splitter.on('=').limit(2).trimResults());

      try {
        furtherOptionsMap = ImmutableMap.copyOf(optionSplitter.split(furtherOptions));
      } catch (IllegalArgumentException e) {
        throw new InvalidConfigurationException("Invalid Mathsat option in \"" + furtherOptions + "\": " + e.getMessage(), e);
      }
    }
  }

  private final LogManager logger;

  private final Mathsat5FormulaCreator formulaCreator;
  private final long mathsatEnv;
  private final long mathsatConfig;
  private final Mathsat5Settings settings;
  private static final AtomicInteger logfileCounter = new AtomicInteger(0);

  private final ShutdownNotifier shutdownNotifier;
  private final TerminationTest terminationTest;

  private Mathsat5FormulaManager(
      LogManager pLogger,
      long pMathsatConfig,
      AbstractUnsafeFormulaManager<Long> unsafeManager,
      AbstractFunctionFormulaManager<Long> pFunctionManager,
      AbstractBooleanFormulaManager<Long> pBooleanManager,
      AbstractRationalFormulaManager<Long> pNumericManager,
      AbstractBitvectorFormulaManager<Long> pBitpreciseManager,
      Mathsat5Settings pSettings,
      final ShutdownNotifier pShutdownNotifier) {

    super(unsafeManager, pFunctionManager, pBooleanManager, pNumericManager, pBitpreciseManager);
    FormulaCreator<Long> creator = getFormulaCreator();
    if (!(creator instanceof Mathsat5FormulaCreator)) {
      throw new IllegalArgumentException("the formel-creator has to be a Mathsat5FormulaCreator instance!");
    }
    formulaCreator = (Mathsat5FormulaCreator) getFormulaCreator();
    mathsatConfig = pMathsatConfig;
    mathsatEnv = formulaCreator.getEnv();
    settings = pSettings;
    logger = checkNotNull(pLogger);

    shutdownNotifier = checkNotNull(pShutdownNotifier);
    terminationTest = new TerminationTest() {
        @Override
        public boolean shouldTerminate() throws InterruptedException {
          pShutdownNotifier.shutdownIfNecessary();
          return false;
        }
      };
  }

  ShutdownNotifier getShutdownNotifier() {
    return shutdownNotifier;
  }

  static long getMsatTerm(Formula pT) {
    return ((Mathsat5Formula)pT).getTerm();
  }

  public static Mathsat5FormulaManager create(LogManager logger,
      Configuration config, ShutdownNotifier pShutdownNotifier, boolean useIntegers) throws InvalidConfigurationException {

    // Init Msat
    Mathsat5Settings settings = new Mathsat5Settings(config);

    long msatConf = msat_create_config();
    msat_set_option_checked(msatConf, "theory.la.split_rat_eq", "false");

    for (Map.Entry<String, String> option : settings.furtherOptionsMap.entrySet()) {
      try {
        msat_set_option_checked(msatConf, option.getKey(), option.getValue());
      } catch (IllegalArgumentException e) {
        throw new InvalidConfigurationException(e.getMessage(), e);
      }
    }

    final long msatEnv = msat_create_env(msatConf);

    // Create Mathsat5FormulaCreator
    Mathsat5FormulaCreator creator = new Mathsat5FormulaCreator(msatEnv, useIntegers);

    // Create managers
    Mathsat5UnsafeFormulaManager unsafeManager = new Mathsat5UnsafeFormulaManager(creator);
    Mathsat5FunctionFormulaManager functionTheory = new Mathsat5FunctionFormulaManager(creator, unsafeManager);
    Mathsat5BooleanFormulaManager booleanTheory = Mathsat5BooleanFormulaManager.create(creator);
    Mathsat5RationalFormulaManager rationalTheory = new Mathsat5RationalFormulaManager(creator, functionTheory, useIntegers);
    Mathsat5BitvectorFormulaManager bitvectorTheory  = Mathsat5BitvectorFormulaManager.create(creator);

    return new Mathsat5FormulaManager(logger, msatConf,
        unsafeManager, functionTheory, booleanTheory,
        rationalTheory, bitvectorTheory, settings, pShutdownNotifier);
  }

  BooleanFormula encapsulateBooleanFormula(long t) {
    return formulaCreator.encapsulate(BooleanFormula.class, t);
  }

  @Override
  public BooleanFormula parse(String pS) throws IllegalArgumentException {
    long f = msat_from_smtlib2(mathsatEnv, pS);
    return encapsulateBooleanFormula(f);
  }

  @Override
  public Appender dumpFormula(final Long f) {
    // Lazy invocation of msat_to_smtlib2 wrapped in an Appender.
    return Appenders.fromToStringMethod(
        new Object() {
          @Override
          public String toString() {
            return msat_to_smtlib2(mathsatEnv, f);
          }
        });
  }


  @Override
  public String getVersion() {
    return msat_get_version();
  }

  long createEnvironment(long cfg, boolean shared, boolean ghostFilter) {
    long env;

    if (ghostFilter) {
      msat_set_option_checked(cfg, "dpll.ghost_filtering", "true");
    }

    msat_set_option_checked(cfg, "theory.la.split_rat_eq", "false");

    for (Map.Entry<String, String> option : settings.furtherOptionsMap.entrySet()) {
      msat_set_option_checked(cfg, option.getKey(), option.getValue());
    }

    if (settings.logAllQueries && settings.logfile != null) {
      String filename = String.format(settings.logfile.toAbsolutePath().getPath(), logfileCounter.getAndIncrement());

      msat_set_option_checked(cfg, "debug.api_call_trace", "1");
      msat_set_option_checked(cfg, "debug.api_call_trace_filename", filename);
    }

    if (shared) {
      env = msat_create_shared_env(cfg, this.mathsatEnv);
    } else {
      env = msat_create_env(cfg);
    }

    return env;
  }

  long addTerminationTest(long env) {
    return msat_set_termination_test(env, terminationTest);
  }

  long getMsatEnv() {
    return mathsatEnv;
  }

  @Override
  public void close() {
    logger.log(Level.FINER, "Freeing Mathsat environment");
    msat_destroy_env(mathsatEnv);
    msat_destroy_config(mathsatConfig);
  }
}
