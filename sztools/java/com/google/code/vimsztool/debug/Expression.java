package com.google.code.vimsztool.debug;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class Expression {
	
	public static final String EXP_TYPE_STR = "string";
	public static final String EXP_TYPE_NULL = "null";
	public static final String EXP_TYPE_BOOL = "boolean";
	public static final String EXP_TYPE_NUM = "number";
	
	private String expType;
	private String name;
	private boolean isMethod;

	private List<Expression> members = new ArrayList<Expression>();
	private List<Expression> params = new ArrayList<Expression>();
	
	public void addMember(Expression exp) {
		getMembers().add(exp);
	}
	public void addParam(Expression param) {
		getParams().add(param);
	}

	public static Expression parseExpXmlStr(String xml) {
		if (xml==null || xml.equals("")) return null;
		try {
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilder db = dbf.newDocumentBuilder();
			InputStream is = new ByteArrayInputStream(xml.getBytes());
			Document dom = db.parse(is);
			Element docEle = dom.getDocumentElement();
			Element expEle = getFirstChild(docEle, "exp");
			Expression exp = parseExp(expEle);
			return exp;
		} catch (Throwable e) {
			e.printStackTrace();
		}
		return null;
	}
	
	private static Expression parseExp(Element el) {
		
		Expression expObj = new Expression();
		String expType = el.getAttribute("exptype");
		if (expType==null || expType.equals("")) expType = "expression";
		
		expObj.setExpType(expType);
		expObj.setName(el.getAttribute("name"));
		if (! expType.equals("expression")) return expObj;
		
		String method = el.getAttribute("method");
		if (method !=null && method.equals("true")) {
			expObj.setMethod(true);
		}
		
		Element paramsNode = getFirstChild(el, "params");
		if (paramsNode != null ) {
			List<Element> paramNodeList = getChildren(paramsNode,"exp");
			for (int i=0; i<paramNodeList.size(); i++) {
				Element paramNode = (Element)paramNodeList.get(i);
				Expression param = parseExp(paramNode);
				expObj.addParam(param);
			}
		}
		
		Element membersNode = (Element)getFirstChild(el,"members");
		if (membersNode != null) {
			List<Element> memberNodeList = getChildren(membersNode,"exp");
			for (int i=0; i<memberNodeList.size(); i++) {
				Element memberNode = (Element)memberNodeList.get(i);
				Expression member = parseExp(memberNode);
				expObj.addMember(member);
			}
		}
		return expObj;
		
	}
	
	public static Element getFirstChild(Element ele, String name) {
		Element result = null;
		NodeList childNodes = ele.getChildNodes();
		for (int i=0; i<childNodes.getLength(); i++) {
			Node node = childNodes.item(i);
			if (node.getNodeType() != Node.ELEMENT_NODE) continue;
			if (node.getNodeName().equals(name)) {
				result = (Element)node;
				break;
			}
		}
		return result;
	}
	
	public static List<Element> getChildren(Element ele,String childName) {
		List<Element> result = new ArrayList<Element>();
		NodeList childNodes = ele.getChildNodes();
		for (int i=0; i<childNodes.getLength(); i++) {
			Node node = childNodes.item(i);
			if (node.getNodeType() != Node.ELEMENT_NODE) continue;
			if (! node.getNodeName().equals(childName)) continue;
			result.add((Element)node);
		}
		return result;
	}

	public void setExpType(String expType) {
		this.expType = expType;
	}

	public String getExpType() {
		return expType;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}
	public void setMethod(boolean isMethod) {
		this.isMethod = isMethod;
	}
	public boolean isMethod() {
		return isMethod;
	}
	public void setMembers(List<Expression> members) {
		this.members = members;
	}
	public List<Expression> getMembers() {
		return members;
	}
	public void setParams(List<Expression> params) {
		this.params = params;
	}
	public List<Expression> getParams() {
		return params;
	}

}