package com.google.code.vimsztool.omni;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public class ClassInfo {
	
	
	
	@SuppressWarnings("unchecked")
	public String dumpClassInfo(Class aClass) {
		if (aClass == null ) return "";
		
		List<String> superClassNames = new ArrayList<String>();
		List<String> interfaceNames = new ArrayList<String>();
		Class tmpClass = aClass;
		StringBuilder sb=new StringBuilder();
		while (true) {
			if (tmpClass == null) break;
			superClassNames.add(tmpClass.getName());
			Class[] itfs=tmpClass.getInterfaces();
			for (Class itf : itfs ) {
				interfaceNames.add(itf.getName());
			}
			tmpClass=tmpClass.getSuperclass();
		}
		sb.append("Class ").append(aClass.getName()).append("\n\n");
		sb.append("SuperClass \n");
		for (String name : superClassNames) {
			sb.append("    ").append(name).append("\n");
		}
		sb.append("\n");
		sb.append("Interface \n");
		for (String name : interfaceNames) {
			sb.append("    ").append(name).append("\n");
		}
		sb.append("\n");
		sb.append("Constructor \n");
		List<MemberInfo> infos = getConstructorInfo(aClass);
		for (MemberInfo info : infos) {
			sb.append("    ").append(info.getFullDecleration()).append("\n");
		}
		sb.append("\n");
		sb.append("static members \n");
		infos = getMemberInfo(aClass, true);
		for (MemberInfo info : infos) {
			sb.append("    ").append(info.getFullDecleration()).append("\n");
		}
		sb.append("\n");
		sb.append("non-static members \n");
		infos = getMemberInfo(aClass, false);
		for (MemberInfo info : infos) {
			sb.append("    ").append(info.getFullDecleration()).append("\n");
		}
		sb.append("\n");
		return sb.toString();
	}
	
	@SuppressWarnings("unchecked")
	public ArrayList<MemberInfo> getConstructorInfo(Class aClass) {
		ArrayList<MemberInfo> memberInfos=new ArrayList<MemberInfo>();
		Constructor[] constructors = aClass.getDeclaredConstructors();
		for (int i = 0; i < constructors.length; i++) {
			if (Modifier.isPublic(constructors[i].getModifiers())) {
				MemberInfo memberInfo=new MemberInfo();
				memberInfo.setMemberType(MemberInfo.TYPE_CONSTRUCTOR);
				memberInfo.setModifiers(modifiers(constructors[i].getModifiers()));
				memberInfo.setName(constructors[i].getName());
				memberInfo.setExceptions(getExceptionInfo(constructors[i]));
				memberInfo.setParams(getParameterInfo(constructors[i]));
				memberInfos.add(memberInfo);
			}
		}
		return memberInfos;
	}

	@SuppressWarnings("unchecked")
	public ArrayList<MemberInfo> getMemberInfo(Class aClass,boolean staticMember) {
		
		ArrayList<MemberInfo> memberInfos=new ArrayList<MemberInfo>();
		
		Field[] fields = aClass.getDeclaredFields(); // Look up fields.
		for (int i = 0; i < fields.length; i++) {
			if (staticMember && Modifier.isStatic(fields[i].getModifiers())
					|| ( !staticMember && !Modifier.isStatic(fields[i].getModifiers()) ) ) {
				
				if (Modifier.isPublic(fields[i].getModifiers())) {
					MemberInfo memberInfo=new MemberInfo();
					memberInfo.setModifiers(modifiers(fields[i].getModifiers()));
					memberInfo.setMemberType(MemberInfo.TYPE_FIELD);
					memberInfo.setName(fields[i].getName());
					memberInfo.setReturnType(typeName(fields[i].getType()));
					memberInfos.add(memberInfo);
				}
			}
		}

		Method[] methods = aClass.getDeclaredMethods(); // Look up methods.
		for (int i = 0; i < methods.length; i++) {
			if (staticMember && Modifier.isStatic(methods[i].getModifiers())
					|| ( !staticMember && ! Modifier.isStatic(methods[i].getModifiers()) ) ) {
				if (Modifier.isPublic(methods[i].getModifiers())) {
					MemberInfo memberInfo=new MemberInfo();
					memberInfo.setModifiers(modifiers(methods[i].getModifiers()));
					memberInfo.setMemberType(MemberInfo.TYPE_METHOD);
					memberInfo.setName(methods[i].getName());
					memberInfo.setReturnType(typeName(methods[i].getReturnType()));
					memberInfo.setExceptions(getExceptionInfo(methods[i]));
					memberInfo.setParams(getParameterInfo(methods[i]));
					memberInfos.add(memberInfo);
				}
			}
		}
		return memberInfos;

	}

	public static String typeName(Class t) {
		String brackets = "";
		while (t.isArray()) {
			brackets += "[]";
			t = t.getComponentType();
		}
		String name = t.getName();
		int pos = name.lastIndexOf('.');
		if (pos != -1)
			name = name.substring(pos + 1);
		return name + brackets;
	}

	public static String modifiers(int m) {
		if (m == 0)
			return "";
		else
			return Modifier.toString(m) + " ";
	}

	public static String getFieldFullDeclaration(Field f) {
		return "  " + modifiers(f.getModifiers())
				+ typeName(f.getType()) + " " + f.getName() + ";";
	}


	@SuppressWarnings("unchecked")
	public static String getParameterInfo(Member member) {
		Class parameters[] ;
		StringBuilder sb=new StringBuilder();
		if (member instanceof Method) {
			Method m = (Method) member;
			parameters = m.getParameterTypes();
		} else {
			Constructor c = (Constructor) member;
			parameters = c.getParameterTypes();
		}

		for (int i = 0; i < parameters.length; i++) {
			if (i > 0) 
				sb.append(", ");
			sb.append(typeName(parameters[i]));
		}
		return sb.toString();
	}
	
	@SuppressWarnings("unchecked")
	public static String getExceptionInfo(Member member) {
		Class exceptions[];
		StringBuilder sb=new StringBuilder(" ");
		if (member instanceof Method) {
			Method m = (Method) member;
			exceptions = m.getExceptionTypes();
		} else {
			Constructor c = (Constructor) member;
			exceptions = c.getExceptionTypes();
		}
		if (exceptions.length < 1) return "";
		for (int i = 0; i < exceptions.length; i++) {
			if (i > 0)
				sb.append(", ");
			sb.append(typeName(exceptions[i]));
		}
		return sb.toString();
	}

}
