package com.google.code.vimsztool.server;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.antlr.runtime.tree.CommonErrorNode;
import org.antlr.runtime.tree.CommonTree;

import com.google.code.vimsztool.compiler.CompilerContext;
import com.google.code.vimsztool.omni.PackageInfo;
import com.google.code.vimsztool.parser.AstTreeFactory;
import com.google.code.vimsztool.parser.JavaParser;
import com.google.code.vimsztool.parser.ParseResult;

public class SzjdeAutoImportCommand extends SzjdeCommand  {

	@Override
	public String execute() {
		String classPathXml = params.get(SzjdeConstants.PARAM_CLASSPATHXML);
		String currentPkg = params.get(SzjdeConstants.PARAM_PKG_NAME);
		String tmpFilePath = params.get(SzjdeConstants.PARAM_TMP_FILE_PATH);
		
		CompilerContext ctx = getCompilerContext(classPathXml);
		PackageInfo packageInfo =ctx.getPackageInfo();
		Set<String> varNames = new HashSet<String>();
		
		ParseResult result = AstTreeFactory.getJavaSourceAst(tmpFilePath);
		searchImportedTokens(result.getTree(),varNames);
		
		
		StringBuilder sb = new StringBuilder();
		for (String varName : varNames ) {
			varName = varName.trim();
			
			StringBuilder tmpSb = new StringBuilder();
			
			List<String> binClassNames=packageInfo.findPackage(varName);
			if (binClassNames.size() == 0) continue;
			boolean noNeedImport = false;
			for (String binClassName : binClassNames) {
				String pkgName = binClassName.substring(0,binClassName.lastIndexOf("."));
				if ( pkgName.equals("java.lang")  || pkgName.equals(currentPkg)) {
					noNeedImport =true;
					break;
				}
				tmpSb.append(binClassName).append(";");
			}
			if (noNeedImport) continue;
			sb.append(tmpSb.toString()).append("\n");
		}
		
		return sb.toString();
	}
	

	
	public static void searchImportedTokens(CommonTree t, Set<String> names) {
        if ( t != null ) {
        	if (t instanceof CommonErrorNode) {
        		CommonErrorNode tmp = (CommonErrorNode)t;
	    		if ("ABCDEFGHIJKLMNOPQRSTUVWXYZ".indexOf(tmp.start.getText().charAt(0)) > -1 ) {
		        	names.add(tmp.start.getText().trim());
	    		}
        	}
	        if (t.getType() ==  JavaParser.QUALIFIED_TYPE_IDENT) {
	        	names.add(t.getChild(0).getText().trim());
	    	}
	        if (t.getType() == JavaParser.AT) {
	        	names.add(t.getChild(0).getText().trim());
	        }
	        if (t.getType() == JavaParser.THROWS_CLAUSE) {
		        for ( int i = 0; i < t.getChildCount(); i++ ) {
		        	names.add(t.getChild(i).getText().trim());
		        }
	        }
	    	if (t.getType() == JavaParser.DOT) {
	    		if ("ABCDEFGHIJKLMNOPQRSTUVWXYZ".indexOf(t.getChild(0).getText().charAt(0)) > -1 ) {
		        	names.add(t.getChild(0).getText().trim());
	    		}
	    	}
	        for ( int i = 0; i < t.getChildCount(); i++ ) {
	            searchImportedTokens((CommonTree)t.getChild(i), names);
	        }
	    }
	}

}
