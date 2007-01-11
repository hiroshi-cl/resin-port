/*
 * Copyright (c) 1998-2006 Caucho Technology -- all rights reserved
 *
 * This file is part of Resin(R) Open Source
 *
 * Each copy or derived work must preserve the copyright notice and this
 * notice unmodified.
 *
 * Resin Open Source is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2
 * as published by the Free Software Foundation.
 *
 * Resin Open Source is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, or any warranty
 * of NON-INFRINGEMENT.  See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Resin Open Source; if not, write to the
 *
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.jsf.html;

import java.io.*;
import java.util.*;

import javax.faces.*;
import javax.faces.component.*;
import javax.faces.component.html.*;
import javax.faces.context.*;
import javax.faces.render.*;

/**
 * The HTML text renderer
 */
class HtmlOutputTextRenderer extends HtmlRenderer
{
  public static final Renderer RENDERER = new HtmlOutputTextRenderer();

  /**
   * True if the renderer is responsible for rendering the children.
   */
  @Override
  public boolean getRendersChildren()
  {
    return true;
  }
  /**
   * Renders the open tag for the text.
   */
  @Override
  public void encodeBegin(FacesContext context, UIComponent component)
    throws IOException
  {
    ResponseWriter out = context.getResponseWriter();

    String id = component.getId();
    String dir;
    String lang;
    String style;
    String styleClass;
    String title;
    
    if (component instanceof HtmlOutputText) {
      HtmlOutputText htmlOutput = (HtmlOutputText) component;

      dir = htmlOutput.getDir();
      lang = htmlOutput.getLang();
      style = htmlOutput.getStyle();
      styleClass = htmlOutput.getStyleClass();
      title = htmlOutput.getTitle();
    }
    else {
      Map<String,Object> attrMap = component.getAttributes();
    
      dir = (String) attrMap.get("dir");
      lang = (String) attrMap.get("lang");
      style = (String) attrMap.get("style");
      styleClass = (String) attrMap.get("styleClass");
      title = (String) attrMap.get("title");
    }

    if (dir == null && lang == null
	&& style == null && styleClass == null
	&& (id == null || id.startsWith(UIViewRoot.UNIQUE_ID_PREFIX))) {
      return;
    }

    out.startElement("span", component);

    if (id != null && ! id.startsWith(UIViewRoot.UNIQUE_ID_PREFIX))
      out.writeAttribute("id", component.getClientId(context), "id");

    if (dir != null)
      out.writeAttribute("dir", dir, "dir");

    if (lang != null)
      out.writeAttribute("lang", lang, "dir");

    if (style != null)
      out.writeAttribute("style", style, "style");

    if (styleClass != null)
      out.writeAttribute("class", styleClass, "class");

    if (title != null)
      out.writeAttribute("title", title, "title");
  }

  /**
   * Renders the content for the component.
   */
  @Override
  public void encodeChildren(FacesContext context, UIComponent component)
    throws IOException
  {
    ResponseWriter out = context.getResponseWriter();
    
    if (component instanceof HtmlOutputText) {
      HtmlOutputText htmlOutput = (HtmlOutputText) component;

      Object value = htmlOutput.getValue();

      if (value == null)
	return;
      
      boolean escape = htmlOutput.isEscape();

      String string = String.valueOf(value);

      if (escape)
	escapeText(out, string, "value");
      else
	out.writeText(string, "value");
    }
    else {
      Map<String,Object> attrMap = component.getAttributes();

      Object value = attrMap.get("value");

      if (value == null)
	return;
      
      boolean escape = (Boolean) attrMap.get("escape");
      
      String string = String.valueOf(value);

      if (escape)
	escapeText(out, string, "value");
      else
	out.writeText(string, "value");
    }
  }

  /**
   * Renders the closing tag for the component.
   */
  @Override
  public void encodeEnd(FacesContext context, UIComponent component)
    throws IOException
  {
    ResponseWriter out = context.getResponseWriter();
    
    if (component instanceof HtmlOutputText) {
      HtmlOutputText htmlOutput = (HtmlOutputText) component;

      if (htmlOutput.getStyleClass() != null
	  || htmlOutput.getStyle() != null
	  || htmlOutput.getDir() != null
	  || htmlOutput.getLang() != null) {
	out.endElement("span");
      }
    }
    else {
      Map<String,Object> attrMap = component.getAttributes();

      if (attrMap.get("styleClass") != null
	  || attrMap.get("style") != null
	  || attrMap.get("dir") != null
	  || attrMap.get("lang") != null) {
	out.endElement("span");
      }
    }
  }

  public String toString()
  {
    return "HtmlOutputTextRenderer[]";
  }
}
