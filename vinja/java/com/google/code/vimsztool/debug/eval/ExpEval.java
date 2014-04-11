package com.google.code.vimsztool.debug.eval;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;

import org.antlr.runtime.tree.CommonTree;

import com.google.code.vimsztool.compiler.CompilerContext;
import com.google.code.vimsztool.debug.Debugger;
import com.google.code.vimsztool.debug.SuspendThreadStack;
import com.google.code.vimsztool.exception.ExpressionEvalException;
import com.google.code.vimsztool.exception.VariableOrFieldNotFoundException;
import com.google.code.vimsztool.parser.AstTreeFactory;
import com.google.code.vimsztool.parser.JavaParser;
import com.google.code.vimsztool.parser.JavaSourceSearcher;
import com.google.code.vimsztool.parser.ParseResult;
import com.google.code.vimsztool.util.IdGenerator;
import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.ArrayType;
import com.sun.jdi.BooleanType;
import com.sun.jdi.BooleanValue;
import com.sun.jdi.ByteType;
import com.sun.jdi.CharType;
import com.sun.jdi.CharValue;
import com.sun.jdi.ClassLoaderReference;
import com.sun.jdi.ClassNotLoadedException;
import com.sun.jdi.ClassObjectReference;
import com.sun.jdi.ClassType;
import com.sun.jdi.DoubleType;
import com.sun.jdi.Field;
import com.sun.jdi.FloatType;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.IntegerType;
import com.sun.jdi.IntegerValue;
import com.sun.jdi.InterfaceType;
import com.sun.jdi.InvalidTypeException;
import com.sun.jdi.InvocationException;
import com.sun.jdi.LocalVariable;
import com.sun.jdi.Location;
import com.sun.jdi.LongType;
import com.sun.jdi.Method;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.PrimitiveType;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.ShortType;
import com.sun.jdi.StackFrame;
import com.sun.jdi.StringReference;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.Type;
import com.sun.jdi.Value;
import com.sun.jdi.VirtualMachine;

public class ExpEval {
	
	public static final String SEP_ROW_TXT="=========================\n";
	
	public static final String VAR_NODE_DIR = "dir";
	public static final String VAR_NODE_LEAF = "leaf";
	public static final int VAR_NODE_VALUE_LEN = 80;
	public static final int MAX_VAR_NODE = 50;
	
	private static String[] primitiveTypeNames = { "boolean", "byte", "char",
		"short", "int", "long", "float", "double" };
	
	private  Debugger debugger;
	private HashMap<String, Value> evalContext = new HashMap<String, Value>();
	
	public ExpEval(Debugger debugger) {
		this.debugger = debugger;
		
	}
	
	public String executeEvalCmd(String debugCmd,String debugCmdArgs) {
		String actionResult = null;
		try {
			if (debugCmd.equals("eval") || debugCmd.equals("print")) {
				String exp = debugCmdArgs.substring(debugCmd.length()+1);
				actionResult =  eval(exp);
			} else if (debugCmd.equals("inspect")) {
				String exp = debugCmdArgs.substring(debugCmd.length()+1);
				actionResult =  inspect(exp,false);
			} else if (debugCmd.equals("sinspect")) {
				String exp = debugCmdArgs.substring(debugCmd.length()+1);
				actionResult =  inspect(exp,true);
			} else if (debugCmd.equals("locals")) {
				actionResult = variables();
			} else if (debugCmd.equals("fields")) {
				actionResult = fields(false);
			} else if (debugCmd.equals("sfields")) {
				actionResult = fields(true);
			} else if (debugCmd.equals("reftype")) {
				String exp = debugCmdArgs.substring(debugCmd.length()+1);
				actionResult = reftype(exp);
			} else if (debugCmd.equals("qeval")) {
				actionResult = quickEval();
			} else if (debugCmd.equals("sizeof")) {
				String exp = debugCmdArgs.substring(debugCmd.length()+1);
				actionResult = sizeOf(exp);
			} else if (debugCmd.equals("geval")) {
				String exp = debugCmdArgs.substring(debugCmd.length()+1);
				actionResult = globExpEval(exp, false);
			} else if (debugCmd.equals("ginspect")) {
				String exp = debugCmdArgs.substring(debugCmd.length()+1);
				actionResult = globExpEval(exp, true);
			} else if (debugCmd.equals("subetree")) {
				String exp = debugCmdArgs.substring(debugCmd.length()+1);
				actionResult = etreeSubNode(exp);
			}
			return actionResult;
		} catch (ExpressionEvalException e) {
			return e.getMessage();
		}
	}
	
	public String sizeOf(String exp) {
		try {
			
			ThreadReference threadRef = checkAndGetCurrentThread();
			SuspendThreadStack threadStack = debugger.getSuspendThreadStack();
			StackFrame stackFrame = threadRef.frame(threadStack.getCurFrame());
			ClassLoaderReference classLoaderRef = stackFrame.location().declaringType().classLoader();
			String qualifiedClass = "com.github.shrekwang.oi.MeasurerMain";
			
			ReferenceType refType = loadClass(threadRef, classLoaderRef,qualifiedClass);
			ClassType clazz = (ClassType)refType;
			
			Method matchedMethod =  refType.methodsByName("measure").get(0);
			
			ParseResult result = AstTreeFactory.getExpressionAst(exp);
			if (result.hasError()) {
				return result.getErrorMsg();
			}
		
			CommonTree node = result.getTreeList().get(0);
			Object arg = evalTreeNode(node);
			
			List<Value> arguments = new ArrayList<Value>();
			arguments.add(getMirrorValue(arg));
				
			Value value = clazz.invokeMethod(threadRef, matchedMethod, arguments, ObjectReference.INVOKE_SINGLE_THREADED);
			return ((StringReference)value).value();
			
			
		} catch (Exception e) {
			e.printStackTrace();
			return "error:" + e.getMessage();
		}
	}
	
	private static Pattern translate(String pat) {
		if (pat == null || pat.trim().equals(""))
			return null;
		StringBuilder sb = new StringBuilder("");
		for (int i = 0; i < pat.length(); i++) {
			char c = pat.charAt(i);
			switch (c) {
			case '*':
				sb.append(".*");
				break;
			case '?':
				sb.append(".");
				break;
			default:
				sb.append(Pattern.quote(String.valueOf(c)));
			}
		}
		return Pattern.compile(sb.toString());
	}
	
	private void addValueToEvalResult(ObjectReference objRef, Pattern pat, Map<String,Value> evalResult, String objName) {
		Value value = null;
		ReferenceType refType = objRef.referenceType();
		List<Field> fields = refType.fields();
		for (Field field: fields) {
			if (pat.matcher(field.name()).matches()) {
				value = objRef.getValue(field);
				evalResult.put(objName + "." + field.name(), value);
			}
		}
	}
	public String globExpEval(String globExp, boolean inspect) {
		String[] exps = globExp.split(",");
		List<VarNode> allNodes = new ArrayList<VarNode>();
		for (int i=0; i<exps.length; i++) {
			String exp = exps[i];
			allNodes.addAll(singleGlobExpEval(exp.trim()));
		}
		String v=XmlGenerate.generate(allNodes);
		return v;
	}
	
	public List<VarNode> singleGlobExpEval(String globExp) {
		
		ThreadReference threadRef = checkAndGetCurrentThread();
		SuspendThreadStack threadStack = debugger.getSuspendThreadStack();
		Value value = null;
		String[] expSecs = globExp.split("\\.");
		Pattern pat = translate(expSecs[0]);
		Pattern pat1 = null;
		if (expSecs.length > 1) {
			pat1 = translate(expSecs[1]);
		}
		Map<String,Value> evalResult = new HashMap<String, Value>();
		try {
			StackFrame stackFrame = threadRef.frame(threadStack.getCurFrame());
			ObjectReference thisObj = stackFrame.thisObject();
			List<LocalVariable> localVariables = stackFrame.visibleVariables();
			for (LocalVariable localVar : localVariables) {
				if (pat.matcher(localVar.name()).matches()) {
					value = stackFrame.getValue(localVar);
					if (pat1 != null) {
						addValueToEvalResult((ObjectReference)value, pat1, evalResult, localVar.name());
					} else {
						evalResult.put(localVar.name(), value);
					}
				}
			}
			
			ReferenceType refType = stackFrame.location().declaringType();
			if (thisObj != null ) {
				refType = thisObj.referenceType();
			}
			List<Field> fields = refType.fields();
			for (Field field: fields) {
				if (pat.matcher(field.name()).matches()) {
					if (thisObj != null) {
						value = thisObj.getValue(field);
					} else {
						value = refType.getValue(field);
					}
					if (pat1 != null) {
						addValueToEvalResult((ObjectReference)value, pat1, evalResult, field.name());
					} else {
						evalResult.put(field.name(), value);
					}
				}
			}
		} catch (Throwable e) {
		}
		
		List<VarNode> varNodes = new ArrayList<VarNode>();
		for (String name : evalResult.keySet()) {
			VarNode varNode = createVarNode(name, evalResult.get(name));
	        varNodes.add(varNode);
		}
		return varNodes;
		
	}
	
	public String quickEval() {
		ThreadReference threadRef = checkAndGetCurrentThread();
		SuspendThreadStack threadStack = debugger.getSuspendThreadStack();
		
		CompilerContext ctx = debugger.getCompilerContext();
		try {
			StackFrame stackFrame = threadRef.frame(threadStack.getCurFrame());
			Location loc = stackFrame.location();
		    String abPath = ctx.findSourceFile(loc.sourcePath());
		    JavaSourceSearcher searcher = JavaSourceSearcher.createSearcher(abPath,ctx);
			
			Set<String> evaluatedExp = new HashSet<String>();
			
			int count = 0;
			int lineOffset = -1 ;
	    	boolean currentLine = false;

			//eval expressions in last three lines at suspend location.
            List<VarNode> allNodes = new ArrayList<VarNode>();

			while (count < 3 && loc.lineNumber()-lineOffset > 1 ) {
				lineOffset++;
		    	currentLine = (lineOffset == 0 ? true:false);
		    	
				Set<String> exps = searcher.searchNearByExps(loc.lineNumber()-lineOffset, currentLine);
				if (exps ==null || exps.size() == 0)  continue; 
				for (String exp : exps) {
					if (exp ==null || exp.trim().equals("")) continue;
					if (evaluatedExp.contains(exp)) continue;
					ParseResult result = AstTreeFactory.getExpressionAst(exp);
					if (result.hasError()) continue; 

                    allNodes.addAll(createVarNodesFromExp(exp));
					evaluatedExp.add(exp);
				}
				count++;
		    }
            String v=XmlGenerate.generate(allNodes);
			return v;
		} catch (Exception e) {
			e.printStackTrace();
			return e.getMessage();
		}
	}
	
	public String setFieldValue(String name, String exp) {
		ThreadReference threadRef = checkAndGetCurrentThread();
		SuspendThreadStack threadStack = debugger.getSuspendThreadStack();
		VirtualMachine vm = debugger.getVm();
		
		try {
			
			ParseResult result = AstTreeFactory.getExpressionAst(exp);
			if (result.hasError()) {
				return result.getErrorMsg();
			}
			Object obj = evalTreeNode(result.getTree());
			Value value = null;
			if (obj instanceof Integer) {
				value = vm.mirrorOf(((Integer)obj).intValue());
			} else if (obj instanceof Boolean) {
				value = vm.mirrorOf(((Boolean)obj).booleanValue());
			} else if (obj instanceof String) {
				value = vm.mirrorOf((String)obj);
			} else {
				value = (Value)obj;
			}
			
			StackFrame stackFrame = threadRef.frame(threadStack.getCurFrame());
			String[] expSecs = name.split("\\.");
				
			LocalVariable localVariable;
			localVariable = stackFrame.visibleVariableByName(expSecs[0]);
			if (localVariable != null) {
				if (expSecs.length == 1) {
					stackFrame.setValue(localVariable, value);
				} else {
					ReferenceType refType = (ReferenceType)localVariable.type();
					Field field = refType.fieldByName(expSecs[1]);
					ObjectReference varValue = (ObjectReference)stackFrame.getValue(localVariable);
					varValue.setValue(field, value);
				}
			} else {
				ObjectReference thisObj = stackFrame.thisObject();
				if (thisObj == null) {
					return "can't find field or variable with name '"+name+"'";
				}
				ReferenceType refType = thisObj.referenceType();
				Field field = refType.fieldByName(expSecs[0]);
				if (expSecs.length == 1) {
					thisObj.setValue(field, value);
				} else {
					ObjectReference secFieldValue = (ObjectReference)thisObj.getValue(field);
					
					refType = (ReferenceType)field.type();
					Field secField = refType.fieldByName(expSecs[1]);
					secFieldValue.setValue(secField, value);
				}
			}
			String valueStr = (value == null ? "null": value.toString()) ;
			return Debugger.CMD_SUCCESS + ": set \""+name + "\" to value " + valueStr;
		} catch (Throwable e) {
			return e.getMessage();
		}

	}

	
	
	public String fields(boolean staticField ) {
		ThreadReference threadRef = checkAndGetCurrentThread();
		SuspendThreadStack threadStack = debugger.getSuspendThreadStack();
		List<VarNode> varNodes = new ArrayList<VarNode>();
		try {
			StackFrame stackFrame = threadRef.frame(threadStack.getCurFrame());
			ObjectReference thisObj = stackFrame.thisObject();
			if (thisObj == null) {
				throw new ExpressionEvalException("eval expression error, there's no 'this' object yet");
			}
			Map<Field, Value> values = thisObj.getValues(thisObj.referenceType().visibleFields());
			List<String> fieldNames = new ArrayList<String>();
			for (Field field : values.keySet()) {
				if (field.isStatic() == staticField) {
					fieldNames.add(field.name());
				}
			}
			int maxLen = getMaxLength(fieldNames)+2;
			for (Field field : values.keySet()) {
				if (field.isStatic() == staticField) {
					String varNodeName = padStr(maxLen,field.name());
					VarNode varNode = createVarNode(varNodeName, values.get(field));
			        varNodes.add(varNode);
				}
			}
		} catch (IncompatibleThreadStateException e) {
		}

		String v=XmlGenerate.generate(varNodes);
		return v;
	}

	
	public String variables() {
		ThreadReference threadRef = checkAndGetCurrentThread();
		SuspendThreadStack threadStack = debugger.getSuspendThreadStack();
		int curFrame = threadStack.getCurFrame();
		List<VarNode> varNodes = new ArrayList<VarNode>();
		try {
			List<String> varNames = new ArrayList<String>();
			for (LocalVariable var : threadRef.frame(curFrame).visibleVariables()) {
				varNames.add(var.name());
			}
			int maxLen = getMaxLength(varNames)+2;
			for (LocalVariable var : threadRef.frame(curFrame).visibleVariables()) {
				Value value = threadRef.frame(curFrame).getValue(var);
				String varNodeName = padStr(maxLen,var.name());
				VarNode varNode = createVarNode(varNodeName, value);
		        varNodes.add(varNode);
			}
		} catch (Exception e) {
			VarNode varNode = new VarNode("locals","dir","error",e.getMessage());
	        varNodes.add(varNode);
		}
		String v=XmlGenerate.generate(varNodes);
		return v;
	}
	
	public String reftype(String exp) {
		ParseResult result = AstTreeFactory.getExpressionAst(exp);
		if (result.hasError()) {
			return result.getErrorMsg();
		}
		Value value = (Value)evalTreeNode(result.getTree());
		return value.type().name();
	}
	
	public String eval(String exp) {
		List<VarNode> varNodes = createVarNodesFromExp(exp);
		String v=XmlGenerate.generate(varNodes);
		return v;
	}
	
	public List<VarNode> createVarNodesFromExp(String exp) {
		ParseResult result = AstTreeFactory.getExpressionAst(exp);
		List<String> exps = result.getExpList();
		int maxLen = getMaxLength(exps)+2;
		
		List<VarNode> varNodes = new ArrayList<VarNode>();
		
		for (int i=0; i< exps.size(); i++) {
			String expOrigStr = exps.get(i);
			String varNodeName = padStr(maxLen,expOrigStr);
			try {
				CommonTree node = result.getTreeList().get(i);
				Value value = (Value)evalTreeNode(node);
				VarNode varNode = createVarNode(varNodeName, value);
				varNodes.add(varNode);
			} catch (ExpressionEvalException e) {
				VarNode varNode = new VarNode(varNodeName,VAR_NODE_LEAF,"error",e.getMessage());
				varNodes.add(varNode);
			}
		}
		return varNodes;
	}
	
	private ReferenceType getCollectionRefType() {
		VirtualMachine vm = debugger.getVm();
	    ReferenceType refType = vm.classesByName("java.util.Collection").get(0);
	    return refType;
	}
	
	private ReferenceType getMapRefType() {
		VirtualMachine vm = debugger.getVm();
	    ReferenceType refType = vm.classesByName("java.util.Map").get(0);
	    return refType;
	}
	
	private boolean isRefType(ReferenceType refType, Value value) {
	    List<Value> arguments = new ArrayList<Value>();
	    arguments.add(value);
	    boolean isInstance =((BooleanValue)invoke(refType.classObject(),"isInstance",arguments)).booleanValue();
	    return isInstance;
	}
	
	
	private List<VarNode> getCollectionNodes(Value value) {
	    
		List<VarNode> varNodes = new ArrayList<VarNode>();
	    List<Value> arguments = new ArrayList<Value>();
	    arguments.add(value);
    	Object iterator = invoke(value, "iterator", new ArrayList<Value>());
    	int i = 0;
    	while (true) {
	    	boolean hasNext =((BooleanValue) invoke(iterator, "hasNext", new ArrayList<Value>())).booleanValue();
	    	if (!hasNext) break;
	    	Value next = invoke(iterator,"next",new ArrayList<Value>());
			VarNode varNode = createVarNode("["+String.valueOf(i)+"]", next);
			varNodes.add(varNode);
			if (i++ >= MAX_VAR_NODE ) break;
    	}
    	return varNodes;
	}
	
	private List<VarNode> getMapNodes(Value value) {
	    List<Value> arguments = new ArrayList<Value>();
	    arguments.add(value);
    	Value entrySet = invoke(value, "entrySet", new ArrayList<Value>());
    	return getCollectionNodes(entrySet);
	}
	
	public String etreeSubNode(String uuid) {
		
		List<VarNode> varNodes = new ArrayList<VarNode>();
		
		Value value = evalContext.get(uuid);
		
		if (value instanceof ArrayReference) {
			try {
				ArrayReference array = (ArrayReference)value;
				int loopCount = array.length() > MAX_VAR_NODE ? MAX_VAR_NODE : array.length();
				for (int i=0; i<loopCount; i++) {
					Value cellValue = array.getValue(i);
					VarNode varNode = createVarNode("["+String.valueOf(i)+"]", cellValue);
					varNodes.add(varNode);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else if (isRefType(getCollectionRefType(),value)) {
			varNodes = getCollectionNodes(value);
		} else if (isRefType(getMapRefType(),value)) {
			varNodes = getMapNodes(value);
		} else if (value instanceof ObjectReference) {
			
			ObjectReference objRef = (ObjectReference) value;
			Map<Field, Value> values = objRef.getValues(objRef.referenceType().visibleFields());
			List<String> fieldNames = new ArrayList<String>();
			for (Field field : values.keySet()) {
				if ( !field.isStatic() ) {
					fieldNames.add(field.name());
				}
			}
			int maxLen = getMaxLength(fieldNames)+2;
			for (Field field : values.keySet()) {
				if ( !field.isStatic() ) {
					String varNodeName = padStr(maxLen,field.name());
					Value fieldValue = values.get(field);
					VarNode varNode = createVarNode(varNodeName, fieldValue);
					varNodes.add(varNode);
				}
			}
		}
		
		String v=XmlGenerate.generate(varNodes);
		return v;
	}
	
	private VarNode createVarNode(String varNodeName, Value value) {
		String varNodeType = VAR_NODE_LEAF;
		if (value instanceof ObjectReference && ! (value instanceof StringReference) ) {
			varNodeType = VAR_NODE_DIR;
		}
		String varJavaType = "";
		if (value != null) {
			varJavaType = value.type().name();
			if (varJavaType.indexOf(".") > -1 ) varJavaType = varJavaType.substring(varJavaType.lastIndexOf(".")+1);
		}
		String repr = getPrettyPrintStr(value);
		repr = repr.replaceAll("\n", "\\\\n");
		/*
		if (repr.length() > VAR_NODE_VALUE_LEN) {
			repr = repr.substring(0, VAR_NODE_VALUE_LEN - 3) + "..." + repr.substring(repr.length() - 3);
		}
		*/
		String uuid = IdGenerator.getUniqueId();
		VarNode varNode = new VarNode(varNodeName,varNodeType,varJavaType,repr);
		varNode.setUuid(uuid);
		putVar(uuid,value);
		return varNode;
	}

	public String inspect(String exp,boolean staticField) {
		ParseResult result = AstTreeFactory.getExpressionAst(exp);
		if (result.hasError()) {
			return result.getErrorMsg();
		}
		Value value = (Value)evalTreeNode(result.getTree());
		
		StringBuilder sb = new StringBuilder();
		if (value instanceof ObjectReference) {
			sb.append(exp).append(" = ");
			sb.append(value.type().name()).append("\n");
			
			ObjectReference objRef = (ObjectReference) value;
			Map<Field, Value> values = objRef.getValues(objRef.referenceType().visibleFields());
			List<String> fieldNames = new ArrayList<String>();
			for (Field field : values.keySet()) {
				if (field.isStatic() == staticField) {
					fieldNames.add(field.name());
				}
			}
			int maxLen = getMaxLength(fieldNames)+2;
			for (Field field : values.keySet()) {
				if (field.isStatic() == staticField) {
					sb.append("    ");
					sb.append(padStr(maxLen,field.name())).append(":");
					sb.append(getPrettyPrintStr(values.get(field)));
					sb.append("\n");
				}
			}
		}
		return sb.toString();
	}
	
	private String getInspectFieldsStr(String exp, ObjectReference value) {
		StringBuilder sb = new StringBuilder();
		sb.append(exp).append(" = ");
		if (value == null) {
			sb.append("null").append("\n");
			return sb.toString();
		}
		sb.append(value.type().name()).append("\n");
		
		ObjectReference objRef = (ObjectReference) value;
		Map<Field, Value> values = objRef.getValues(objRef.referenceType().visibleFields());
		List<String> fieldNames = new ArrayList<String>();
		for (Field field : values.keySet()) {
			if ( !field.isStatic()) {
				fieldNames.add(field.name());
			}
		}
		int maxLen = getMaxLength(fieldNames)+2;
		for (Field field : values.keySet()) {
			if ( !field.isStatic() ) {
				sb.append("    ");
				sb.append(padStr(maxLen,field.name())).append(":");
				sb.append(getPrettyPrintStr(values.get(field)));
				sb.append("\n");
			}
		}
		return sb.toString();
	}
	
	public String eval(List<String> exps) {
		if (exps ==null || exps.size() == 0) return "";
		
		List<VarNode> allNodes = new ArrayList<VarNode>();
		for (String exp : exps) {
			allNodes.addAll(createVarNodesFromExp(exp));
		}
		String v=XmlGenerate.generate(allNodes);
		return v;
	}

	public String evalSimpleValue(String exp) {
		ParseResult result = AstTreeFactory.getExpressionAst(exp);
		if (result.hasError()) {
			return result.getErrorMsg();
		}
		try {
			CommonTree node = result.getTreeList().get(0);
			return evalTreeNodeToStr(node);
		} catch (ExpressionEvalException e) {
			return e.getMessage();
		}
	}
	
	public String evalTreeNodeToStr(CommonTree node) {
		Object value = evalTreeNode(node);
		return getPrettyPrintStr(value);
	}
	
	


	public Value evalTreeNode(CommonTree node) {
		
		CommonTree subNode = null;
		CommonTree leftOp = null;
		CommonTree rightOp = null;
		
		switch (node.getType()) {
		
		case JavaParser.PARENTESIZED_EXPR:
		case JavaParser.EXPR:
			subNode = (CommonTree) node.getChild(0);
			return evalTreeNode(subNode);
			
		case JavaParser.LOGICAL_NOT:
			subNode = (CommonTree) node.getChild(0);
			return getMirrorValue(LogicalNot.operate(this,subNode));
			
		case JavaParser.IDENT:
			return evalJdiVar(node.getText());
		case JavaParser.METHOD_CALL:
			return evalJdiInvoke(node);
		case JavaParser.ARRAY_ELEMENT_ACCESS:
			return evalJdiArray(node);
		case JavaParser.DOT:
			return evalJdiMember(node);
		case JavaParser.CLASS_CONSTRUCTOR_CALL:
			return evalJdiClassConstructorCall(node); 
			
		case JavaParser.PLUS:
		case JavaParser.MINUS:
		case JavaParser.STAR:
		case JavaParser.DIV:
		case JavaParser.MOD:
			
		case JavaParser.EQUAL:
		case JavaParser.NOT_EQUAL:
			
		case JavaParser.GREATER_THAN:
		case JavaParser.GREATER_OR_EQUAL:
		case JavaParser.LESS_THAN:
		case JavaParser.LESS_OR_EQUAL:
			
		case JavaParser.LOGICAL_AND:
		case JavaParser.LOGICAL_OR:
			
		case JavaParser.AND:
		case JavaParser.OR:
			leftOp = (CommonTree) node.getChild(0);
			rightOp = (CommonTree) node.getChild(1);
			return getMirrorValue(evalTreeNode(leftOp,rightOp,node.getType()));
			
		case JavaParser.DECIMAL_LITERAL :
			return getMirrorValue(Integer.valueOf(node.getText()));
		case JavaParser.FLOATING_POINT_LITERAL:
			return getMirrorValue(Float.valueOf(node.getText()));
		case JavaParser.CHARACTER_LITERAL:
			return getMirrorValue(Character.valueOf(node.getText().charAt(1)));
		case JavaParser.STRING_LITERAL:
			String text = node.getText(); 
			return getMirrorValue(text.substring(1, text.length()-1));
		case JavaParser.FALSE :
			return getMirrorValue(Boolean.FALSE);
		case JavaParser.TRUE :
			return getMirrorValue(Boolean.TRUE);
		case JavaParser.NULL :
			return null;
		case JavaParser.THIS:
			return evalThisObject();
			
		default:
			throw new ExpressionEvalException("parse expression error.");
		}
	}
	
	private Value evalThisObject() {
		try {
			ThreadReference threadRef = checkAndGetCurrentThread();
			SuspendThreadStack threadStack = debugger.getSuspendThreadStack();
			StackFrame stackFrame = threadRef.frame(threadStack.getCurFrame());
			ObjectReference thisObj = stackFrame.thisObject();
			return thisObj;
		} catch (Throwable e) {
			throw new ExpressionEvalException("parse expression error. msg:" + e.getMessage());
		}
	}
	
	private Object evalTreeNode(CommonTree leftOp, CommonTree rightOp, int opType) {
		//Object leftValue = evalTreeNode(leftOp);
		//Object rightValue = evalTreeNode(rightOp);
		
		Object result = null;
		switch (opType) {
		
		case JavaParser.PLUS:
			return Plus.operate(this, leftOp, rightOp);
		case JavaParser.MINUS:
			return Minus.operate(this, leftOp, rightOp);
		case JavaParser.STAR:
			return Multi.operate(this, leftOp, rightOp);
		case JavaParser.DIV:
			return Divide.operate(this, leftOp, rightOp);
		case JavaParser.MOD:
			return Mod.operate(this, leftOp, rightOp);
			
		case JavaParser.NOT_EQUAL:
			return NotEqual.operate(this, leftOp, rightOp);
		case JavaParser.EQUAL:
			return Equal.operate(this, leftOp, rightOp);
		case JavaParser.GREATER_THAN:
			return Greater.operate(this, leftOp, rightOp);
		case JavaParser.GREATER_OR_EQUAL:
			return GreaterOrEqual.operate(this, leftOp, rightOp);
		case JavaParser.LESS_THAN:
			return Less.operate(this,leftOp, rightOp);
		case JavaParser.LESS_OR_EQUAL:
			return LessOrEqual.operate(this, leftOp, rightOp);
			
		case JavaParser.LOGICAL_AND:
			return LogicalAnd.operate(this, leftOp, rightOp);
		case JavaParser.LOGICAL_OR:
			return LogicalOr.operate(this, leftOp, rightOp);
		case JavaParser.AND:
			return BitAnd.operate(this, leftOp, rightOp);
		case JavaParser.OR:
			return BitOr.operate(this, leftOp, rightOp);
		}
		return result;
	}
	
	
	public void printTree(CommonTree t, int indent) {
        if ( t != null ) {
            StringBuffer sb = new StringBuffer(indent);
            for ( int i = 0; i < indent; i++ )
                sb = sb.append("   ");
            for ( int i = 0; i < t.getChildCount(); i++ ) {
                System.out.println(sb.toString() + t.getChild(i).toString());
                printTree((CommonTree)t.getChild(i), indent+1);
            }
        }
    }
	
	private Value evalJdiInvoke(CommonTree node) {
		CommonTree firstNode = (CommonTree)node.getChild(0);
		CommonTree argNode = (CommonTree)node.getChild(1);
		int argCount = argNode.getChildCount();
		List<Value> arguments = new ArrayList<Value>();
		
		if (argCount > 0 ) {
			for (int i=0; i<argCount; i++) {
				Object result = evalTreeNode((CommonTree)argNode.getChild(i));
				arguments.add(getMirrorValue(result));
			}
			
		}
		
		if (firstNode.getType() == JavaParser.DOT) {
			Object var = null;
			try {
				var = evalTreeNode((CommonTree)firstNode.getChild(0));
			} catch (VariableOrFieldNotFoundException e) {
				var = getClassType(firstNode.getChild(0).getText());
				
			}
			String methodName = firstNode.getChild(1).getText();
			if (var instanceof ObjectReference || var instanceof ReferenceType) {
				return invoke(var,methodName,arguments);
			}
			if (var instanceof String) {
				VirtualMachine vm = debugger.getVm();
				return invoke(vm.mirrorOf((String)var),methodName,arguments);
			}
			return null;
		} else {
			try {
				String methodName = firstNode.getText();
				
				ThreadReference threadRef = checkAndGetCurrentThread();
				SuspendThreadStack threadStack = debugger.getSuspendThreadStack();
				StackFrame stackFrame = threadRef.frame(threadStack.getCurFrame());
				ReferenceType refType = stackFrame.location().declaringType();
				
				ObjectReference thisObj = (ObjectReference)evalThisObject();
				Object invoker = thisObj;
				if (thisObj == null) {
					invoker = refType;
				}
				List<Method> methods = refType.methodsByName(methodName);
				Method matchedMethod = null;
				
				if (methods != null && methods.size() > 0) {
					if (methods.size() == 1) {
						matchedMethod = methods.get(0);
					} else {
						matchedMethod = findMatchedMethod(methods, arguments);
					}
				}
				
				//if no match method, search to see if it is the static imported method
				if (matchedMethod == null) {
					String javaSourcePath = stackFrame.location().sourcePath();
					String staticImportedClass = findStaticImported(javaSourcePath, methodName);
					if (staticImportedClass != null) {
						invoker = getClassType(staticImportedClass);
					}
				}
				
				return invoke(invoker,methodName,arguments);
			} catch (Exception e) {
				throw new ExpressionEvalException(e.getMessage());
			}
		}
	}
	
	
	private Value getMirrorValue(Object value) {
		if (value == null) return null;
		if (value instanceof Value) return (Value)value;
		
		VirtualMachine vm = debugger.getVm();
		if (value instanceof Integer) {
			return vm.mirrorOf(((Integer)value).intValue());
		} else if (value instanceof Boolean) {
			return vm.mirrorOf(((Boolean)value).booleanValue());
		} else if (value instanceof Float) {
			return vm.mirrorOf(((Float)value).floatValue());
		} else if (value instanceof Byte) {
			return vm.mirrorOf(((Byte)value).byteValue());
		} else if (value instanceof Character) {
			return vm.mirrorOf(((Character)value).charValue());
		} else if (value instanceof Double) {
			return vm.mirrorOf(((Double)value).doubleValue());
		} else if (value instanceof Long) {
			return vm.mirrorOf(((Long)value).longValue());
		} else if (value instanceof Short) {
			return vm.mirrorOf(((Short)value).shortValue());
		} else if (value instanceof String) {
			return vm.mirrorOf(((String)value));
		}
		return null;
	}
	
	private Value evalJdiVar(String name) {
		ThreadReference threadRef = checkAndGetCurrentThread();
		try {
			SuspendThreadStack threadStack = debugger.getSuspendThreadStack();
			StackFrame stackFrame = threadRef.frame(threadStack.getCurFrame());
			ObjectReference thisObj = stackFrame.thisObject();
			Value value = findValueInFrame(threadRef, name, thisObj, true);
			return value;
		} catch (IncompatibleThreadStateException e) {
			throw new ExpressionEvalException("eval expression error, caused by : " + e.getMessage());
		}
	}
	
	private Value evalJdiClassConstructorCall(CommonTree node) {
		CommonTree argNode = (CommonTree)node.getChild(1);
		int argCount = argNode.getChildCount();
		List<Value> arguments = new ArrayList<Value>();
		
		if (argCount > 0 ) {
			for (int i=0; i<argCount; i++) {
				Object result = evalTreeNode((CommonTree)argNode.getChild(i));
				arguments.add(getMirrorValue(result));
			}
			
		}
		
		CommonTree leftNode = (CommonTree)node.getChild(0);
		String className = leftNode.getChild(0).getText();
		ClassType refType = (ClassType)getClassType(className);
		
	    List<Method> methods = refType.methodsByName("<init>");
	    
	    Method matchedMethod = null;
		if (methods != null && methods.size() > 0) {
			if (methods.size() == 1) {
				matchedMethod = methods.get(0);
			} else {
				matchedMethod = findMatchedMethod(methods, arguments);
			}
		}
		SuspendThreadStack threadStack = debugger.getSuspendThreadStack();
		ThreadReference threadRef = threadStack.getCurThreadRef();
		try {
			ObjectReference value =refType.newInstance(threadRef, matchedMethod, arguments, ObjectReference.INVOKE_SINGLE_THREADED);
			return value;
		} catch (Exception e) {
			throw new ExpressionEvalException("call class constructor error, msg is : " + e.getMessage());
		}
	}
	
	private Value evalJdiMember(CommonTree node) {
		
		CommonTree objNode = (CommonTree)node.getChild(0);
		String memberName = node.getChild(1).getText();
		
		ThreadReference threadRef = checkAndGetCurrentThread();
		ObjectReference thisObj = null;
		
		try {
			thisObj = (ObjectReference)evalTreeNode(objNode);
			Value value = findValueInFrame(threadRef, memberName, thisObj, false);
			return value;
		} catch (VariableOrFieldNotFoundException e) {
			ReferenceType refType = getClassType(objNode.getText());
			Field field = refType.fieldByName(memberName);
			Value value = refType.getValue(field);
			return value;
		}
		
	}
	
	
	
	private Value evalJdiArray(CommonTree node) {
		CommonTree arrayNode = (CommonTree)node.getChild(0);
		CommonTree indexExpNode = (CommonTree)node.getChild(1);
		
		ArrayReference array = (ArrayReference)evalTreeNode(arrayNode);
		Object arrayIdxValue = evalTreeNode((CommonTree)indexExpNode.getChild(0));
		if (arrayIdxValue instanceof IntegerValue ) {
			int idx = ((IntegerValue)arrayIdxValue).value();
			return  array.getValue(idx);
		} else if (arrayIdxValue instanceof Integer) {
			int idx = ((Integer)arrayIdxValue).intValue();
			return  array.getValue(idx);
		}  else {
			throw new ExpressionEvalException("eval expression error, array index is not int type.");
		}
	}
	
	private ThreadReference checkAndGetCurrentThread() {
		if (debugger.getVm() == null ) {
			throw new ExpressionEvalException("no virtual machine connected.");
		}
		
		SuspendThreadStack threadStack = debugger.getSuspendThreadStack();
		ThreadReference threadRef = threadStack.getCurThreadRef();
		if (threadRef == null ) {
			throw new ExpressionEvalException("no suspend thread.");
		}
		return threadRef;
	}
	

	public Value findValueInFrame(ThreadReference threadRef, String name,
			ObjectReference thisObj, boolean searchLocalVar)  {
		
		Value value = null;
		try {
			SuspendThreadStack threadStack = debugger.getSuspendThreadStack();
			StackFrame stackFrame = threadRef.frame(threadStack.getCurFrame());
			Value contextVar = evalContext.get(name);
			if (contextVar != null) return contextVar;
			
			if (searchLocalVar) {
				LocalVariable localVariable;
				localVariable = stackFrame.visibleVariableByName(name);
				if (localVariable != null) {
					return stackFrame.getValue(localVariable);
				}
			}
			
			ReferenceType refType = stackFrame.location().declaringType();
			if (thisObj != null ) {
				refType = thisObj.referenceType();
				if ( thisObj instanceof ArrayReference && name.equals("length")) {
					return getMirrorValue(((ArrayReference)thisObj).length());
				}
			}
			Field field = refType.fieldByName(name);
			if (field == null ) {
				String javaSourcePath = stackFrame.location().sourcePath();
				String staticImportedClass = findStaticImported(javaSourcePath, name);
				if (staticImportedClass != null) {
					refType = getClassType(staticImportedClass);
					field = refType.fieldByName(name);
					value = refType.getValue(field);
					return value;
				} 
				throw new VariableOrFieldNotFoundException("eval expression error, field '" + name +"' can't be found."); 
			}
			if (thisObj != null) {
				value = thisObj.getValue(field);
			} else {
				value = refType.getValue(field);
			}
			
		} catch (IncompatibleThreadStateException e) {
			throw new ExpressionEvalException("eval expression error, caused by:" + e.getMessage());
		} catch (AbsentInformationException e) {
			throw new ExpressionEvalException("eval expression error, caused by:" + e.getMessage());
		}
		return value;
	}
	
	private String findStaticImported(String javaSourcePath, String memberName) {
		
		BufferedReader br = null;
		try {
			CompilerContext ctx = debugger.getCompilerContext();
			String abPath = ctx.findSourceFile(javaSourcePath);
			br = new BufferedReader(new FileReader(abPath));
			Pattern pat = Pattern.compile("import\\s+static\\s+(.*)\\."+ memberName + "\\s*;\\s*$");
			String qualifiedClass = null;
			
			while (true) {
				String tmp = br.readLine();
				if (tmp == null) break;
				tmp = tmp.trim();
				Matcher matcher = pat.matcher(tmp);
				if (matcher.matches()) {
					qualifiedClass = matcher.group(1);
					break;
				} 
			}
			br.close();
			return qualifiedClass;
		} catch (Exception e) {
			if (br!= null) try { br.close(); } catch (Exception e1) {}
		}
		return null;
	}
	
	public Value invoke(Object invoker, String methodName, List args) {
		SuspendThreadStack threadStack = debugger.getSuspendThreadStack();
		ThreadReference threadRef = threadStack.getCurThreadRef();
		Value value = null;
		Method matchedMethod = null;
		List<Method> methods = null;
		ClassType refType = null;
		ObjectReference obj  = null;
		if (invoker instanceof ClassType) {
			refType = (ClassType)invoker;
		    methods = refType.methodsByName(methodName);
		} else {
		   obj = (ObjectReference)invoker;
		   methods = obj.referenceType().methodsByName(methodName);
		}
		if (methods == null || methods.size() == 0) {
			throw new ExpressionEvalException("eval expression error, method '" + methodName + "' can't be found");
		}
		if (methods.size() == 1) {
			matchedMethod = methods.get(0);
		} else {
			matchedMethod = findMatchedMethod(methods, args);
		}
		try {
		    if (invoker instanceof ClassType) {
			   ClassType clazz = (ClassType)refType;
			   value = clazz.invokeMethod(threadRef, matchedMethod, args,
					ObjectReference.INVOKE_SINGLE_THREADED);
		    } else {
		    	value = obj.invokeMethod(threadRef, matchedMethod, args,
						ObjectReference.INVOKE_SINGLE_THREADED);
		    }
		} catch (InvalidTypeException e) {
			e.printStackTrace();
			throw new ExpressionEvalException("eval expression error, caused by :" + e.getMessage());
		} catch (ClassNotLoadedException e) {
			e.printStackTrace();
			throw new ExpressionEvalException("eval expression error, caused by :" + e.getMessage());
		} catch (IncompatibleThreadStateException e) {
			e.printStackTrace();
			throw new ExpressionEvalException("eval expression error, caused by :" + e.getMessage());
		} catch (InvocationException e) {
			e.printStackTrace();
			throw new ExpressionEvalException("eval expression error, caused by :" + e.getMessage());
		}
		return value;
	}
	private Method findMatchedMethod(List<Method> methods, List arguments) {
		for (Method method : methods) {
			try {
				List argTypes = method.argumentTypes();
				if (argumentsMatch(argTypes, arguments))
					return method;
			} catch (ClassNotLoadedException e) {
			}
		}
		return null;
	}
	
	private boolean argumentsMatch(List argTypes, List arguments) {
		if (argTypes.size() != arguments.size()) {
			return false;
		}
		Iterator typeIter = argTypes.iterator();
		Iterator valIter = arguments.iterator();
		while (typeIter.hasNext()) {
			Type argType = (Type) typeIter.next();
			Value value = (Value) valIter.next();
			if (value == null) {
				if (isPrimitiveType(argType.name()))
					return false;
			}
			if (!value.type().equals(argType)) {
				if (!isAssignableTo(value.type(), argType)) {
					return false;
				}
			}
		}
		return true;
	}

	private boolean isPrimitiveType(String name) {
		for (String primitiveType : primitiveTypeNames) {
			if (primitiveType.equals(name))
				return true;
		}
		return false;
	}
	private boolean isAssignableTo(Type fromType, Type toType) {

		if (fromType.equals(toType))
			return true;
		if (fromType instanceof BooleanType && toType instanceof BooleanType)
			return true;
		if (toType instanceof BooleanType)
			return false;
		
		if (fromType instanceof BooleanType && toType instanceof BooleanType) return true;
		if (fromType instanceof ByteType && toType instanceof ByteType) return true;
		if (fromType instanceof CharType && toType instanceof CharType) return true;
		if (fromType instanceof IntegerType && toType instanceof IntegerType) return true;
		if (fromType instanceof LongType && toType instanceof LongType) return true;
		if (fromType instanceof ByteType && toType instanceof ByteType) return true;
		if (fromType instanceof FloatType && toType instanceof FloatType) return true;
		if (fromType instanceof DoubleType && toType instanceof DoubleType) return true;
		if (fromType instanceof ShortType && toType instanceof ShortType) return true;

		if (fromType instanceof ByteType && toType instanceof IntegerType) return true;
		if (fromType instanceof CharType && toType instanceof IntegerType) return true;
		
		if (fromType instanceof ArrayType) {
			return isArrayAssignableTo((ArrayType) fromType, toType);
		}
		List interfaces = null;;
		if (fromType instanceof ClassType) {
			ClassType superclazz = ((ClassType) fromType).superclass();
			if ((superclazz != null) && isAssignableTo(superclazz, toType)) {
				return true;
			}
			interfaces = ((ClassType) fromType).interfaces();
		} else if (fromType instanceof InterfaceType) {
			interfaces = ((InterfaceType) fromType).superinterfaces();
		}
		if (interfaces == null ) return false;
		
		Iterator iter = interfaces.iterator();
		while (iter.hasNext()) {
			InterfaceType interfaze = (InterfaceType) iter.next();
			if (isAssignableTo(interfaze, toType)) {
				return true;
			}
		}
		return false;
	}

	boolean isArrayAssignableTo(ArrayType fromType, Type toType) {
		if (toType instanceof ArrayType) {
			try {
				Type toComponentType = ((ArrayType) toType).componentType();
				return isComponentAssignable(fromType.componentType(),
						toComponentType);
			} catch (ClassNotLoadedException e) {
				return false;
			}
		}
		if (toType instanceof InterfaceType) {
			return toType.name().equals("java.lang.Cloneable");
		}
		return toType.name().equals("java.lang.Object");
	}

	private boolean isComponentAssignable(Type fromType, Type toType) {
		if (fromType instanceof PrimitiveType) {
			return fromType.equals(toType);
		}
		if (toType instanceof PrimitiveType) {
			return false;
		}
		return isAssignableTo(fromType, toType);
	}
	
	
	private int getMaxLength(List<String> names) {
		int max = 0;
		if (names == null || names.size() ==0) return 0;
		for (String name : names ) {
			int len = name.length();
			if (len > max) max = len;
		}
		return max;
	}
	private String padStr(int maxLen, String origStr) {
		if (origStr  == null) return "";
		int len = origStr.length();
		if (len >= maxLen ) return origStr;
		for (int i=0; i< (maxLen-len); i++) {
			origStr = origStr + " ";
		}
		return origStr;
	}
	
	public String getPrettyPrintStr(Object var) {
		if (var == null)
			return "null";
		if (var instanceof ArrayReference) {
			StringBuilder sb = new StringBuilder("[");
			ArrayReference arrayObj = (ArrayReference) var;
			if (arrayObj.length() == 0)
				return "[]";
			List<Value> values = arrayObj.getValues();
			for (Value value : values) {
				sb.append(getPrettyPrintStr(value)).append(",");
			}
			sb.deleteCharAt(sb.length() - 1);
			sb.append("]");
			return sb.toString();
		} else if (var instanceof StringReference) {
			return "\"" + ((StringReference)var).value() + "\"";
		} else if (var instanceof CharValue) {
			return "'" + ((CharValue)var).value() + "'";
			
		} else if (var instanceof ObjectReference) {
			Value strValue = invoke((ObjectReference) var, "toString", new ArrayList());
			if (strValue == null) return "";
			String v = strValue.toString();
			if (v.startsWith("\"") &&  v.endsWith("\"")) {
				v = v.substring(1, v.length()-1);
			}
			return v;
		} else if (var instanceof String) {
			return "\"" + (String)var + "\"";
			
		} else {
			return var.toString();
		}
	}

	
    private ReferenceType getClassType(String className) {
		
		try {
			ThreadReference threadRef = checkAndGetCurrentThread();
			SuspendThreadStack threadStack = debugger.getSuspendThreadStack();
			StackFrame stackFrame = threadRef.frame(threadStack.getCurFrame());
			CompilerContext ctx = debugger.getCompilerContext();
			VirtualMachine vm = debugger.getVm();
			List<ReferenceType> refTypes = vm.classesByName("java.lang."+className);
			if (refTypes !=null && refTypes.size() >0 ) {
				return refTypes.get(0);
			}
			String locSourcePath = stackFrame.location().sourcePath();
			String abPath = ctx.findSourceFile(locSourcePath);
			List<String> lines = getResourceLines(abPath);
			Pattern pat = Pattern.compile("import\\s+(.*\\."+className+")\\s*;\\s*$");
			String qualifiedClass = null;
			
			int pkgIndex = locSourcePath.lastIndexOf(File.separator);
			String packageName = "";
			if (pkgIndex != -1 ){
				packageName = locSourcePath.substring(0,pkgIndex).replace(File.separator, ".");
			}
			
			for (String tmp : lines) {
				tmp = tmp.trim();
				Matcher matcher = pat.matcher(tmp);
				if (matcher.matches()) {
					qualifiedClass = matcher.group(1);
					break;
				} 
			}
			if (qualifiedClass == null ){
				if (packageName.equals("")) {
					qualifiedClass = className;
				} else {
					qualifiedClass = packageName + "." + className;
				}
			}
			
			refTypes = vm.classesByName(qualifiedClass);
			if (refTypes !=null && refTypes.size() >0 ) {
				return refTypes.get(0);
			}
			ClassLoaderReference classLoaderRef = stackFrame.location().declaringType().classLoader();
			return loadClass(threadRef, classLoaderRef,qualifiedClass);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
    
    private List<String> getResourceLines(String filename) {
    	List<String> result = new ArrayList<String>();
		BufferedReader br = null;
    	if (filename.startsWith("jar:")) {
			JarFile jarFile = null;
			try {
				String jarPath = filename.substring(6,filename.lastIndexOf("!"));
				jarFile = new JarFile(jarPath);
				String entryName = filename .substring(filename.lastIndexOf("!") + 1);
				entryName = entryName.replace("\\", "/");
				ZipEntry zipEntry = jarFile.getEntry(entryName);
				InputStream is = jarFile.getInputStream(zipEntry);
				br = new BufferedReader(new InputStreamReader(is));
				while (true) {
					String tmp = br.readLine();
					if (tmp == null) break;
					result.add(tmp);
				}
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				if (br != null) try {br.close(); } catch (Exception e) {}
				if (jarFile != null) try {jarFile.close(); } catch (Exception e) {}
			}
		} else {
			try {
				br = new BufferedReader(new FileReader(filename));
				while (true) {
					String tmp = br.readLine();
					if (tmp == null) break;
					result.add(tmp);
				}
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				if (br != null) try {br.close(); } catch (Exception e) {}
			} 
		}
		return result;
    }
    
    private ReferenceType loadClass(ThreadReference threadRef,
    		ClassLoaderReference classLoaderRef, String className) {
    	
    	//classLoaderRef = (ClassLoaderReference)invoke( (ObjectReference) classLoaderRef,"getSystemClassLoader" , new ArrayList() );
    	
    	ReferenceType refType= classLoaderRef.referenceType();
		List<Method> aa = refType.methodsByName("loadClass");
		Method matchedMethod = null;
		for (Method method : aa) {
			if (method.argumentTypeNames().size() == 2 ) {
				matchedMethod = method;
			}
		}
		VirtualMachine vm = refType.virtualMachine();
		Value value = vm.mirrorOf(className);
		List<Value> args = new ArrayList<Value>();
		args.add(value);
		args.add(vm.mirrorOf(true));
		ClassObjectReference v = null;
		try {
			v =(ClassObjectReference)classLoaderRef.invokeMethod(threadRef, matchedMethod, args, ObjectReference.INVOKE_SINGLE_THREADED);
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (v == null) return null;
		
		try {
	    	invoke( (ObjectReference)v,"newInstance" , new ArrayList() ); //force to initialize the static field, don't otherwise can do it.
		} catch (Exception e) {
		}
		ReferenceType v2 = v.reflectedType();
		return v2;
    }
    
    public void putVar(String name, Value value) {
    	evalContext.put(name, value);
    }
    public void removeVar(String name) {
    	evalContext.remove(name);
    }
	
}
