/*
 * Copyright (c) 1998-2004 Caucho Technology -- all rights reserved
 *
 * This file is part of Resin(R) Open Source
 *
 * Each copy or derived work must preserve the copyright notice and this
 * notice unmodified.
 *
 * Resin Open Source is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * Resin Open Source is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, or any warranty
 * of NON-INFRINGEMENT.  See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Resin Open Source; if not, write to the
 *   Free SoftwareFoundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.jsp.java;

import java.io.*;
import java.util.*;
import java.util.logging.*;
import java.lang.reflect.*;
import java.beans.*;

import javax.servlet.http.*;
import javax.servlet.*;
import javax.servlet.jsp.tagext.*;

import com.caucho.util.*;
import com.caucho.vfs.*;

import com.caucho.log.Log;

import com.caucho.xml.QName;
import com.caucho.xml.XmlChar;

import com.caucho.server.http.*;
import com.caucho.java.*;
import com.caucho.jsp.*;
import com.caucho.jsp.el.*;

import com.caucho.xpath.*;
import com.caucho.el.*;

import com.caucho.jsp.cfg.TldFunction;
import com.caucho.jsp.cfg.TldTag;
import com.caucho.jsp.cfg.TldAttribute;
import com.caucho.jsp.cfg.TldVariable;

/**
 * Generates JSP code.  JavaGenerator, JavaScriptGenerator, and
 * StaticGenerator specialize the JspGenerator for language-specific
 * requirements.
 *
 * <p>JspParser parses the JSP file into an XML-DOM tree.  JspGenerator
 * generates code from that tree.
 */
public class JavaTagGenerator extends JavaJspGenerator {
  static final L10N L = new L10N(JavaTagGenerator.class);
  static final Logger log = Log.open(JavaTagGenerator.class);

  private String _bodyContent = "scriptless";
  private String _dynamicAttributes = null;
  
  private ArrayList<TldAttribute> _attributes = new ArrayList<TldAttribute>();
  private ArrayList<TldVariable> _variables = new ArrayList<TldVariable>();

  public JavaTagGenerator(ParseTagManager tagManager)
  {
    super(tagManager);

    setOmitXmlDeclaration(true);
  }

  public void init()
  {
    super.init();
    
    setOmitXmlDeclaration(true);
  }

  /**
   * Returns true if the XML declaration should be ignored.
   */
  /*
  boolean isOmitXmlDeclaration()
  {
    // tags always omit the declaration
    return true;
  }
  */

  /**
   * Sets the body content.
   */
  public void setBodyContent(String bodyContent)
  {
    _bodyContent = bodyContent;
  }

  /**
   * Gets the body content.
   */
  public String getBodyContent()
  {
    return _bodyContent;
  }

  /**
   * Sets the name of the dynamic attributes map
   */
  public void setDynamicAttributes(String dynamicAttributes)
  {
    _dynamicAttributes = dynamicAttributes;
  }

  /**
   * Gets the body content.
   */
  public String getDynamicAttributes()
  {
    return _dynamicAttributes;
  }

  /**
   * Adds an attribute.
   */
  public void addAttribute(TldAttribute attribute)
  {
    _attributes.add(attribute);
  }

  /**
   * Returns the attributes.
   */
  public ArrayList<TldAttribute> getAttributes()
  {
    return _attributes;
  }

  /**
   * Adds a variable.
   */
  public void addVariable(TldVariable var)
  {
    _variables.add(var);
  }

  /**
   * Returns the variables.
   */
  public ArrayList<TldVariable> getVariables()
  {
    return _variables;
  }

  public boolean isTag()
  {
    return true;
  }

  /**
   * Returns true for XML.
   */
  boolean isXml()
  {
    return _parseState.isXml();
  }

  /**
   * Generates the Java code.
   */
  protected void generate(JspJavaWriter out)
    throws Exception
  {
    out.setLineMap(_lineMap);
    
    generateClassHeader(out);

    generateAttributes(out);

    if (_dynamicAttributes != null)
      generateDynamicAttributes(out);

    generateDoTag(out, _rootNode);

    if (! hasScripting())
      generateStaticDoTag(out, _rootNode);

    generateTagInfo(out);
    
    generateClassFooter(out);
  }

  /**
   * Generates the class header.
   *
   * @param doc the XML document representing the JSP page.
   */
  protected void generateClassHeader(JspJavaWriter out)
    throws IOException, JspParseException
  {
    out.println("/*");
    out.println(" * JSP-Tag generated by " + com.caucho.Version.FULL_VERSION);
    out.println(" */" );
    out.println();

    if (_pkg != null && ! _pkg.equals(""))
      out.println("package " + _pkg + ";");

    out.println("import javax.servlet.*;");
    out.println("import javax.servlet.jsp.*;");
    out.println("import javax.servlet.http.*;");

    fillSingleTaglibImports();

    ArrayList<String> imports = _parseState.getImportList();
    for (int i = 0; i < imports.size(); i++) {
      String name = imports.get(i);
      out.print("import ");
      out.print(name);
      out.println(";");
    }
    _parseState.addImport("javax.servlet.*");
    _parseState.addImport("javax.servlet.jsp.*");
    _parseState.addImport("javax.servlet.http.*");
    _parseState.addImport("java.lang.*");
    out.println();

    out.print("public class ");
    out.print(_className);

    if (hasScripting()) {
      out.print(" extends com.caucho.jsp.java.JspTagSupport");
    }
    else
      out.print(" extends com.caucho.jsp.java.JspTagFileSupport");

    if (_dynamicAttributes != null)
      out.print(" implements javax.servlet.jsp.tagext.DynamicAttributes");

    out.println(" {");
    out.pushDepth();

    out.println("private boolean _caucho_isDead;");

    for (int i = 0; i < _declarations.size(); i++) {
      JspDeclaration decl = _declarations.get(i);

      out.println();
      decl.generateDeclaration(out);
    }
  }

  /**
   * Generates the attribute definitions.
   *
   * @param out the writer to the .java source
   */
  protected void generateAttributes(JspJavaWriter out)
    throws IOException, JspParseException
  {
    for (int i = 0; i < _attributes.size(); i++) {
      TldAttribute attr = _attributes.get(i);

      String name = attr.getName();

      String upperName;
      char ch = name.charAt(0);
      upperName = Character.toUpperCase(ch) + name.substring(1);

      Class cl = attr.getType();
      if (cl == null)
        cl = String.class;
      String type = cl.getName();

      String isSetName = "_jsp_" + name + "_isSet";

      out.println();
      out.println("private " + type + " _" + name + ";");
      out.println("private boolean " + isSetName + ";");
      
      out.println();
      out.println("public void set" + upperName + "(" + type + " value)");
      out.println("{");
      out.pushDepth();
      out.println(isSetName + " = true;");
      out.println("_" + name + " = value;");
      out.popDepth();
      out.println("}");

      /*
      // jsp/101f
      out.println();
      out.println("public " + type + " get" + upperName + "()");
      out.println("{");
      out.pushDepth();
      out.println("return _" + name + ";");
      out.popDepth();
      out.println("}");
      */
    }
  }

  /**
   * Generates the attribute definitions.
   *
   * @param out the writer to the .java source
   */
  protected void generateDynamicAttributes(JspJavaWriter out)
    throws IOException, JspParseException
  {
    String dyn = _dynamicAttributes;

    out.println();
    out.println("java.util.HashMap " + dyn + " = new java.util.HashMap();");
    out.println();
    out.println("public void setDynamicAttribute(String uri, String localName, Object value)");
    out.println("  throws javax.servlet.jsp.JspException");
    out.println("{");
    out.println("  if (uri == null || \"\".equals(uri))");
    out.println("    " + dyn + ".put(localName, value);");
    out.println("}");
  }

  /**
   * Prints the _jspService header
   */
  protected void generateDoTag(JspJavaWriter out, JspNode node)
    throws Exception
  {
    out.println();
    out.println("public void doTag()");
    out.println("  throws javax.servlet.jsp.JspException, java.io.IOException");
    out.println("{");
    out.pushDepth();

    out.println("javax.servlet.jsp.JspContext _jsp_parentContext = getJspContext();");
    out.println("com.caucho.jsp.PageContextWrapper pageContext = com.caucho.jsp.PageContextWrapper.create(_jsp_parentContext);");
    // jsp/1056
    out.println("setJspContext(pageContext);");

    if (hasScripting()) {
      out.println("javax.servlet.http.HttpServletRequest request = (javax.servlet.http.HttpServletRequest) pageContext.getRequest();");
      out.println("javax.servlet.http.HttpServletResponse response = (javax.servlet.http.HttpServletResponse) pageContext.getResponse();");
      out.println("javax.servlet.http.HttpSession session = pageContext.getSession();");
      out.println("javax.servlet.ServletContext application = pageContext.getServletContext();");
    }
    
    out.println("com.caucho.jsp.PageContextWrapper jspContext = pageContext;");
    out.println("javax.servlet.jsp.JspWriter out = pageContext.getOut();");
    generateTagAttributes(out);
    if (hasScripting())
      generatePrologue(out);

    out.println("try {");
    out.pushDepth();
     
    if (hasScripting()) {
      node.generate(out);
      
      generateTagVariablesAtEnd(out);
    } else {
      out.println("doTag(_jsp_parentContext, pageContext, out, null);");
    }
    
    out.popDepth();
    out.println("} catch (Throwable e) {");
    out.println("  if (e instanceof java.io.IOException)");
    out.println("    throw (java.io.IOException) e;");
    out.println("  throw com.caucho.jsp.QJspException.createJspException(e);");
    out.println("}");

    if (hasScripting() && _variables.size() > 0) {
      out.println("finally {");
      out.pushDepth();
      
      generateTagVariablesAtEnd(out);
      
      out.println("setJspContext(_jsp_parentContext);");
      out.println("com.caucho.jsp.PageContextWrapper.free(pageContext);");
      
      out.popDepth();
      out.println("}");
    }
  
    out.popDepth();
    out.println("}");
  }

  /**
   * Generates the attribute definitions.
   *
   * @param out the writer to the .java source
   */
  protected void generateTagAttributes(JspJavaWriter out)
    throws IOException, JspParseException
  {
    for (int i = 0; i < _attributes.size(); i++) {
      TldAttribute attr = _attributes.get(i);

      String name = attr.getName();

      String upperName;
      char ch = name.charAt(0);
      upperName = Character.toUpperCase(ch) + name.substring(1);

      Class cl = attr.getType();
      if (cl == null)
        cl = String.class;
      String type = cl.getName();

      String isSetName = "_jsp_" + name + "_isSet";
      
      out.println("if (" + isSetName + ")");
      out.println("  pageContext.setAttribute(\"" + name + "\", " +
                  JspNode.toELObject("_" + name, cl) + ");");
    }

    // jsp/10a1
    if (_dynamicAttributes != null) {
      out.println("pageContext.setAttribute(\"" + _dynamicAttributes + "\"," +
		  "this._" + _dynamicAttributes + ");");
    }
  }

  /**
   * Prints the _jspService header
   */
  protected void generateStaticDoTag(JspJavaWriter out, JspNode node)
    throws Exception
  {
    out.println();
    out.println("public static void doTag(javax.servlet.jsp.JspContext _jsp_parentContext,");
    out.println("                         com.caucho.jsp.PageContextWrapper pageContext,");
    out.println("                         javax.servlet.jsp.JspWriter out,");
    out.println("                         javax.servlet.jsp.tagext.JspFragment _jspBody)");
    out.println("  throws Throwable");
    out.println("{");
    out.pushDepth();

    generatePrologue(out);

    out.println("try {");
    out.pushDepth();
    
    node.generate(out);

    out.popDepth();
    out.println("} finally {");
    out.pushDepth();
      
    generateTagVariablesAtEnd(out);
      
    out.println("com.caucho.jsp.PageContextWrapper.free(pageContext);");
      
    out.popDepth();
    out.println("}");

    out.popDepth();
    out.println("}");
  }

  /**
   * Generates prologue stuff
   *
   * @param out the writer to the .java source
   */
  protected void generatePrologue(JspJavaWriter out)
    throws Exception
  {
    _rootNode.generatePrologue(out);
    
    for (int i = 0; i < _variables.size(); i++) {
      TldVariable var = _variables.get(i);

      if (var.getNameFromAttribute() != null) {
	out.print("String _jsp_var_from_attribute_" + i + " = (String) ");
	out.println("pageContext.getAttribute(\"" +
		    var.getNameFromAttribute() + "\");");
      }
      
      if ("AT_END".equals(var.getScope()))
	continue;

      String srcName = var.getNameGiven();
      if (srcName == null)
	srcName = var.getAlias();
      
      String dstName;
      if (var.getNameGiven() != null)
	dstName = "\"" + var.getNameGiven() + "\"";
      else
	dstName = "_jsp_var_from_attribute_" + i;

      if ("NESTED".equals(var.getScope())) {
	out.print("Object _jsp_nested_var_" + i + " = ");
	out.println("_jsp_parentContext.getAttribute(" + dstName + ");");
      }
      /*
      else {
	out.print("pageContext.setAttribute(\"" + srcName + "\",");
	out.println("_jsp_parentContext.getAttribute(" + dstName + "));");
      }
      */
    }
  }

  /**
   * Generates the variable setting.
   *
   * @param out the writer to the .java source
   */
  protected void generateTagVariablesAtEnd(JspJavaWriter out)
    throws IOException, JspParseException
  {
    for (int i = 0; i < _variables.size(); i++) {
      TldVariable var = _variables.get(i);
      
      String srcName = var.getNameGiven();
      if (srcName == null)
	srcName = var.getAlias();
      
      String dstName;
      if (var.getNameGiven() != null)
	dstName = "\"" + var.getNameGiven() + "\"";
      else
	dstName = "_jsp_var_from_attribute_" + i;

      if ("NESTED".equals(var.getScope())) {
	out.println("_jsp_parentContext.setAttribute(" + dstName + ", _jsp_nested_var_" + i + ");");
      }
      else {
	out.print("_jsp_parentContext.setAttribute(" + dstName + ",");
	out.println("pageContext.getAttribute(\"" + srcName + "\"));");
      }
    }
  }

  public TagInfo generateTagInfo(String className, TagLibraryInfo taglib)
  {
    init(className);
    
    TldTag tag = new TldTag();
    
    for (int i = 0; i < _attributes.size(); i++) {
      TldAttribute attr = _attributes.get(i);

      tag.addAttribute(attr);
    }
    
    for (int i = 0; i < _variables.size(); i++) {
      TldVariable var = _variables.get(i);

      try {
	tag.addVariable(var);
      } catch (Exception e) {
	log.log(Level.WARNING, e.toString(), e);
      }
    }

    return new TagInfo(tag.getName(),
		       _fullClassName,
		       _bodyContent,
		       "tagfile tag",
		       taglib,
		       null,
		       tag.getAttributes(),
		       tag.getDisplayName(),
		       tag.getSmallIcon(),
		       tag.getLargeIcon(),
		       tag.getVariables(),
		       _dynamicAttributes != null);
  }
  
  /**
   * Generates the attribute definitions.
   *
   * @param out the writer to the .java source
   */
  protected void generateTagInfo(JspJavaWriter out)
    throws IOException, JspParseException
  {
    /*
    out.println();
    out.println("public javax.servlet.jsp.tagext.TagInfo _caucho_getTagInfo()");
    out.println("  throws com.caucho.config.ConfigException");
    out.println("{");
    out.pushDepth();
    out.println("  return _caucho_getTagInfo(_caucho_getTagLibrary());");
    out.popDepth();
    out.println("}");
    */
    
    out.println();
    out.println("public javax.servlet.jsp.tagext.TagInfo _caucho_getTagInfo(javax.servlet.jsp.tagext.TagLibraryInfo taglib)");
    out.println("  throws com.caucho.config.ConfigException");
    out.println("{");
    out.pushDepth();
    out.println("com.caucho.jsp.cfg.TldTag tag = new com.caucho.jsp.cfg.TldTag();");

    out.println("tag.setName(\"test\");");

    out.println("com.caucho.jsp.cfg.TldAttribute attr;");
    for (int i = 0; i < _attributes.size(); i++) {
      TldAttribute attr = _attributes.get(i);

      out.println("attr = new com.caucho.jsp.cfg.TldAttribute();");
      out.println("attr.setName(\"" + attr.getName() + "\");");

      Class type = attr.getType();
      if (type != null)
        out.println("attr.setType(" + type.getName() + ".class);");
      out.println("attr.setRtexprvalue(" + attr.getRtexprvalue() + ");");
      out.println("attr.setRequired(" + attr.getRequired() + ");");

      out.println("tag.addAttribute(attr);");
    }

    out.println("com.caucho.jsp.cfg.TldVariable var;");
    for (int i = 0; i < _variables.size(); i++) {
      TldVariable var = _variables.get(i);

      out.println("var = new com.caucho.jsp.cfg.TldVariable();");

      if (var.getNameGiven() != null)
	out.println("var.setNameGiven(\"" + var.getNameGiven() + "\");");
      
      if (var.getNameFromAttribute() != null)
	out.println("var.setNameFromAttribute(\"" + var.getNameFromAttribute() + "\");");

      String type = var.getVariableClass();
      if (type != null)
        out.println("var.setVariableClass(\"" + type + "\");");
      out.println("var.setDeclare(" + var.getDeclare() + ");");
      if (var.getScope() != null)
	out.println("var.setScope(\"" + var.getScope() + "\");");

      out.println("tag.addVariable(var);");
    }

    out.println("return new com.caucho.jsp.java.TagInfoExt(tag.getName(),");
    out.println("                   getClass().getName(),");
    out.println("                   \"" + _bodyContent + "\",");
    out.println("                   \"A sample tag\",");
    out.println("                   taglib,");
    out.println("                   null,");
    out.println("                   tag.getAttributes(),");
    out.println("                   tag.getDisplayName(),");
    out.println("                   tag.getSmallIcon(),");
    out.println("                   tag.getLargeIcon(),");
    out.println("                   tag.getVariables(),");
    out.println("                   " + (_dynamicAttributes != null) + ",");
    out.println("                   _caucho_depends);");

    out.popDepth();
    out.println("}");
    
    out.println();
    out.println("public String _caucho_getDynamicAttributes()");
    out.println("{");
    out.pushDepth();

    if (_dynamicAttributes != null)
      out.println("return \"" + _dynamicAttributes + "\";");
    else
      out.println("return null;");
    
    out.popDepth();
    out.println("}");

    generateTagLibrary(out);
  }
  
  /**
   * Generates the attribute definitions.
   *
   * @param out the writer to the .java source
   */
  protected void generateTagLibrary(JspJavaWriter out)
    throws IOException, JspParseException
  {
    out.println();
    out.println("private javax.servlet.jsp.tagext.TagLibraryInfo _caucho_getTagLibrary()");
    out.println("  throws com.caucho.config.ConfigException");
    out.println("{");
    out.pushDepth();

    out.println("return new com.caucho.jsp.java.TagTaglib(\"x\", \"http://test.com\");");
    out.popDepth();
    out.println("}");
  }
}
