package com.google.code.vimsztool.server;

import java.util.Set;

import com.google.code.vimsztool.compiler.CompilerContext;
import com.google.code.vimsztool.omni.ClassInfo;
import com.google.code.vimsztool.omni.ClassMetaInfoManager;

public class SzjdeLocateSourceCommand extends SzjdeCommand {

	public String execute() {
		String classPathXml = params.get(SzjdeConstants.PARAM_CLASSPATHXML);
		String className = params.get(SzjdeConstants.PARAM_CLASS_NAME);
		String sourceType = params.get(SzjdeConstants.PARAM_SOURCE_TYPE);
		CompilerContext cc = getCompilerContext(classPathXml);
		ClassMetaInfoManager cmm = cc.getClassMetaInfoManager();

		if (sourceType != null && sourceType.equals("impl")) {
			ClassInfo classInfo = cmm.getMetaInfo(className);
			if (classInfo != null) {
				Set<String> subNames = classInfo.getSubNames();
				if (subNames.size() == 1) {
					className = subNames.toArray(new String[]{})[0];
				}
			}
		}
		String sourcePath = cc.findSourceClass(className);
		if (sourcePath.equals("None")) return sourcePath;
		
		StringBuilder sb = new StringBuilder();
		sb.append(sourcePath).append("\n");
		if (className.indexOf(".") > -1) {
			sb.append(className.substring(className.lastIndexOf(".")+1));
		}  else {
			sb.append(className);
		}
		return sb.toString();
	}

}
