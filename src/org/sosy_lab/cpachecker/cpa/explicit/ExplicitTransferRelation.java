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
package org.sosy_lab.cpachecker.cpa.explicit;

import java.math.BigInteger;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.ast.DefaultExpressionVisitor;
import org.sosy_lab.cpachecker.cfa.ast.IASTArraySubscriptExpression;
import org.sosy_lab.cpachecker.cfa.ast.IASTAssignment;
import org.sosy_lab.cpachecker.cfa.ast.IASTBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.IASTCastExpression;
import org.sosy_lab.cpachecker.cfa.ast.IASTCharLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.IASTExpression;
import org.sosy_lab.cpachecker.cfa.ast.IASTExpressionStatement;
import org.sosy_lab.cpachecker.cfa.ast.IASTFieldReference;
import org.sosy_lab.cpachecker.cfa.ast.IASTFloatLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.IASTFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.IASTFunctionCallAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.IASTFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.IASTFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.IASTFunctionTypeSpecifier;
import org.sosy_lab.cpachecker.cfa.ast.IASTIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.IASTInitializer;
import org.sosy_lab.cpachecker.cfa.ast.IASTInitializerExpression;
import org.sosy_lab.cpachecker.cfa.ast.IASTIntegerLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.IASTLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.IASTRightHandSide;
import org.sosy_lab.cpachecker.cfa.ast.IASTStatement;
import org.sosy_lab.cpachecker.cfa.ast.IASTStringLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.IASTUnaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.RightHandSideVisitor;
import org.sosy_lab.cpachecker.cfa.ast.StorageClass;
import org.sosy_lab.cpachecker.cfa.ast.IASTBinaryExpression.BinaryOperator;
import org.sosy_lab.cpachecker.cfa.ast.IASTEnumerationSpecifier.IASTEnumerator;
import org.sosy_lab.cpachecker.cfa.ast.IASTUnaryExpression.UnaryOperator;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFAEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.AssumeEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.CallToReturnEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.DeclarationEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.FunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.FunctionDefinitionNode;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.FunctionReturnEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.ReturnStatementEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.StatementEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractElement;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.cpa.assumptions.storage.AssumptionStorageElement;
import org.sosy_lab.cpachecker.cpa.pointer.Memory;
import org.sosy_lab.cpachecker.cpa.pointer.Pointer;
import org.sosy_lab.cpachecker.cpa.pointer.PointerElement;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCCodeException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCFAEdgeException;
import org.sosy_lab.cpachecker.util.assumptions.NumericTypes;

@Options(prefix="cpa.explicit")
public class ExplicitTransferRelation implements TransferRelation
{
  private final Set<String> globalVariables = new HashSet<String>();
  public static Set<String> globalVarsStatic = null;

  @Option(description="threshold for amount of different values that "
    + "are tracked for one variable in ExplicitCPA (0 means infinitely)")
  private int threshold = 0;

  private String missingInformationLeftVariable = null;
  private String missingInformationLeftPointer  = null;

  private IASTRightHandSide missingInformationRightExpression = null;

  private ExplicitPrecision currentPrecision = null;

  public ExplicitTransferRelation(Configuration config) throws InvalidConfigurationException
  {
    config.inject(this);

    globalVarsStatic = globalVariables;
  }

  @Override
  public Collection<AbstractElement> getAbstractSuccessors(AbstractElement element, Precision pPrecision, CFAEdge cfaEdge)
    throws CPATransferException
  {
    if(!(pPrecision instanceof ExplicitPrecision))
      throw new IllegalArgumentException("precision is no ExplicitPrecision");

    ExplicitPrecision precision = (ExplicitPrecision) pPrecision;

    currentPrecision = precision;

    precision.setLocation(cfaEdge.getSuccessor());

    AbstractElement successor;
    ExplicitElement explicitElement = (ExplicitElement)element;

    // check the type of the edge
    switch(cfaEdge.getEdgeType())
    {
    // if edge is a statement edge, e.g. a = b + c
    case StatementEdge:
      StatementEdge statementEdge = (StatementEdge) cfaEdge;
      successor = handleStatement(explicitElement, statementEdge.getStatement(), cfaEdge, precision);
      break;


    case ReturnStatementEdge:
      ReturnStatementEdge returnEdge = (ReturnStatementEdge)cfaEdge;
      // this statement is a function return, e.g. return (a);
      // note that this is different from return edge
      // this is a statement edge which leads the function to the
      // last node of its CFA, where return edge is from that last node
      // to the return site of the caller function
      successor = handleExitFromFunction(explicitElement, returnEdge.getExpression(), returnEdge);
      break;

    // edge is a declaration edge, e.g. int a;
    case DeclarationEdge:
      DeclarationEdge declarationEdge = (DeclarationEdge) cfaEdge;
      successor = handleDeclaration(explicitElement, declarationEdge, precision);
      break;

    // this is an assumption, e.g. if(a == b)
    case AssumeEdge:
      AssumeEdge assumeEdge = (AssumeEdge) cfaEdge;
      successor = handleAssumption(explicitElement.clone(), assumeEdge.getExpression(), cfaEdge, assumeEdge.getTruthAssumption(), precision);
      break;

    case BlankEdge:
      successor = explicitElement.clone();
      break;

    case FunctionCallEdge:
      FunctionCallEdge functionCallEdge = (FunctionCallEdge) cfaEdge;
      successor = handleFunctionCall(explicitElement, functionCallEdge);
      break;

    // this is a return edge from function, this is different from return statement
    // of the function. See case for statement edge for details
    case FunctionReturnEdge:
      FunctionReturnEdge functionReturnEdge = (FunctionReturnEdge) cfaEdge;
      successor = handleFunctionReturn(explicitElement, functionReturnEdge);
      break;

    default:
      throw new UnrecognizedCFAEdgeException(cfaEdge);
    }

    if(successor == null)
      return Collections.emptySet();

    else
      return Collections.singleton(successor);
  }

  private ExplicitElement handleFunctionCall(ExplicitElement element, FunctionCallEdge callEdge)
    throws UnrecognizedCCodeException
  {
    ExplicitElement newElement = new ExplicitElement(element);

    // copy global variables into the new element, to make them available in body of called function
    // assignConstant() won't do it here, as the current referenceCount of the variable also has to be copied
    for(String globalVar : globalVariables)
    {
      if(element.contains(globalVar))
        newElement.copyConstant(element, globalVar);
    }

    FunctionDefinitionNode functionEntryNode = callEdge.getSuccessor();
    String calledFunctionName = functionEntryNode.getFunctionName();
    String callerFunctionName = callEdge.getPredecessor().getFunctionName();

    List<String> paramNames = functionEntryNode.getFunctionParameterNames();
    List<IASTExpression> arguments = callEdge.getArguments();

    assert(paramNames.size() == arguments.size());

    // visitor for getting the values of the actual parameters in caller function context
    ExpressionValueVisitor visitor = new ExpressionValueVisitor(element, callerFunctionName);

    // get value of actual parameter in caller function context
    for(int i = 0; i < arguments.size(); i++)
    {
      Long value = arguments.get(i).accept(visitor);

      String formalParamName = getScopedVariableName(paramNames.get(i), calledFunctionName);

      if(value == null)
        newElement.forget(formalParamName);

      else
        newElement.assignConstant(formalParamName, value, this.threshold);
    }

    return newElement;
  }

  private ExplicitElement handleExitFromFunction(ExplicitElement element, IASTExpression expression, ReturnStatementEdge returnEdge)
    throws UnrecognizedCCodeException
  {
    if(expression == null)
      expression = NumericTypes.ZERO; // this is the default in C

    String functionName       = returnEdge.getPredecessor().getFunctionName();

    return handleAssignmentToVariable("___cpa_temp_result_var_", expression, new ExpressionValueVisitor(element, functionName));
  }

  /**
   * Handles return from one function to another function.
   * @param element previous abstract element
   * @param functionReturnEdge return edge from a function to its call site
   * @return new abstract element
   */
  private ExplicitElement handleFunctionReturn(ExplicitElement element, FunctionReturnEdge functionReturnEdge)
    throws UnrecognizedCCodeException
  {
    CallToReturnEdge summaryEdge    = functionReturnEdge.getSuccessor().getEnteringSummaryEdge();
    IASTFunctionCall exprOnSummary  = summaryEdge.getExpression();

    ExplicitElement newElement      = element.getPreviousElement().clone();
    String callerFunctionName       = functionReturnEdge.getSuccessor().getFunctionName();
    String calledFunctionName       = functionReturnEdge.getPredecessor().getFunctionName();

    // copy global variables back to the new element, to make them available in body of calling function
    // assignConstant() won't do it here, as the current referenceCount of the variable also has to be copied back
    for(String variableName : globalVariables)
    {
      if(element.contains(variableName))
        newElement.copyConstant(element, variableName);

      else
        newElement.forget(variableName);
    }

    // expression is an assignment operation, e.g. a = g(b);
    if(exprOnSummary instanceof IASTFunctionCallAssignmentStatement)
    {
      IASTFunctionCallAssignmentStatement assignExp = ((IASTFunctionCallAssignmentStatement)exprOnSummary);
      IASTExpression op1 = assignExp.getLeftHandSide();

      // we expect left hand side of the expression to be a variable
      if((op1 instanceof IASTIdExpression) || (op1 instanceof IASTFieldReference))
      {
        String returnVarName = getScopedVariableName("___cpa_temp_result_var_", calledFunctionName);

        String assignedVarName = getScopedVariableName(op1.getRawSignature(), callerFunctionName);

        if(element.contains(returnVarName))
          newElement.assignConstant(assignedVarName, element.getValueFor(returnVarName), this.threshold);

        else
          newElement.forget(assignedVarName);
      }

      // a* = b(); TODO: for now, nothing is done here, but cloning the current element
      else if(op1 instanceof IASTUnaryExpression && ((IASTUnaryExpression)op1).getOperator() == UnaryOperator.STAR)
          return element.clone();

      else
        throw new UnrecognizedCCodeException("on function return", summaryEdge, op1);
    }

    return newElement;
  }

  private AbstractElement handleAssumption(ExplicitElement element, IASTExpression expression, CFAEdge cfaEdge, boolean truthValue, ExplicitPrecision precision)
    throws UnrecognizedCCodeException
  {
    // convert a simple expression like [a] to [a != 0]
    expression = convertToNotEqualToZeroAssume(expression);

    // convert an expression like [a + 753 != 951] to [a != 951 + 753]
    expression = optimizeAssumeForEvaluation(expression);

    String functionName = cfaEdge.getPredecessor().getFunctionName();

    // get the value of the expression (either true[1L], false[0L], or unknown[null])
    ExpressionValueVisitor evalVisitor = new ExpressionValueVisitor(element, functionName);
    Long value = expression.accept(evalVisitor);

    // value is null, try to derive further information
    if(value == null)
    {
      AssigningValueVisitor avv = new AssigningValueVisitor(element, functionName, truthValue);

      expression.accept(avv);

      return element;
    }

    else if((truthValue && value == 1L) || (!truthValue && value == 0L))
      return element;

    else
      return null;
  }

  private ExplicitElement handleDeclaration(ExplicitElement element, DeclarationEdge declarationEdge, ExplicitPrecision precision)
    throws UnrecognizedCCodeException
  {

    ExplicitElement newElement = element.clone();
    if((declarationEdge.getName() == null)
        || (declarationEdge.getStorageClass() == StorageClass.TYPEDEF)
        || (declarationEdge.getDeclSpecifier() instanceof IASTFunctionTypeSpecifier)) {
      // nothing interesting to see here, please move along
      return newElement;
    }

    // get the variable name in the declarator
    String varName = declarationEdge.getName();
    String functionName = declarationEdge.getPredecessor().getFunctionName();

    Long initialValue = null;

    // handle global variables
    if(declarationEdge.isGlobal())
    {
      // if this is a global variable, add to the list of global variables
      globalVariables.add(varName);

      // global variables without initializer are set to 0 in C
      initialValue = 0L;
    }

    // get initial value
    IASTInitializer init = declarationEdge.getInitializer();
    if(init instanceof IASTInitializerExpression)
    {
      IASTRightHandSide exp = ((IASTInitializerExpression)init).getExpression();

      initialValue = getExpressionValue(element, exp, functionName);
    }

    // assign initial value if necessary
    String scopedVarName = getScopedVariableName(varName, functionName);

    if(initialValue != null && precision.isTracking(scopedVarName))
      newElement.assignConstant(scopedVarName, initialValue, this.threshold);

    else
      newElement.forget(scopedVarName);

    return newElement;
  }

  private ExplicitElement handleStatement(ExplicitElement element, IASTStatement expression, CFAEdge cfaEdge, ExplicitPrecision precision)
    throws UnrecognizedCCodeException
  {
    // expression is a binary operation, e.g. a = b;
    if(expression instanceof IASTAssignment)
      return handleAssignment(element, (IASTAssignment)expression, cfaEdge, precision);

    // external function call - do nothing
    else if(expression instanceof IASTFunctionCallStatement)
      return element.clone();

    // there is such a case
    else if(expression instanceof IASTExpressionStatement)
      return element.clone();

    else
      throw new UnrecognizedCCodeException(cfaEdge, expression);
  }

  private ExplicitElement handleAssignment(ExplicitElement element, IASTAssignment assignExpression, CFAEdge cfaEdge, ExplicitPrecision precision)
    throws UnrecognizedCCodeException
  {
    IASTExpression op1    = assignExpression.getLeftHandSide();
    IASTRightHandSide op2 = assignExpression.getRightHandSide();

    if(op1 instanceof IASTIdExpression)
    {
      // a = ...
      if(precision.isOnBlacklist(getScopedVariableName(op1.getRawSignature(),cfaEdge.getPredecessor().getFunctionName())))
        return element;

      else
      {
        String functionName = cfaEdge.getPredecessor().getFunctionName();
        ExpressionValueVisitor v = new ExpressionValueVisitor(element, functionName);

        return handleAssignmentToVariable(op1.getRawSignature(), op2, v);
      }
    }

    else if(op1 instanceof IASTUnaryExpression && ((IASTUnaryExpression)op1).getOperator() == UnaryOperator.STAR)
    {
      // *a = ...

      op1 = ((IASTUnaryExpression)op1).getOperand();

      // Cil produces code like
      // *((int*)__cil_tmp5) = 1;
      // so remove cast
      if(op1 instanceof IASTCastExpression)
        op1 = ((IASTCastExpression)op1).getOperand();

      if(op1 instanceof IASTIdExpression)
      {
        missingInformationLeftPointer = op1.getRawSignature();
        missingInformationRightExpression = op2;
      }

      else
        throw new UnrecognizedCCodeException("left operand of assignment has to be a variable", cfaEdge, op1);

      return element.clone();

    }

    else if(op1 instanceof IASTFieldReference)
    {
      // a->b = ...
      if(precision.isOnBlacklist(getScopedVariableName(op1.getRawSignature(),cfaEdge.getPredecessor().getFunctionName())))
        return element.clone();

      else
      {
        String functionName = cfaEdge.getPredecessor().getFunctionName();
        ExpressionValueVisitor v = new ExpressionValueVisitor(element, functionName);

        return handleAssignmentToVariable(op1.getRawSignature(), op2, v);
      }
    }

    // TODO assignment to array cell
    else if(op1 instanceof IASTArraySubscriptExpression)
      return element.clone();

    else
      throw new UnrecognizedCCodeException("left operand of assignment has to be a variable", cfaEdge, op1);
  }

  private ExplicitElement handleAssignmentToVariable(String lParam, IASTRightHandSide exp, ExpressionValueVisitor v)
    throws UnrecognizedCCodeException
  {
    Long value = exp.accept(v);

    if(v.missingPointer)
    {
      missingInformationRightExpression = exp;
      assert value == null;
    }

    ExplicitElement newElement = v.element.clone();
    String assignedVar = getScopedVariableName(lParam, v.functionName);

    if(value == null)
      newElement.forget(assignedVar);
    else
    {
      if(currentPrecision.isTracking(assignedVar) || assignedVar.endsWith("___cpa_temp_result_var_"))
        newElement.assignConstant(assignedVar, value, this.threshold);
      else
        newElement.forget(assignedVar);
    }

    return newElement;
  }

  /**
   * Visitor that get's the value from an expression.
   * The result may be null, i.e., the value is unknown.
   */
  private class ExpressionValueVisitor extends DefaultExpressionVisitor<Long, UnrecognizedCCodeException>
                                       implements RightHandSideVisitor<Long, UnrecognizedCCodeException>
  {
    protected ExplicitElement element;
    protected String functionName;

    private boolean missingPointer = false;

    public ExpressionValueVisitor(ExplicitElement pElement, String pFunctionName)
    {
      element = pElement;
      functionName = pFunctionName;
    }

    // TODO fields, arrays

    @Override
    protected Long visitDefault(IASTExpression pExp)
    {
      return null;
    }

    @Override
    public Long visit(IASTBinaryExpression pE) throws UnrecognizedCCodeException
    {
      BinaryOperator binaryOperator = pE.getOperator();
      IASTExpression lVarInBinaryExp = pE.getOperand1();
      IASTExpression rVarInBinaryExp = pE.getOperand2();

      switch(binaryOperator)
      {
      case MODULO:
        // TODO check which cases can be handled (I think all)
        //return null;
        throw new UnrecognizedCCodeException("unsupported binary operator", null, pE);

      case PLUS:
      case MINUS:
      case DIVIDE:
      case MULTIPLY:
      case SHIFT_LEFT:
      case BINARY_AND:
      case BINARY_OR:
      case BINARY_XOR:
      {
        Long lVal = lVarInBinaryExp.accept(this);
        if(lVal == null)
          return null;

        Long rVal = rVarInBinaryExp.accept(this);
        if(rVal == null)
          return null;

        switch(binaryOperator)
        {
        case PLUS:
          return lVal + rVal;

        case MINUS:
          return lVal - rVal;

        case DIVIDE:
          // TODO maybe we should signal a division by zero error?
          if(rVal == 0)
            return null;

          return lVal / rVal;

        case MULTIPLY:
          return lVal * rVal;

        case SHIFT_LEFT:
          return lVal << rVal;

        case BINARY_AND:
          return lVal & rVal;

        case BINARY_OR:
          return lVal | rVal;

        case BINARY_XOR:
          return lVal ^ rVal;

        default:
          throw new AssertionError();
        }
      }

      case EQUALS:
      case NOT_EQUALS:
      case GREATER_THAN:
      case GREATER_EQUAL:
      case LESS_THAN:
      case LESS_EQUAL: {

        Long lVal = lVarInBinaryExp.accept(this);
        if(lVal == null)
          return null;

        Long rVal = rVarInBinaryExp.accept(this);
        if(rVal == null)
          return null;

        long l = lVal;
        long r = rVal;

        boolean result;
        switch(binaryOperator)
        {
        case EQUALS:
          result = (l == r);
          break;
        case NOT_EQUALS:
          result = (l != r);
          break;
        case GREATER_THAN:
          result = (l > r);
          break;
        case GREATER_EQUAL:
          result = (l >= r);
          break;
        case LESS_THAN:
          result = (l < r);
          break;
        case LESS_EQUAL:
          result = (l <= r);
          break;

        default:
          throw new AssertionError();
        }

        // return 1 if expression holds, 0 otherwise
        return (result ? 1L : 0L);
      }

      default:
        return null;
      }
    }

    @Override
    public Long visit(IASTCastExpression pE) throws UnrecognizedCCodeException
    {
      return pE.getOperand().accept(this);
    }

    @Override
    public Long visit(IASTFunctionCallExpression pIastFunctionCallExpression) throws UnrecognizedCCodeException
    {
      return null;
    }

    @Override
    public Long visit(IASTCharLiteralExpression pE) throws UnrecognizedCCodeException
    {
      return (long)pE.getCharacter();
    }

    @Override
    public Long visit(IASTFloatLiteralExpression pE) throws UnrecognizedCCodeException
    {
      return null;
    }

    @Override
    public Long visit(IASTIntegerLiteralExpression pE) throws UnrecognizedCCodeException
    {
      return pE.getValue().longValue();
    }

    @Override
    public Long visit(IASTStringLiteralExpression pE) throws UnrecognizedCCodeException
    {
      return null;
    }

    @Override
    public Long visit(IASTIdExpression idExp) throws UnrecognizedCCodeException
    {
      if(idExp.getDeclaration() instanceof IASTEnumerator)
      {
        IASTEnumerator enumerator = (IASTEnumerator)idExp.getDeclaration();
        if(enumerator.hasValue())
          return enumerator.getValue();

        else
          return null;
      }

      String varName = getScopedVariableName(idExp.getName(), functionName);

      if(element.contains(varName))
        return element.getValueFor(varName);

      else
        return null;
    }

    @Override
    public Long visit(IASTUnaryExpression unaryExpression) throws UnrecognizedCCodeException
    {
      UnaryOperator unaryOperator = unaryExpression.getOperator();
      IASTExpression unaryOperand = unaryExpression.getOperand();

      Long value = null;

      switch(unaryOperator)
      {
      case MINUS:
        value = unaryOperand.accept(this);
        return (value != null) ? -value : null;

      case NOT:
        value = unaryOperand.accept(this);

        if(value == null)
          return null;

        // if the value is 0, return 1, if it is anything other than 0, return 0
        else
          return (value == 0L) ? 1L : 0L;

      case AMPER:
        return null; // valid expression, but it's a pointer value

      case STAR:
      {
        missingPointer = true;
        return null;
      }

      default:
        throw new UnrecognizedCCodeException("unknown unary operator", null, unaryExpression);
      }
    }

    @Override
    public Long visit(IASTFieldReference fieldReferenceExpression) throws UnrecognizedCCodeException
    {
      String varName = getScopedVariableName(fieldReferenceExpression.getRawSignature(), functionName);

      if(element.contains(varName))
        return element.getValueFor(varName);

      else
        return null;
    }
  }


  /**
   * Visitor that derives further information from an assume edge
   */
  private class AssigningValueVisitor extends ExpressionValueVisitor
  {
    protected boolean truthValue = false;

    public AssigningValueVisitor(ExplicitElement pElement, String pFunctionName, boolean truthValue)
    {
      super(pElement, pFunctionName);

      this.truthValue = truthValue;
    }

    private IASTExpression unwrap(IASTExpression expression)
    {
      // is this correct for e.g. [!a != !(void*)(int)(!b)] !?!?!
      if(expression instanceof IASTUnaryExpression)
      {
        IASTUnaryExpression exp = (IASTUnaryExpression)expression;
        if(exp.getOperator() == UnaryOperator.NOT)
        {
          expression = exp.getOperand();
          truthValue = !truthValue;

          expression = unwrap(expression);
        }
      }

      if(expression instanceof IASTCastExpression)
      {
        IASTCastExpression exp = (IASTCastExpression)expression;
        expression = exp.getOperand();

        expression = unwrap(expression);
      }

      return expression;
    }

    @Override
    public Long visit(IASTBinaryExpression pE) throws UnrecognizedCCodeException
    {
      BinaryOperator binaryOperator   = pE.getOperator();

      IASTExpression lVarInBinaryExp  = pE.getOperand1();

      lVarInBinaryExp = unwrap(lVarInBinaryExp);

      IASTExpression rVarInBinaryExp  = pE.getOperand2();

      Long leftValue                  = lVarInBinaryExp.accept(this);
      Long rightValue                 = rVarInBinaryExp.accept(this);

      if((binaryOperator == BinaryOperator.EQUALS && truthValue) || (binaryOperator == BinaryOperator.NOT_EQUALS && !truthValue))
      {
        if(leftValue == null &&  rightValue != null && isAssignable(lVarInBinaryExp))
        {
          String leftVariableName = getScopedVariableName(lVarInBinaryExp.getRawSignature(), functionName);
          if(currentPrecision.isTracking(leftVariableName))
          {
            //System.out.println("assigning " + leftVariableName + " value of " + rightValue);
            element.assignConstant(leftVariableName, rightValue, 2000);
          }
        }

        else if(rightValue == null && leftValue != null && isAssignable(rVarInBinaryExp))
        {
          String rightVariableName = getScopedVariableName(rVarInBinaryExp.getRawSignature(), functionName);
          if(currentPrecision.isTracking(rightVariableName))
          {
            //System.out.println("assigning " + rightVariableName + " value of " + leftValue);
            element.assignConstant(rightVariableName, leftValue, 2000);
          }
        }
      }

      return super.visit(pE);
    }

    private boolean isAssignable(IASTExpression expression)
    {
      return expression instanceof IASTIdExpression || expression instanceof IASTFieldReference;
    }
  }

  private class PointerExpressionValueVisitor extends ExpressionValueVisitor
  {
    private final PointerElement pointerElement;

    public PointerExpressionValueVisitor(ExplicitElement pElement, String pFunctionName, PointerElement pPointerElement)
    {
      super(pElement, pFunctionName);
      pointerElement = pPointerElement;
    }

    @Override
    public Long visit(IASTUnaryExpression unaryExpression) throws UnrecognizedCCodeException
    {
      if(unaryExpression.getOperator() != UnaryOperator.STAR)
        return super.visit(unaryExpression);

      // Cil produces code like
      // __cil_tmp8 = *((int *)__cil_tmp7);
      // so remove cast
      IASTExpression unaryOperand = unaryExpression.getOperand();
      if(unaryOperand instanceof IASTCastExpression)
        unaryOperand = ((IASTCastExpression)unaryOperand).getOperand();

      if(unaryOperand instanceof IASTIdExpression)
      {
        String rightVar = derefPointerToVariable(pointerElement, unaryOperand.getRawSignature());
        if(rightVar != null)
        {
          rightVar = getScopedVariableName(rightVar, functionName);

          if(element.contains(rightVar))
            return element.getValueFor(rightVar);
        }
      }

      else
        throw new UnrecognizedCCodeException("Pointer dereference of something that is not a variable", null, unaryExpression);

      return null;
    }
  }

  private Long getExpressionValue(ExplicitElement element, IASTRightHandSide expression, String functionName)
    throws UnrecognizedCCodeException
  {
    return expression.accept(new ExpressionValueVisitor(element, functionName));
  }

  public String getScopedVariableName(String variableName, String functionName)
  {
    if(globalVariables.contains(variableName))
      return variableName;

    return functionName + "::" + variableName;
  }

  @Override
  public Collection<? extends AbstractElement> strengthen(AbstractElement element, List<AbstractElement> elements, CFAEdge cfaEdge, Precision precision)
    throws UnrecognizedCCodeException
  {
    assert element instanceof ExplicitElement;
    ExplicitElement explicitElement = (ExplicitElement)element;

    for(AbstractElement ae : elements)
    {
      if(ae instanceof PointerElement)
        return strengthen(explicitElement, (PointerElement)ae, cfaEdge, precision);

      else if(ae instanceof AssumptionStorageElement)
        return strengthen(explicitElement, (AssumptionStorageElement)ae, cfaEdge, precision);
    }

    return null;
  }

  private Collection<? extends AbstractElement> strengthen(ExplicitElement pExplicitElement, AssumptionStorageElement pAe, CFAEdge pCfaEdge, Precision pPrecision)
  {
    return null;
  }

  private Collection<? extends AbstractElement> strengthen(ExplicitElement explicitElement, PointerElement pointerElement, CFAEdge cfaEdge, Precision precision)
    throws UnrecognizedCCodeException
  {
    try
    {
      if(missingInformationRightExpression != null)
      {
        String functionName = cfaEdge.getPredecessor().getFunctionName();
        ExpressionValueVisitor v = new PointerExpressionValueVisitor(explicitElement, functionName, pointerElement);

        if(missingInformationLeftVariable != null)
        {
          ExplicitElement newElement = handleAssignmentToVariable(missingInformationLeftVariable, missingInformationRightExpression, v);

          return Collections.singleton(newElement);
        }
        else if(missingInformationLeftPointer != null)
        {
          String leftVar = derefPointerToVariable(pointerElement, missingInformationLeftPointer);
          if(leftVar != null)
          {
            leftVar = getScopedVariableName(leftVar, functionName);
            ExplicitElement newElement = handleAssignmentToVariable(leftVar, missingInformationRightExpression, v);

            return Collections.singleton(newElement);
          }
        }
      }
      return null;
    }

    finally
    {
      missingInformationLeftVariable = null;
      missingInformationLeftPointer = null;
      missingInformationRightExpression = null;
    }
  }

  private String derefPointerToVariable(PointerElement pointerElement, String pointer)
  {
    Pointer p = pointerElement.lookupPointer(pointer);
    if (p != null && p.getNumberOfTargets() == 1)
    {
      Memory.PointerTarget target = p.getFirstTarget();
      if(target instanceof Memory.Variable)
        return ((Memory.Variable)target).getVarName();

      else if(target instanceof Memory.StackArrayCell)
        return ((Memory.StackArrayCell)target).getVarName();
    }

    return null;
  }

  /**
   * This method converts a simple expression like [a] to [a != 0], to handle these expression just like the more general one
   *
   * @param expression the expression to generalize
   * @return the generalized expression
   */
  private IASTBinaryExpression convertToNotEqualToZeroAssume(IASTExpression expression)
  {
    if(expression instanceof IASTBinaryExpression)
    {
      IASTBinaryExpression binaryExpression = (IASTBinaryExpression)expression;

      if(binaryExpression.getOperator() == BinaryOperator.EQUALS || binaryExpression.getOperator() == BinaryOperator.NOT_EQUALS)
        return binaryExpression;
    }

    IASTIntegerLiteralExpression zero = new IASTIntegerLiteralExpression("0",
        expression.getFileLocation(),
        expression.getExpressionType(),
        BigInteger.ZERO);

    return new IASTBinaryExpression(expression.getRawSignature() + " " + BinaryOperator.NOT_EQUALS.getOperator() + " " + zero.getRawSignature(),
                                  expression.getFileLocation(),
                                  expression.getExpressionType(),
                                  expression,
                                  zero,
                                  BinaryOperator.NOT_EQUALS);
  }

  /**
   * This method converts an expression like [a + 753 != 951] to [a != 951 + 753], to be able to derive addition information easier with the current expression evaluation visitor.
   *
   * @param expression the expression to generalize
   * @return the generalized expression
   */
  private IASTExpression optimizeAssumeForEvaluation(IASTExpression expression)
  {
    if(expression instanceof IASTBinaryExpression)
    {
      IASTBinaryExpression binaryExpression = (IASTBinaryExpression)expression;

      BinaryOperator operator = binaryExpression.getOperator();
      IASTExpression leftOperand = binaryExpression.getOperand1();
      IASTExpression riteOperand = binaryExpression.getOperand2();

      if(operator == BinaryOperator.EQUALS || operator == BinaryOperator.NOT_EQUALS)
      {
        if(leftOperand instanceof IASTBinaryExpression && riteOperand instanceof IASTLiteralExpression)
        {
          IASTBinaryExpression expr = (IASTBinaryExpression)leftOperand;

          BinaryOperator operation = expr.getOperator();
          IASTExpression leftAddend = expr.getOperand1();
          IASTExpression riteAddend = expr.getOperand2();

          // [(a + 753) != 951] => [a == 951 + 753]
          if(riteAddend instanceof IASTLiteralExpression && (operation == BinaryOperator.PLUS || operation == BinaryOperator.MINUS))
          {
            BinaryOperator newOperation = (operation == BinaryOperator.PLUS) ? BinaryOperator.MINUS : BinaryOperator.PLUS;

            IASTBinaryExpression sum = new IASTBinaryExpression(riteOperand.getRawSignature() + " " + newOperation.getOperator() + " " + riteAddend.getRawSignature(),
                expr.getFileLocation(),
                expr.getExpressionType(),
                riteOperand,
                riteAddend,
                newOperation);

            IASTBinaryExpression assume = new IASTBinaryExpression(leftAddend.getRawSignature() + " " + operator.getOperator() + " " + sum.getRawSignature(),
                  expression.getFileLocation(),
                  expression.getExpressionType(),
                  leftAddend,
                  sum,
                  operator);

            return assume;
          }
        }
      }
    }

    return expression;
  }
}
