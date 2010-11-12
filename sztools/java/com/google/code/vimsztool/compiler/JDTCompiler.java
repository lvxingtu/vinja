
package com.google.code.vimsztool.compiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.eclipse.jdt.internal.compiler.Compiler;
import org.eclipse.jdt.internal.compiler.DefaultErrorHandlingPolicies;
import org.eclipse.jdt.internal.compiler.ICompilerRequestor;
import org.eclipse.jdt.internal.compiler.IErrorHandlingPolicy;
import org.eclipse.jdt.internal.compiler.IProblemFactory;
import org.eclipse.jdt.internal.compiler.env.ICompilationUnit;
import org.eclipse.jdt.internal.compiler.env.INameEnvironment;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.compiler.problem.DefaultProblemFactory;

import com.google.code.vimsztool.omni.PackageInfo;

/**
 * JDT class compiler. This compiler will load source dependencies from the
 * context classloader, reducing dramatically disk access during 
 * the compilation process.
 * 
 */
public class JDTCompiler  {
	private CompilerContext ctx;
	  
	  
	public JDTCompiler(CompilerContext ctx) {
		this.ctx=ctx;
	}
	
	public static void main(String[] args) throws Exception {
		CompilerContext cc=new CompilerContext("test/classPath.xml");
		JDTCompiler compiler=new JDTCompiler(cc);
		compiler.generateClass("test/src/Test.java");
	}
	
    
    /** 
     * Compile the servlet from .java file to .class file
     */
    @SuppressWarnings({ "unchecked", "deprecation" })
	public List<String> generateClass( final String sourceFile ) {
        
    	String targetClassName=ctx.buildClassName(sourceFile);
        String[] fileNames = new String[] {sourceFile};
        String[] classNames = new String[] {targetClassName};
        
        final INameEnvironment env = new NameEnvironment(sourceFile,targetClassName,ctx) ;

        final IErrorHandlingPolicy policy = 
            DefaultErrorHandlingPolicies.proceedWithAllProblems();

        final Map settings = new HashMap();
        settings.put(CompilerOptions.OPTION_LineNumberAttribute,
                     CompilerOptions.GENERATE);
        settings.put(CompilerOptions.OPTION_SourceFileAttribute,
                     CompilerOptions.GENERATE);
        settings.put(CompilerOptions.OPTION_ReportDeprecation,
                     CompilerOptions.IGNORE);
        if (ctx.getEncoding() != null) {
            settings.put(CompilerOptions.OPTION_Encoding, ctx.getEncoding());
        }
       

        // Source JVM
        if(ctx.getEncoding() != null ) {
            settings.put(CompilerOptions.OPTION_Source, ctx.getSrcVM());
        } else {
            // Default to 1.5
            settings.put(CompilerOptions.OPTION_Source, CompilerOptions.VERSION_1_5);
        }
        
        // Target JVM
        if(ctx.getDstVM() != null ) {
            settings.put(CompilerOptions.OPTION_TargetPlatform, ctx.getDstVM());
        } else {
            // Default to 1.5
            settings.put(CompilerOptions.OPTION_TargetPlatform, CompilerOptions.VERSION_1_5);
            settings.put(CompilerOptions.OPTION_Compliance, CompilerOptions.VERSION_1_5);
        }

        final IProblemFactory problemFactory = 
            new DefaultProblemFactory(Locale.getDefault());
        
        List<String> problemList = new ArrayList<String>();
        final ICompilerRequestor requestor = new CompilerRequestor(ctx.getOutputDir(),problemList);

        ICompilationUnit[] compilationUnits = 
            new ICompilationUnit[classNames.length];
        for (int i = 0; i < compilationUnits.length; i++) {
            String className = classNames[i];
            compilationUnits[i] = new CompilationUnit(fileNames[i], className,ctx.getEncoding());
        }
        Compiler compiler = new Compiler(env, policy, settings, requestor, problemFactory, true);
        compiler.compile(compilationUnits);
        
        PackageInfo.cacheClassNameInDist(ctx.getOutputDir());
        
        return problemList;
        
    }
    
    
}
