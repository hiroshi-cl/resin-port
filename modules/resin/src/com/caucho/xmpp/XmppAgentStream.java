/*
 * Copyright (c) 1998-2008 Caucho Technology -- all rights reserved
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
 * @author Scott Ferguson
 */

package com.caucho.xmpp;

import com.caucho.bam.BamStream;
import com.caucho.bam.BamError;
import com.caucho.vfs.*;
import java.io.*;
import java.util.logging.*;

import com.caucho.hessian.io.*;

/**
 * Handles callbacks for a xmpp service
 */
public class XmppAgentStream implements BamStream
{
  private static final Logger log
    = Logger.getLogger(XmppAgentStream.class.getName());

  private XmppBrokerStream _packetHandler;
  private XmppContext _xmppContext;
  
  private WriteStream _os;

  private XmppWriter _writer;

  XmppAgentStream(XmppBrokerStream packetHandler,
		  WriteStream os)
  {
    _packetHandler = packetHandler;
    _os = os;

    _xmppContext = packetHandler.getXmppContext();
    XmppMarshalFactory marshalFactory = packetHandler.getMarshalFactory();
      
    XmppStreamWriterImpl out;
    out = new XmppStreamWriterImpl(_os, marshalFactory);
      
    _writer = new XmppWriter(_xmppContext, out);
  }
  
  /**
   * Returns the jid of the client
   */
  public String getJid()
  {
    return _packetHandler.getJid();
  }
  
  public void message(String to, String from, Serializable value)
  {
    try {
      if (log.isLoggable(Level.FINER)) {
	log.finer(_packetHandler + " send message to=" + to
		  + " from=" + from);
      }

      _writer.sendMessage(to, from, value);
      
      _os.flush();
    } catch (IOException e) {
      _packetHandler.close();
      
      log.log(Level.FINE, e.toString(), e);
    }
  }
  
  public void messageError(String to,
			       String from,
			       Serializable value,
			       BamError error)
  {
    try {
      if (log.isLoggable(Level.FINER)) {
	log.finer(_packetHandler + " send error message to=" + to
		  + " from=" + from + " error=" + error);
      }

      _os.flush();
    } catch (IOException e) {
      _packetHandler.close();
      
      log.log(Level.FINE, e.toString(), e);
    }
  }
  
  public boolean queryGet(long id,
		              String to,
		              String from,
		              Serializable query)
  {
    try {
      if (log.isLoggable(Level.FINER)) {
	log.finer(_packetHandler + " queryGet to=" + to
		  + " from=" + from);
      }
      
      _os.flush();
    } catch (IOException e) {
      _packetHandler.close();
      
      log.log(Level.FINE, e.toString(), e);
    }
    
    return true;
  }
  
  public boolean querySet(long id,
		              String to,
		              String from,
		              Serializable query)
  {
    try {
      if (log.isLoggable(Level.FINER)) {
	log.finer(_packetHandler + " querySet to=" + to
		  + " from=" + from);
      }

      String xmppId = _packetHandler.findId(id);
      
      _os.print("<iq id=\"");
      _os.print(xmppId);
      _os.print("\" type=\"set\" to=\"");
      _os.print(to);
      _os.print("\" from=\"");
      _os.print(from);
      _os.print("\">");

      // XXX: print query

      _os.print("</iq>");
      
      _os.flush();
    } catch (IOException e) {
      _packetHandler.close();
      
      log.log(Level.FINE, e.toString(), e);
    }
    
    return true;
  }
  
  public void queryResult(long bamId,
			      String to,
			      String from,
			      Serializable value)
  {
    String id = _xmppContext.findId(bamId);

    _writer.sendQuery(id, to, from, value, "result");
  }
  
  public void queryError(long id,
			     String to,
			     String from,
			     Serializable query,
			     BamError error)
  {
    try {
      if (log.isLoggable(Level.FINER)) {
	log.finer(_packetHandler + " queryError id=" + id
		  + " to=" + to + " from=" + from
		  + " error=" + error);
      }

      String xmppId = _packetHandler.findId(id);

      _os.print("<iq id=\"");
      _os.print(xmppId);
      _os.print("\" type=\"error\" to=\"");
      _os.print(to);
      _os.print("\" from=\"");
      _os.print(from);
      _os.print("\">");

      // XXX: print query

      _os.print("</iq>");
      
      _os.flush();
    } catch (IOException e) {
      _packetHandler.close();
      
      log.log(Level.FINE, e.toString(), e);
    }
  }
  /**
   * General presence, for clients announcing availability
   */
  public void presence(String to,
		       String from,
		       Serializable data)
  {
    try {
      if (log.isLoggable(Level.FINER)) {
	log.finer(_packetHandler + " send presence to=" + to
		  + " from=" + from + " value=" + data);
      }
      
      _writer.sendPresence(to, from, data, "");

      _os.flush();
    } catch (IOException e) {
      _packetHandler.close();
      
      log.log(Level.FINE, e.toString(), e);
    }
  }

  /**
   * General presence, for clients announcing unavailability
   */
  public void presenceUnavailable(String to,
			  	      String from,
				      Serializable data)
  {
    try {
      if (log.isLoggable(Level.FINER)) {
	log.finer(_packetHandler + " send presenceUnavailable to=" + to
		  + " from=" + from + " value=" + data);
      }
      
      _writer.sendPresence(to, from, data, "unavailable");

      _os.flush();
    } catch (IOException e) {
      _packetHandler.close();
      
      log.log(Level.FINE, e.toString(), e);
    }
  }

  /**
   * Presence probe from the server to a client
   */
  public void presenceProbe(String to,
			        String from,
			        Serializable data)
  {
    try {
      if (log.isLoggable(Level.FINER)) {
	log.finer(_packetHandler + " send presenceProbe to=" + to
		  + " from=" + from + " value=" + data);
      }
      
      _writer.sendPresence(to, from, data, "probe");

      _os.flush();
    } catch (IOException e) {
      _packetHandler.close();
      
      log.log(Level.FINE, e.toString(), e);
    }
  }

  /**
   * A subscription request from a client
   */
  public void presenceSubscribe(String to,
				    String from,
				    Serializable data)
  {
    try {
      if (log.isLoggable(Level.FINER)) {
	log.finer(_packetHandler + " send presenceSubscribe to=" + to
		  + " from=" + from + " value=" + data);
      }
      
      _writer.sendPresence(to, from, data, "subscribe");

      _os.flush();
    } catch (IOException e) {
      _packetHandler.close();
      
      log.log(Level.FINE, e.toString(), e);
    }
  }

  /**
   * A subscription response to a client
   */
  public void presenceSubscribed(String to,
				     String from,
				     Serializable data)
  {
    try {
      if (log.isLoggable(Level.FINER)) {
	log.finer(_packetHandler + " send presenceSubscribed to=" + to
		  + " from=" + from + " value=" + data);
      }
      
      _writer.sendPresence(to, from, data, "subscribed");

      _os.flush();
    } catch (IOException e) {
      _packetHandler.close();
      
      log.log(Level.FINE, e.toString(), e);
    }
  }

  /**
   * An unsubscription request from a client
   */
  public void presenceUnsubscribe(String to,
				      String from,
				      Serializable data)
  {
    try {
      if (log.isLoggable(Level.FINER)) {
	log.finer(_packetHandler + " send presenceUnsubscribe to=" + to
		  + " from=" + from + " value=" + data);
      }
      
      _writer.sendPresence(to, from, data, "unsubscribe");

      _os.flush();
    } catch (IOException e) {
      _packetHandler.close();
      
      log.log(Level.FINE, e.toString(), e);
    }
  }

  /**
   * A unsubscription response to a client
   */
  public void presenceUnsubscribed(String to,
				       String from,
				       Serializable data)
  {
    try {
      if (log.isLoggable(Level.FINER)) {
	log.finer(_packetHandler + " send presenceUnsubscribed to=" + to
		  + " from=" + from + " value=" + data);
      }
      
      _writer.sendPresence(to, from, data, "unsubscribed");

      _os.flush();
    } catch (IOException e) {
      _packetHandler.close();
      
      log.log(Level.FINE, e.toString(), e);
    }
  }

  /**
   * An error response to a client
   */
  public void presenceError(String to,
			        String from,
			        Serializable data,
			        BamError error)
  {
    try {
      if (log.isLoggable(Level.FINER)) {
	log.finer(_packetHandler + " send presenceError to=" + to
		  + " from=" + from + " value=" + data);
      }
      
      _os.flush();
    } catch (IOException e) {
      _packetHandler.close();
      
      log.log(Level.FINE, e.toString(), e);
    }
  }
}
