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
package org.sosy_lab.cpachecker.cfa.parser.eclipse;

import static org.sosy_lab.cpachecker.cfa.CFACreationUtils.isReachableNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.logging.Level;

import org.eclipse.cdt.core.dom.ast.ASTVisitor;
import org.eclipse.cdt.core.dom.ast.IASTASMDeclaration;
import org.eclipse.cdt.core.dom.ast.IASTBreakStatement;
import org.eclipse.cdt.core.dom.ast.IASTCompoundStatement;
import org.eclipse.cdt.core.dom.ast.IASTContinueStatement;
import org.eclipse.cdt.core.dom.ast.IASTDeclaration;
import org.eclipse.cdt.core.dom.ast.IASTDeclarationStatement;
import org.eclipse.cdt.core.dom.ast.IASTExpression;
import org.eclipse.cdt.core.dom.ast.IASTExpressionStatement;
import org.eclipse.cdt.core.dom.ast.IASTFileLocation;
import org.eclipse.cdt.core.dom.ast.IASTForStatement;
import org.eclipse.cdt.core.dom.ast.IASTFunctionDefinition;
import org.eclipse.cdt.core.dom.ast.IASTGotoStatement;
import org.eclipse.cdt.core.dom.ast.IASTIfStatement;
import org.eclipse.cdt.core.dom.ast.IASTLabelStatement;
import org.eclipse.cdt.core.dom.ast.IASTLiteralExpression;
import org.eclipse.cdt.core.dom.ast.IASTNullStatement;
import org.eclipse.cdt.core.dom.ast.IASTProblem;
import org.eclipse.cdt.core.dom.ast.IASTProblemDeclaration;
import org.eclipse.cdt.core.dom.ast.IASTProblemStatement;
import org.eclipse.cdt.core.dom.ast.IASTReturnStatement;
import org.eclipse.cdt.core.dom.ast.IASTSimpleDeclaration;
import org.eclipse.cdt.core.dom.ast.IASTStatement;
import org.eclipse.cdt.core.dom.ast.IASTTranslationUnit;
import org.eclipse.cdt.core.dom.ast.IASTWhileStatement;
import org.sosy_lab.common.LogManager;
import org.sosy_lab.cpachecker.cfa.CFACreationUtils;
import org.sosy_lab.cpachecker.cfa.ast.IASTExpressionAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.IASTFunctionCallAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.IASTIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.IASTNode;
import org.sosy_lab.cpachecker.cfa.ast.StorageClass;
import org.sosy_lab.cpachecker.cfa.objectmodel.BlankEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFAEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFAFunctionDefinitionNode;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFAFunctionExitNode;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFALabelNode;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFANode;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.AssumeEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.DeclarationEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.FunctionDefinitionNode;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.ReturnStatementEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.StatementEdge;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.SortedSetMultimap;
import com.google.common.collect.TreeMultimap;

/**
 * Builder to traverse AST.
 * Known Limitations:
 * <p> -- K&R style function definitions not implemented
 * <p> -- Pointer modifiers not tracked (i.e. const, volatile, etc. for *
 */
class CFABuilder extends ASTVisitor
{
  // Data structure for maintaining our scope stack in a function
  private final Deque<CFANode> locStack = new ArrayDeque<CFANode>();

  // Data structures for handling loops & else conditions
  private final Deque<CFANode> loopStartStack = new ArrayDeque<CFANode>();
  private final Deque<CFANode> loopNextStack  = new ArrayDeque<CFANode>(); // For the node following the current if / while block
  private final Deque<CFANode> elseStack      = new ArrayDeque<CFANode>();

  // Data structures for handling goto
  private final Map<String, CFALabelNode> labelMap = new HashMap<String, CFALabelNode>();
  private final Multimap<String, CFANode> gotoLabelNeeded = ArrayListMultimap.create();

  // Data structures for handling function declarations
  private final Map<String, CFAFunctionDefinitionNode> cfas = new HashMap<String, CFAFunctionDefinitionNode>();
  private final SortedSetMultimap<String, CFANode> cfaNodes = TreeMultimap.create();
  private CFAFunctionDefinitionNode currentCFA = null;
  private SortedSet<CFANode> currentCFANodes = null;

  // Data structure for storing global declarations
  private final List<org.sosy_lab.cpachecker.cfa.ast.IASTDeclaration> globalDeclarations = Lists.newArrayList();

  private final Scope scope = new Scope();
  private final ASTConverter astCreator;

  private final LogManager logger;

  public CFABuilder(LogManager logger, boolean pIgnoreCasts) {
    this.logger = logger;
    astCreator = new ASTConverter(scope, pIgnoreCasts);
    if (pIgnoreCasts) {
      logger.log(Level.WARNING, "Ignoring all casts in the program because of user request!");
    }

    //shouldVisitComments = false;
    shouldVisitDeclarations = true;
    shouldVisitDeclarators = false;
    shouldVisitDeclSpecifiers = false;
    shouldVisitEnumerators = true;
    shouldVisitExpressions = false;
    shouldVisitInitializers = false;
    shouldVisitNames = false;
    shouldVisitParameterDeclarations = true;
    shouldVisitProblems = true;
    shouldVisitStatements = true;
    shouldVisitTranslationUnit = false;
    shouldVisitTypeIds = false;
  }

  /**
   * Retrieves list of all functions
   * @return all CFAs in the program
   */
  public Map<String, CFAFunctionDefinitionNode> getCFAs()  {
    return cfas;
  }

  /**
   * Retrieves list of all nodes
   * @return all CFAs in the program
   */
  public SortedSetMultimap<String, CFANode> getCFANodes()  {
    return cfaNodes;
  }

  /**
   * Retrieves list of all global declarations
   * @return global declarations
   */
  public List<org.sosy_lab.cpachecker.cfa.ast.IASTDeclaration> getGlobalDeclarations ()
  {
    return globalDeclarations;
  }

  /* (non-Javadoc)
   * @see org.eclipse.cdt.core.dom.ast.ASTVisitor#visit(org.eclipse.cdt.core.dom.ast.IASTDeclaration)
   */
  @Override
  public int visit (IASTDeclaration declaration)
  {
    IASTFileLocation fileloc = declaration.getFileLocation ();

    if (declaration instanceof IASTSimpleDeclaration) {
      return handleSimpleDeclaration((IASTSimpleDeclaration)declaration, fileloc);

    } else if (declaration instanceof IASTFunctionDefinition) {
      return handleFunctionDefinition((IASTFunctionDefinition)declaration, fileloc);

    } else if (declaration instanceof IASTProblemDeclaration) {
      // CDT parser struggles on GCC's __attribute__((something)) constructs because we use C99 as default
      // Either insert the following macro before compiling with CIL:
      // #define  __attribute__(x)  /*NOTHING*/
      // or insert "parser.dialect = GNUC" into properties file
      visit(((IASTProblemDeclaration)declaration).getProblem());
      return PROCESS_SKIP;

    } else if (declaration instanceof IASTASMDeclaration) {
      // TODO Assembler code is ignored here
      return ignoreASMDeclaration(fileloc);

    } else {
      throw new CFAGenerationRuntimeException("Unknown declaration type " + declaration.getClass().getSimpleName(),  declaration);
    }
  }

  private int handleSimpleDeclaration(final IASTSimpleDeclaration sd, final IASTFileLocation fileloc){
    final List<org.sosy_lab.cpachecker.cfa.ast.IASTDeclaration> newDs = astCreator.convert(sd);
    assert !newDs.isEmpty();

    for (org.sosy_lab.cpachecker.cfa.ast.IASTDeclaration newD : newDs) {
      if (newD.getStorageClass() != StorageClass.TYPEDEF
          && newD.getName() != null) {
        // this is neither a typedef nor a struct prototype nor a function declaration,
        // so it's a variable declaration

        scope.registerDeclaration(newD);
      }
    }

    if (locStack.size () > 0) {// i.e. we're in a function

      CFANode prevNode = locStack.pop();
      CFANode nextNode = null;

      for (org.sosy_lab.cpachecker.cfa.ast.IASTDeclaration newD : newDs) {
        assert !newD.isGlobal();

        nextNode = new CFANode(fileloc.getStartingLineNumber(), currentCFA.getFunctionName());
        currentCFANodes.add(nextNode);

        final DeclarationEdge edge = new DeclarationEdge(newD, fileloc.getStartingLineNumber(), prevNode, nextNode);
        addToCFA(edge);

        prevNode = nextNode;
      }

      assert nextNode != null;
      locStack.push(nextNode);

    } else {
      assert sd.getParent() instanceof IASTTranslationUnit : "not a real global declaration";

      // else we're in the global scope
      globalDeclarations.addAll(newDs);
    }

    return PROCESS_SKIP; // important to skip here, otherwise we would visit nested declarations
  }

  private int handleFunctionDefinition(final IASTFunctionDefinition declaration,
      final IASTFileLocation fileloc) {

    if (locStack.size () != 0) {
      throw new CFAGenerationRuntimeException("Nested function declarations?");
    }

    assert labelMap.isEmpty();
    assert gotoLabelNeeded.isEmpty();
    assert currentCFA == null;
    assert currentCFANodes == null;

    final org.sosy_lab.cpachecker.cfa.ast.IASTFunctionDefinition fdef = astCreator.convert(declaration);
    final String nameOfFunction = fdef.getName();
    assert !nameOfFunction.isEmpty();

    if (cfas.containsKey(nameOfFunction)) {
      throw new CFAGenerationRuntimeException("Duplicate function " + nameOfFunction, declaration);
    }

    scope.enterFunction(fdef);

    final List<org.sosy_lab.cpachecker.cfa.ast.IASTParameterDeclaration> parameters = fdef.getDeclSpecifier().getParameters();
    final List<String> parameterNames = new ArrayList<String>(parameters.size());

    for (org.sosy_lab.cpachecker.cfa.ast.IASTParameterDeclaration param : parameters) {
      scope.registerDeclaration(param); // declare parameter as local variable
      parameterNames.add(param.getName());
    }

    currentCFANodes = cfaNodes.get(nameOfFunction);

    final CFAFunctionExitNode returnNode = new CFAFunctionExitNode(fileloc.getEndingLineNumber(), nameOfFunction);
    currentCFANodes.add(returnNode);

    final CFAFunctionDefinitionNode startNode = new FunctionDefinitionNode(
        fileloc.getStartingLineNumber(), nameOfFunction, fdef, returnNode, parameters, parameterNames);
    currentCFANodes.add(startNode);
    cfas.put(nameOfFunction, startNode);
    currentCFA = startNode;

    final CFANode nextNode = new CFANode(fileloc.getStartingLineNumber(), nameOfFunction);
    currentCFANodes.add(nextNode);
    locStack.add(nextNode);

    final BlankEdge dummyEdge = new BlankEdge("Function start dummy edge",
        fileloc.getStartingLineNumber(), startNode, nextNode);
    addToCFA(dummyEdge);

    return PROCESS_CONTINUE;
  }

  private int ignoreASMDeclaration(final IASTFileLocation fileloc){
    // TODO Assembler code is ignored here
    logger.log(Level.WARNING, "Ignoring inline assembler code at line "
        + fileloc.getStartingLineNumber() + ", analysis is probably unsound!");

    // locStack may be empty here, which happens when there is assembler code
    // outside of a function
    if (!locStack.isEmpty()) {
      final CFANode prevNode = locStack.pop();

      final CFANode nextNode = new CFANode(fileloc.getStartingLineNumber(), currentCFA.getFunctionName());
      currentCFANodes.add(nextNode);
      locStack.push(nextNode);

      final BlankEdge edge = new BlankEdge("Ignored inline assembler code",
          fileloc.getStartingLineNumber(), prevNode, nextNode);
      addToCFA(edge);
    }
    return PROCESS_SKIP;
  }

  @Override
  public int leave (IASTDeclaration declaration)
  {
    if (declaration instanceof IASTFunctionDefinition)
    {
      if (locStack.size () != 1)
        throw new CFAGenerationRuntimeException ("Depth wrong. Geoff needs to do more work");

      CFANode lastNode = locStack.pop ();

      if (isReachableNode(lastNode)) {
        BlankEdge blankEdge = new BlankEdge ("default return", lastNode.getLineNumber(), lastNode, currentCFA.getExitNode());
        addToCFA(blankEdge);
      }

      if (!gotoLabelNeeded.isEmpty()) {
        throw new CFAGenerationRuntimeException("Following labels were not found in function " + currentCFA.getFunctionName() + ": " + gotoLabelNeeded.keySet());
      }

      for (CFALabelNode n : labelMap.values()) {
        if (n.getNumEnteringEdges() == 0) {
          logger.log(Level.INFO, "Dead code detected at line", n.getLineNumber() + ": Label", n.getLabel(), "is not reachable.");

          // remove this dead code from CFA
          CFACreationUtils.removeChainOfNodesFromCFA(n);
        }
      }

      labelMap.clear();

      Iterator<CFANode> it = currentCFANodes.iterator();
      while (it.hasNext()) {
        CFANode n = it.next();
        if (n.getNumEnteringEdges() == 0 && n.getNumLeavingEdges() == 0) {
          // node was created but isn't part of CFA (e.g. because of dead code)
          it.remove(); // remove n from currentCFANodes
        }
      }
      currentCFANodes = null;

      currentCFA = null;
      scope.leaveFunction();
    }

    return PROCESS_CONTINUE;
  }

  // Methods for to handle visiting and leaving Statements
  /* (non-Javadoc)
   * @see org.eclipse.cdt.core.dom.ast.ASTVisitor#visit(org.eclipse.cdt.core.dom.ast.IASTStatement)
   */
  @Override
  public int visit (IASTStatement statement)
  {
    IASTFileLocation fileloc = statement.getFileLocation ();

    // Handle special condition for else
    if (statement.getPropertyInParent () == IASTIfStatement.ELSE)
    {
      // Edge from current location to post if-statement location
      CFANode prevNode = locStack.pop ();
      CFANode nextNode = locStack.peek ();

      if (isReachableNode(prevNode)) {
        BlankEdge blankEdge = new BlankEdge ("", nextNode.getLineNumber(), prevNode, nextNode);
        addToCFA(blankEdge);
      }

      //  Push the start of the else clause onto our location stack
      CFANode elseNode = elseStack.pop ();
      locStack.push (elseNode);
    }

    // Handle each kind of expression
    if (statement instanceof IASTCompoundStatement)
    {
      scope.enterBlock();
      // Do nothing, just continue visiting
    }
    else if (statement instanceof IASTExpressionStatement)
      handleExpressionStatement ((IASTExpressionStatement)statement, fileloc);
    else if (statement instanceof IASTIfStatement)
      handleIfStatement ((IASTIfStatement)statement, fileloc);
    else if (statement instanceof IASTWhileStatement)
      handleWhileStatement ((IASTWhileStatement)statement, fileloc);
    else if (statement instanceof IASTForStatement)
      return visitForStatement ((IASTForStatement)statement, fileloc);
    else if (statement instanceof IASTBreakStatement)
      handleBreakStatement ((IASTBreakStatement)statement, fileloc);
    else if (statement instanceof IASTContinueStatement)
      handleContinueStatement ((IASTContinueStatement)statement, fileloc);
    else if (statement instanceof IASTLabelStatement)
      handleLabelStatement ((IASTLabelStatement)statement, fileloc);
    else if (statement instanceof IASTGotoStatement)
      handleGotoStatement ((IASTGotoStatement)statement, fileloc);
    else if (statement instanceof IASTReturnStatement)
      handleReturnStatement ((IASTReturnStatement)statement, fileloc);
/* switch statements are removed by CIL
    else if (statement instanceof IASTSwitchStatement)
      handleSwitchStatement ((IASTSwitchStatement)statement, fileloc);
    else if (statement instanceof IASTCaseStatement)
      handleCaseStatement ((IASTCaseStatement)statement, fileloc);
    else if (statement instanceof IASTDefaultStatement)
      handleDefaultStatement ((IASTDefaultStatement)statement, fileloc);
*/
    else if (statement instanceof IASTNullStatement)
    {
      // We really don't care about blank statements
    }
    else if (statement instanceof IASTDeclarationStatement)
    {
      // TODO: I think we can ignore these here...
    } else if (statement instanceof IASTProblemStatement) {
      visit(((IASTProblemStatement)statement).getProblem());
    }
    else {
      throw new CFAGenerationRuntimeException("Unknown AST node " + statement.getClass().getSimpleName() + " in line " + fileloc.getStartingLineNumber() + ": " + statement.getRawSignature());
    }

    return PROCESS_CONTINUE;
  }

  private void handleExpressionStatement (IASTExpressionStatement exprStatement, IASTFileLocation fileloc)
  {
    // check, if the exprStatement is the initializer of a forLoop, i.e. "counter=0;"
    // then we can ignore it, because it is handled with the loopstart
    if (exprStatement.getParent() instanceof IASTForStatement
        && exprStatement == ((IASTForStatement) exprStatement.getParent()).getInitializerStatement()) {
      return;
    }

    CFANode prevNode = locStack.pop ();

    CFANode nextNode = new CFANode(fileloc.getStartingLineNumber(), currentCFA.getFunctionName());
    currentCFANodes.add(nextNode);
    locStack.push(nextNode);

    StatementEdge edge = new StatementEdge(astCreator.convert(exprStatement), fileloc.getStartingLineNumber(), prevNode, nextNode);
    addToCFA(edge);
  }

  private static enum CONDITION {  NORMAL, ALWAYS_FALSE, ALWAYS_TRUE };

  private CONDITION getConditionKind(final IASTExpression cond) {
      if (cond instanceof IASTLiteralExpression) {
          if (((IASTLiteralExpression)cond).getKind() ==
              IASTLiteralExpression.lk_integer_constant) {
              int c = Integer.parseInt(cond.getRawSignature());
              if (c == 0) return CONDITION.ALWAYS_FALSE;
              else return CONDITION.ALWAYS_TRUE;
          }
      }
      return CONDITION.NORMAL;
  }

  private void handleIfStatement(IASTIfStatement ifStatement, IASTFileLocation fileloc) {
    CFANode prevNode = locStack.pop();

    CFANode postIfNode = new CFANode(fileloc.getEndingLineNumber(), currentCFA.getFunctionName());
    currentCFANodes.add(postIfNode);
    locStack.push(postIfNode);

    CFANode thenNode = new CFANode(fileloc.getStartingLineNumber(), currentCFA.getFunctionName());
    currentCFANodes.add(thenNode);
    locStack.push(thenNode);

    CFANode elseNode;
    // elseNode is the start of the else branch, or the node after the loop if there is no else branch
    if (ifStatement.getElseClause() == null) {
      elseNode = postIfNode;
    } else {
      elseNode = new CFANode(fileloc.getStartingLineNumber(), currentCFA.getFunctionName());
      currentCFANodes.add(elseNode);
      elseStack.push(elseNode);
    }

    createConditionEdges(ifStatement.getConditionExpression(), fileloc.getStartingLineNumber(),
        prevNode, thenNode, elseNode);
  }

  private void handleWhileStatement (IASTWhileStatement whileStatement, IASTFileLocation fileloc)
  {
    final CFANode prevNode = locStack.pop ();

    final CFANode loopStart = new CFANode(fileloc.getStartingLineNumber(), currentCFA.getFunctionName());
    currentCFANodes.add(loopStart);
    loopStart.setLoopStart();
    loopStartStack.push(loopStart);

    final CFANode firstLoopNode = new CFANode(fileloc.getStartingLineNumber(), currentCFA.getFunctionName());
    currentCFANodes.add(firstLoopNode);

    final CFANode postLoopNode = new CFANode(fileloc.getEndingLineNumber(), currentCFA.getFunctionName());
    currentCFANodes.add(postLoopNode);
    loopNextStack.push(postLoopNode);

    // inverse order here!
    locStack.push(postLoopNode);
    locStack.push(firstLoopNode);

    final BlankEdge blankEdge = new BlankEdge("while", fileloc.getStartingLineNumber(), prevNode, loopStart);
    addToCFA(blankEdge);

    createConditionEdges(whileStatement.getCondition(), fileloc.getStartingLineNumber(),
        loopStart, firstLoopNode, postLoopNode);
  }

  /** This function creates the edges of a condition:
   * if ALWAYS_TRUE or ALWAYS_FALSE only one blankEdge is created.
   * if NORMAL two edges are created: one 'then'-edge and one 'else'-edge. */
  private void createConditionEdges(final IASTExpression condition, final int filelocStart,
      final CFANode rootNode, final CFANode thenNode, final CFANode elseNode) {

    assert condition != null;

    final CONDITION kind = getConditionKind(condition);

    switch (kind) {
    case ALWAYS_FALSE:
      // no edge connecting rootNode with thenNode,
      // so the "then" branch won't be connected to the rest of the CFA

      final BlankEdge falseEdge = new BlankEdge("", filelocStart, rootNode, elseNode);
      addToCFA(falseEdge);
      break;

    case ALWAYS_TRUE:
      final BlankEdge trueEdge = new BlankEdge("", filelocStart, rootNode, thenNode);
      addToCFA(trueEdge);

      // no edge connecting prevNode with elseNode,
      // so the "else" branch won't be connected to the rest of the CFA
      break;

    case NORMAL:
      final org.sosy_lab.cpachecker.cfa.ast.IASTExpression exp =
        astCreator.convertExpressionWithoutSideEffects(condition);

      // edge connecting rootNode with elseNode
      final AssumeEdge assumeEdgeFalse = new AssumeEdge("!(" + condition.getRawSignature() + ")",
          filelocStart, rootNode, elseNode, exp, false);
      addToCFA(assumeEdgeFalse);

      // edge connecting rootNode with thenNode
      final AssumeEdge assumeEdgeTrue = new AssumeEdge(condition.getRawSignature(),
          filelocStart, rootNode, thenNode, exp, true);
      addToCFA(assumeEdgeTrue);
      break;

    default:
      throw new InternalError("Missing switch clause");
    }
  }

  private int visitForStatement (final IASTForStatement forStatement, final IASTFileLocation fileloc)
  {
    final int filelocStart = fileloc.getStartingLineNumber();
    final CFANode prevNode = locStack.pop();

    // loopInit is Node before "counter = 0;"
    final CFANode loopInit = new CFANode(filelocStart, currentCFA.getFunctionName());
    currentCFANodes.add(loopInit);
    addToCFA(new BlankEdge("for", filelocStart, prevNode, loopInit));

    // loopStart is the Node before the loop itself
    final CFANode loopStart = new CFANode(filelocStart, currentCFA.getFunctionName());
    currentCFANodes.add(loopStart);
    loopStart.setLoopStart();

    // init-edge from loopinit to loopstart
    createInitEdgeForForLoop(forStatement.getInitializerStatement(), filelocStart, loopInit, loopStart);

    // loopEnd is Node before "counter++;"
    final CFANode loopEnd = new CFALabelNode(filelocStart, currentCFA.getFunctionName(), "");
    currentCFANodes.add(loopEnd);
    loopStartStack.push(loopEnd);

    // this edge connects loopEnd with loopStart and contains the statement "counter++;"
    createLastEdgeForForLoop(forStatement.getIterationExpression(), filelocStart, loopEnd, loopStart);

    // firstLoopNode is Node after "counter < 5"
    final CFANode firstLoopNode = new CFANode(
        filelocStart, currentCFA.getFunctionName());
    currentCFANodes.add(firstLoopNode);

    // firstLoopNode is Node after "!(counter < 5)"
    final CFANode postLoopNode = new CFANode(
        fileloc.getEndingLineNumber(), currentCFA.getFunctionName());
    currentCFANodes.add(postLoopNode);
    loopNextStack.push(postLoopNode);

    // inverse order here!
    locStack.push(postLoopNode);
    locStack.push(firstLoopNode);

    createConditionEdgesForForLoop(forStatement.getConditionExpression(),
        filelocStart, loopStart, postLoopNode, firstLoopNode);

    // visit only loopbody, not children, loop.getBody() != loop.getChildren()
    forStatement.getBody().accept(this);

    // leave loop
    final CFANode lastNodeInLoop = locStack.pop();

    // loopEnd is the Node before "counter++;"
    assert loopEnd == loopStartStack.pop();
    assert postLoopNode == loopNextStack.pop();
    assert postLoopNode == locStack.peek();

    if (isReachableNode(lastNodeInLoop)) {
      final BlankEdge blankEdge = new BlankEdge("", lastNodeInLoop.getLineNumber(), lastNodeInLoop, loopEnd);
      addToCFA(blankEdge);
    }

    // skip visiting children of loop, because loopbody was handled before
    return PROCESS_SKIP;
  }

  /** This function creates the edge for the init-statement of a for-loop.
   * The edge is inserted between the loopInit-Node and loopStart-Node.
   * If there are more than one declarations, more edges are inserted. */
  private void createInitEdgeForForLoop(final IASTStatement statement,
      final int filelocStart, CFANode loopInit, final CFANode loopStart) {

    // "int counter = 0;"
    if (statement instanceof IASTDeclarationStatement &&
        ((IASTDeclarationStatement)statement).getDeclaration() instanceof IASTSimpleDeclaration) {

      // convert
      final IASTDeclaration eclipseDecl = ((IASTDeclarationStatement)statement).getDeclaration();
      final List<org.sosy_lab.cpachecker.cfa.ast.IASTDeclaration> declList =
          astCreator.convert((IASTSimpleDeclaration)eclipseDecl);

      // add to CFA
      addDeclarationsToCFA(declList, filelocStart, loopInit, loopStart);

    // "counter = 0;"
    } else if (statement instanceof IASTExpressionStatement) {
      final StatementEdge initEdge = new StatementEdge(
              astCreator.convert((IASTExpressionStatement) statement),
              filelocStart, loopInit, loopStart);
      addToCFA(initEdge);

    //";"
    } else if (statement instanceof IASTNullStatement) {
      addToCFA(new BlankEdge("", filelocStart, loopInit, loopStart));

    } else { // TODO: are there other init-statements in a for-loop?
      throw new AssertionError("CFABuilder: unknown init-statement in for-statement:\n"
          + statement.getClass());
    }
  }

  /** This method takes a list of Declarations and adds them to the CFA.
   * The edges are inserted between startNode and endNode. */
  private void addDeclarationsToCFA(final List<org.sosy_lab.cpachecker.cfa.ast.IASTDeclaration> declList,
      final int filelocStart, CFANode startNode, final CFANode endNode) {

    // create one edge for every declaration
    // (if there is only one declaration, this loop is skipped)
    for (int i = 0; i < declList.size() - 1; i++) {
      final CFANode nextNode = new CFANode(filelocStart, currentCFA.getFunctionName());
      currentCFANodes.add(nextNode);

      final org.sosy_lab.cpachecker.cfa.ast.IASTDeclaration decl = declList.get(i);
      final DeclarationEdge initEdge = new DeclarationEdge(decl, filelocStart, startNode, nextNode);
      addToCFA(initEdge);

      startNode = nextNode;

      // storageClass must not be typedef, struct prototype or function declaration
      if (decl.getStorageClass() != StorageClass.TYPEDEF && decl.getName() != null) {
        scope.registerDeclaration(decl);
      }
    }

    // create the last declaration-edge (if only one declaration, this is the only edge)
    final org.sosy_lab.cpachecker.cfa.ast.IASTDeclaration decl = declList.get(declList.size() - 1);
    final DeclarationEdge initEdge = new DeclarationEdge(decl, filelocStart, startNode, endNode);
    addToCFA(initEdge);

    // storageClass must not be typedef, struct prototype or function declaration
    if (decl.getStorageClass() != StorageClass.TYPEDEF && decl.getName() != null) {
      scope.registerDeclaration(decl);
    }

  }

  /** This function creates the last edge in a loop (= iteration-edge).
   * The edge contains the statement "counter++" or something similar
   * and is inserted between the loopEnd-Node and the loopStart-Node. */
  private void createLastEdgeForForLoop(final IASTExpression exp, final int filelocStart,
      final CFANode loopEnd, final CFANode loopStart) {
    final IASTNode node = astCreator.convertExpressionWithSideEffects(exp);

    if (exp == null) {
      // ignore, only add blankEdge
      final BlankEdge blankEdge = new BlankEdge("", filelocStart, loopEnd, loopStart);
      addToCFA(blankEdge);

      // "counter;"
    } else if (node instanceof IASTIdExpression) {
      final BlankEdge blankEdge = new BlankEdge(
          node.getRawSignature(), filelocStart, loopEnd, loopStart);
      addToCFA(blankEdge);

      // "counter++;"
    } else if (node instanceof IASTExpressionAssignmentStatement) {
      final StatementEdge lastEdge = new StatementEdge(
          (IASTExpressionAssignmentStatement) node, filelocStart, loopEnd, loopStart);
      addToCFA(lastEdge);

    } else if (node instanceof IASTFunctionCallAssignmentStatement) {
      final StatementEdge edge = new StatementEdge((IASTFunctionCallAssignmentStatement)node,
          filelocStart, loopEnd, loopStart);
      addToCFA(edge);

    } else { // TODO: are there other iteration-expressions in a for-loop?
      throw new AssertionError("CFABuilder: unknown iteration-expressions in for-statement:\n"
          + exp.getClass());
    }
  }

  /** This function creates the condition-edges of a for-loop.
   * Normally there are 2 edges: one 'then'-edge and one 'else'-edge.
   * If the condition is ALWAYS_TRUE or ALWAYS_FALSE or 'null' only one edge is created. */
  private void createConditionEdgesForForLoop(final IASTExpression condition, final int filelocStart,
      final CFANode loopStart, final CFANode postLoopNode, final CFANode firstLoopNode) {
    if (condition == null) {
      // no condition -> only a blankEdge from loopStart to firstLoopNode
      final BlankEdge blankEdge = new BlankEdge("", filelocStart, loopStart, firstLoopNode);
      addToCFA(blankEdge);

    } else {
      createConditionEdges(condition, filelocStart, loopStart, firstLoopNode, postLoopNode);
    }
  }

  private void handleBreakStatement (IASTBreakStatement breakStatement, IASTFileLocation fileloc)
  {
    CFANode prevNode = locStack.pop();
    CFANode postLoopNode = loopNextStack.peek();

    BlankEdge blankEdge = new BlankEdge(breakStatement.getRawSignature(), fileloc.getStartingLineNumber(), prevNode, postLoopNode, true);
    addToCFA(blankEdge);

    CFANode nextNode = new CFANode(fileloc.getEndingLineNumber(), currentCFA.getFunctionName());
    currentCFANodes.add(nextNode);
    locStack.push(nextNode);
  }

  private void handleContinueStatement (IASTContinueStatement continueStatement, IASTFileLocation fileloc)
  {
    CFANode prevNode = locStack.pop();
    CFANode loopStartNode = loopStartStack.peek();

    BlankEdge blankEdge = new BlankEdge(continueStatement.getRawSignature(), fileloc.getStartingLineNumber(), prevNode, loopStartNode, true);
    addToCFA(blankEdge);

    CFANode nextNode = new CFANode(fileloc.getEndingLineNumber(), currentCFA.getFunctionName());
    locStack.push(nextNode);
  }

  private void handleLabelStatement (IASTLabelStatement labelStatement, IASTFileLocation fileloc)
  {
    String labelName = labelStatement.getName ().toString ();
    if (labelMap.containsKey(labelName)) {
      throw new CFAGenerationRuntimeException("Duplicate label " + labelName + " in function " + currentCFA.getFunctionName(), labelStatement);
    }

    CFANode prevNode = locStack.pop();

    CFALabelNode labelNode = new CFALabelNode(fileloc.getStartingLineNumber(), currentCFA.getFunctionName(), labelName);
    currentCFANodes.add(labelNode);
    locStack.push(labelNode);
    labelMap.put(labelName, labelNode);

    if (isReachableNode(prevNode)) {
      BlankEdge blankEdge = new BlankEdge("Label: " + labelName, fileloc.getStartingLineNumber(), prevNode, labelNode);
      addToCFA(blankEdge);
    }

    // Check if any goto's previously analyzed need connections to this label
    for (CFANode gotoNode : gotoLabelNeeded.get(labelName)) {
      BlankEdge gotoEdge = new BlankEdge("Goto: " + labelName, gotoNode.getLineNumber(), gotoNode, labelNode, true);
      addToCFA(gotoEdge);
    }
    gotoLabelNeeded.removeAll(labelName);
  }

  private void handleGotoStatement (IASTGotoStatement gotoStatement,
      IASTFileLocation fileloc)
  {
    String labelName = gotoStatement.getName ().toString ();

    CFANode prevNode = locStack.pop();
    CFANode labelNode = labelMap.get (labelName);
    if (labelNode != null)
    {
      BlankEdge gotoEdge = new BlankEdge("Goto: "
          + labelName, fileloc.getStartingLineNumber(), prevNode, labelNode, true);

      /* labelNode was analyzed before, so it is in the labelMap,
       * then there can be a jump backwards and this can create a loop.
       * If LabelNode has not been the start of a loop, Node labelNode can be
       * the start of a loop, so check if there is a path from labelNode to
       * the current Node through DFS-search */
      ArrayList<Integer> visitedNodes = new ArrayList<Integer>();
      if (!labelNode.isLoopStart()
          && isPathFromTo(visitedNodes, labelNode, prevNode)) {
        labelNode.setLoopStart();
      }

      addToCFA(gotoEdge);
    } else {
      gotoLabelNeeded.put(labelName, prevNode);
    }

    CFANode nextNode = new CFANode(fileloc.getEndingLineNumber(), currentCFA.getFunctionName());
    currentCFANodes.add(nextNode);
    locStack.push(nextNode);
  }

  /** isPathFromTo() makes a DSF-search from a given Node to search
   * if there is a way to the target Node.
   * the condition for this function is, that every Node has another NodeNumber
   *
   * The code for this function was taken from CFATopologicalSort.java and is modified.
   *
   * @param pVisitedNodes needed for DSF-search
   * @param fromNode starting node for DSF-search
   * @param toNode target node for isPath
   */
  private boolean isPathFromTo(ArrayList<Integer> pVisitedNodes,
      CFANode fromNode, CFANode toNode) {

    // add current node to visited nodes
    pVisitedNodes.add(fromNode.getNodeNumber());
    boolean isPath = false;

    // check if the target is reached
    if (fromNode.getNodeNumber() == toNode.getNodeNumber()) {
      isPath = true;

    } else {
      // BSF-search with the children of current node
      for (int i = 0; i < fromNode.getNumLeavingEdges(); i++) {
        CFANode successor = fromNode.getLeavingEdge(i).getSuccessor();
        if (!pVisitedNodes.contains(successor.getNodeNumber())) {
          isPath = isPathFromTo(pVisitedNodes, successor, toNode);
        }
        // if there is a path, break the search and return isPath (=true)
        if (isPath) {
          break;
        }
      }
    }
    return isPath;
  }

  private void handleReturnStatement (IASTReturnStatement returnStatement, IASTFileLocation fileloc)
  {
    CFANode prevNode = locStack.pop ();
    CFAFunctionExitNode functionExitNode = currentCFA.getExitNode();

    ReturnStatementEdge edge = new ReturnStatementEdge(astCreator.convert(returnStatement), fileloc.getStartingLineNumber(), prevNode, functionExitNode);
    addToCFA(edge);

    CFANode nextNode = new CFANode(fileloc.getEndingLineNumber(), currentCFA.getFunctionName());
    currentCFANodes.add(nextNode);
    locStack.push(nextNode);
  }

  /* (non-Javadoc)
   * @see org.eclipse.cdt.core.dom.ast.ASTVisitor#leave(org.eclipse.cdt.core.dom.ast.IASTStatement)
   */
  @Override
  public int leave (IASTStatement statement)
  {
    if (statement instanceof IASTIfStatement)
    {
      CFANode prevNode = locStack.pop ();
      CFANode nextNode = locStack.peek ();

      if (isReachableNode(prevNode)) {
        BlankEdge blankEdge = new BlankEdge("", prevNode.getLineNumber(), prevNode, nextNode);
        addToCFA(blankEdge);
      }
    }
    else if (statement instanceof IASTCompoundStatement)
    {
      scope.leaveBlock();
      if (statement.getPropertyInParent () == IASTWhileStatement.BODY)
      {
        CFANode prevNode = locStack.pop ();
        CFANode startNode = loopStartStack.pop();

        if (isReachableNode(prevNode)) {
          BlankEdge blankEdge = new BlankEdge("", prevNode.getLineNumber(), prevNode, startNode);
          addToCFA(blankEdge);
        }

        CFANode nextNode = loopNextStack.pop();
        assert nextNode == locStack.peek();
      }
    }
    else if (statement instanceof IASTWhileStatement) // Code never hit due to bug in Eclipse CDT
    {
      /* Commented out, because with CDT 6, the branch above _and_ this branch
       * are hit, which would result in an exception.
      CFANode prevNode = locStack.pop ();

      if (!prevNode.hasJumpEdgeLeaving ())
      {
        CFANode startNode = loopStartStack.peek ();

        if (!prevNode.hasEdgeTo (startNode))
        {
          BlankEdge blankEdge = new BlankEdge ("");
          blankEdge.initialize (prevNode, startNode);
        }
      }

      loopStartStack.pop ();
      loopNextStack.pop ();
      */
    }
    return PROCESS_CONTINUE;
  }

  //Method to handle visiting a parsing problem.  Hopefully none exist
  /* (non-Javadoc)
   * @see org.eclipse.cdt.core.dom.ast.ASTVisitor#visit(org.eclipse.cdt.core.dom.ast.IASTProblem)
   */
  @Override
  public int visit (IASTProblem problem) {
    throw new CFAGenerationRuntimeException(problem.getMessage(), problem);
  }

  /**
   * This method adds this edge to the leaving and entering edges
   * of its predecessor and successor respectively, but it does so only
   * if the edge does not contain dead code
   */
  private void addToCFA(CFAEdge edge) {
    CFACreationUtils.addEdgeToCFA(edge, logger);
  }
}
