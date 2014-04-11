
package com.google.code.vimsztool.compiler;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.internal.compiler.ClassFile;
import org.eclipse.jdt.internal.compiler.CompilationResult;
import org.eclipse.jdt.internal.compiler.ICompilerRequestor;

import com.google.code.vimsztool.util.Preference;

public class CompilerRequestor implements ICompilerRequestor {
	
	 private CompilerContext ctx;
	 private CompileResultInfo compileResult;
	 private Preference pref = Preference.getInstance();
	 private boolean ignoreWarning ;
	 private PrintWriter out = null;
	 
	 private static final String FIELD_SEPERATOR="::";
	 
	 public CompilerRequestor(CompilerContext ctx,  CompileResultInfo compileResult, PrintWriter resultOutput) {
		 this.ctx = ctx;
		 this.compileResult = compileResult;
		 String ignoreStr = pref.getValue(Preference.JDE_COMPILE_IGNORE_WARING);
		 this.ignoreWarning = ignoreStr.equals("true") ? true : false;
		 this.out = resultOutput;
	 }
	
    public void acceptResult(CompilationResult result) {
        try {
        	List<IProblem> errorList = new ArrayList<IProblem>();
            if (result.hasProblems()) {
                IProblem[] problems = result.getProblems();
                for (int i = 0; i < problems.length; i++) {
                	StringBuilder sb = new StringBuilder();
                    IProblem problem = problems[i];
                    if (ignoreWarning && problem.isWarning()) {
                    	continue;
                    }
                    if (problem.isError()) {
                    	sb.append("E").append(FIELD_SEPERATOR);
                    	compileResult.incErrorCount();
                    } else {
                    	sb.append("W").append(FIELD_SEPERATOR);
                    	compileResult.incWarningCount();
                    }
                    String filename=String.valueOf(problem.getOriginatingFileName());
                    sb.append(filename).append(FIELD_SEPERATOR);
                    sb.append(problem.getSourceLineNumber()).append(FIELD_SEPERATOR);
                    sb.append(problem.getMessage()).append(FIELD_SEPERATOR);
                    sb.append(problem.getSourceStart()).append(FIELD_SEPERATOR);
                    sb.append(problem.getSourceEnd()).append("\n");
                    compileResult.addProblemInfo(sb.toString());
                    if (out != null && problem.isError()) {
	                    String errorType = problem.isError()? "Error" : "Warning";
                    	out.println("compile " + new String(problem.getOriginatingFileName()) + " " + errorType);
                    	String line = ((CompilationUnit)result.getCompilationUnit()).getLine(problem.getSourceLineNumber());
                    	out.println(problem.getSourceLineNumber()+" : " + line);
                    	out.println(problem.getMessage());
                    	out.println("");
                    }
                    
                    if (problem.isError()) {
                    	errorList.add(problem);
                    }
                }
            } else {
                 StringBuilder sb = new StringBuilder();
            	 CompilationUnit unit = (CompilationUnit)result.getCompilationUnit();
            	 String filename=new String(unit.getFileName());
                 sb.append("N").append(FIELD_SEPERATOR);
                 sb.append(filename).append(FIELD_SEPERATOR);
                 sb.append("").append(FIELD_SEPERATOR);
                 sb.append("").append(FIELD_SEPERATOR);
                 sb.append("").append(FIELD_SEPERATOR);
                 sb.append("").append("\n");
                 compileResult.addProblemInfo(sb.toString());
            }
        	compileResult.setError(true);
            if (errorList.isEmpty()) {
            	compileResult.setError(false);
                ClassFile[] classFiles = result.getClassFiles();
                List<String> classNames = new ArrayList<String>();
                for (int i = 0; i < classFiles.length; i++) {
                    ClassFile classFile = classFiles[i];
                    char[][] compoundName = 
                        classFile.getCompoundName();
                    String className = "";
                    String sep = "";
                    for (int j = 0;  j < compoundName.length; j++) {
                        className += sep;
                        className += new String(compoundName[j]);
                        sep = ".";
                    }
                    classNames.add(className);
                    byte[] bytes = classFile.getBytes();
                    String srcFile = String.valueOf(result.getFileName());
                    
                    String outFile = ctx.findProperOutputDir(srcFile) + "/" + className.replace('.', '/') + ".class";
                    
                    File parentFile=new File(outFile).getParentFile();
                    if (!parentFile.exists()) parentFile.mkdirs();
                    FileOutputStream fout = new FileOutputStream(outFile);
                    BufferedOutputStream bos = new BufferedOutputStream(fout);
                    bos.write(bytes);
                    bos.close();
                    
                    compileResult.addOutputInfo(className, outFile);
                }
	            ctx.refreshClassInfo(classNames);
            }
        } catch (IOException exc) {
        	exc.printStackTrace();
        }
    }
}


