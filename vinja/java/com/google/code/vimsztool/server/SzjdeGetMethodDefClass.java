package com.google.code.vimsztool.server;

import static com.google.code.vimsztool.server.SzjdeConstants.PARAM_CLASSPATHXML;
import static com.google.code.vimsztool.server.SzjdeConstants.PARAM_MEMBER_NAME;
import static com.google.code.vimsztool.server.SzjdeConstants.PARAM_EXP_TOKENS;

import java.util.Set;

import com.google.code.vimsztool.compiler.CompilerContext;
import com.google.code.vimsztool.omni.ClassInfo;
import com.google.code.vimsztool.omni.ClassInfoUtil;
import com.google.code.vimsztool.omni.ClassMetaInfoManager;
import com.google.code.vimsztool.omni.JavaExpUtil;
import com.google.code.vimsztool.util.ModifierFilter;


public class SzjdeGetMethodDefClass extends SzjdeCommand {

	@SuppressWarnings("all")
	public String execute() {
		String classPathXml = params.get(PARAM_CLASSPATHXML);
		String[] classNameList = params.get("classnames").split(",");
		String[] tokens = params.get(PARAM_EXP_TOKENS).split(",");
		String memberName = params.get(PARAM_MEMBER_NAME);
		
	    String sourceFile = params.get(SzjdeConstants.PARAM_SOURCEFILE);
		Class aClass = ClassInfoUtil.getExistedClass(classPathXml, classNameList, sourceFile);
		if (aClass == null) return "None";
		ModifierFilter filter = new ModifierFilter(false,true);
		aClass = JavaExpUtil.parseExpResultType(tokens, aClass,filter);
		if (aClass == null) return "None";
		
		String memberType = "field";
		if (memberName.endsWith("()")) {
			memberType = "method";
			memberName = memberName.substring(0,memberName.indexOf("("));
		}
		aClass = JavaExpUtil.searchMemberInHierarchy(aClass, memberName ,memberType ,filter,true);
		if (aClass == null) return "None";
		
		//FIXME: handle inner class here. 
		String className = aClass.getCanonicalName();
		
		String sourceType = params.get(SzjdeConstants.PARAM_SOURCE_TYPE);
		CompilerContext cc = getCompilerContext(classPathXml);

		if (sourceType != null && sourceType.equals("impl")) {
			ClassMetaInfoManager cmm = cc.getClassMetaInfoManager();
			ClassInfo classInfo = cmm.getMetaInfo(className);
			if (classInfo != null) {
				Set<String> subNames = classInfo.getSubNames();
				if (subNames.size() == 1) {
					className = subNames.toArray(new String[]{})[0];
				}
			}
		}

		String sourcePath = cc.findSourceClass(className);
		return sourcePath;
	}
	
	
	

}
