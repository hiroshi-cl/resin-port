/*
 * Copyright (c) 1998-2006 Caucho Technology -- all rights reserved
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
 *
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Emil Ong
 */

package com.caucho.jaxb.skeleton;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.namespace.QName;
import javax.xml.stream.events.*;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Iterator;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

import com.caucho.jaxb.BinderImpl;
import com.caucho.jaxb.JAXBUtil;
import com.caucho.util.L10N;

/**
 * a property for serializing/deserializing arrays
 */
public class FloatArrayProperty extends ArrayProperty {
  private static final L10N L = new L10N(FloatArrayProperty.class);

  public static final FloatArrayProperty PROPERTY = new FloatArrayProperty();

  public FloatArrayProperty()
  {
    super(FloatProperty.PRIMITIVE_PROPERTY);
  }

  public Object read(Unmarshaller u, XMLStreamReader in, QName qname)
    throws IOException, XMLStreamException, JAXBException
  {
    if (in.getEventType() != in.START_ELEMENT || ! qname.equals(in.getName()))
      return new float[0]; // avoid ArrayList instantiation

    ArrayList<Float> ret = new ArrayList<Float>();

    while (in.getEventType() == in.START_ELEMENT && qname.equals(in.getName()))
      ret.add((Float) _componentProperty.read(u, in, qname));

    float[] array = new float[ret.size()];

    for (int i = 0; i < ret.size(); i++)
      array[i] = ret.get(i).floatValue();

    return array;
  }

  public Object read(Unmarshaller u, XMLEventReader in, QName qname)
    throws IOException, XMLStreamException, JAXBException
  {
    XMLEvent event = in.peek();

    if (! event.isStartElement() || 
        ! qname.equals(((StartElement) event).getName()))
      return new float[0]; // avoid ArrayList instantiation

    ArrayList<Float> ret = new ArrayList<Float>();

    while (event.isStartElement() &&
           qname.equals(((StartElement) event).getName())) {
      ret.add((Float) _componentProperty.read(u, in, qname));
      event = in.peek();
    }

    float[] array = new float[ret.size()];

    for (int i = 0; i < ret.size(); i++)
      array[i] = ret.get(i).floatValue();

    return array;
  }

  public Object bindFrom(BinderImpl binder, NodeIterator node, QName qname)
    throws JAXBException
  {
    Node child = node.getNode();

    if (child.getNodeType() != Node.ELEMENT_NODE)
      return new float[0];
    else {
      QName nodeName = JAXBUtil.qnameFromNode(child);

      if (! nodeName.equals(qname))
        return new float[0];
    }

    ArrayList<Float> ret = new ArrayList<Float>();

    while (child != null && child.getNodeType() == Node.ELEMENT_NODE) {
      QName nodeName = JAXBUtil.qnameFromNode(child);

      if (! nodeName.equals(qname))
        break;

      ret.add((Float) _componentProperty.bindFrom(binder, node, qname));

      child = node.nextSibling();
    }

    float[] array = new float[ret.size()];

    for (int i = 0; i < ret.size(); i++)
      array[i] = ret.get(i).floatValue();

    return array;
  }

  public void write(Marshaller m, XMLStreamWriter out, Object obj, QName qname)
    throws IOException, XMLStreamException, JAXBException
  {
    //XXX wrapper
    
    if (obj != null) {
      float[] array = (float[]) obj;

      for (int i = 0; i < array.length; i++) 
        FloatProperty.PRIMITIVE_PROPERTY.write(m, out, array[i], qname);
    }
  }

  public void write(Marshaller m, XMLEventWriter out, Object obj, QName qname)
    throws IOException, XMLStreamException, JAXBException
  {
    //XXX wrapper
    
    if (obj != null) {
      float[] array = (float[]) obj;

      for (int i = 0; i < array.length; i++) 
        FloatProperty.PRIMITIVE_PROPERTY.write(m, out, array[i], qname);
    }
  }
  
  public Node bindTo(BinderImpl binder, Node node, Object obj, QName qname)
    throws JAXBException
  {
    QName name = JAXBUtil.qnameFromNode(node);
    Document doc = node.getOwnerDocument(); 

    if (! name.equals(qname))
      node = JAXBUtil.elementFromQName(qname, doc);

    binder.bind(obj, node);

    if (obj != null) {
      float[] array = (float[]) obj;

      for (int i = 0; i < array.length; i++) {
        Node child = JAXBUtil.elementFromQName(qname, doc);
        node.appendChild(FloatProperty.PRIMITIVE_PROPERTY.bindTo(binder,  
                                                                 child, 
                                                                 array[i], 
                                                                 qname));
      }
    }

    return node;
  }
}
