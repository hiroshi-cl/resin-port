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

package com.caucho.jsp;

import java.util.logging.Logger;
import java.util.logging.Level;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.jsp.JspWriter;

import com.caucho.server.connection.AbstractResponseStream;


/**
 * A buffered JSP writer encapsulating a Writer.
 */
public class JspWriterAdapter extends AbstractBodyContent {
  // the parent writer
  private JspWriter _parent;
  
  // the underlying writer
  private AbstractResponseStream _out;
  
  // the page context
  private PageContextImpl _pageContext;

  private boolean _isClosed;

  /**
   * Creates a new JspWriterAdapter
   */
  JspWriterAdapter()
  {
  }

  /**
   * Initialize the JSP writer
   *
   * @param os the underlying stream
   */
  void init(PageContextImpl pageContext)
  {
    _pageContext = pageContext;
    _out = null;
    _isClosed = false;
  }

  /**
   * Initialize the JSP writer
   *
   * @param os the underlying stream
   */
  void init(JspWriter parent, AbstractResponseStream out)
  {
    _parent = parent;
    _out = out;
    _isClosed = false;
  }

  /**
   * Writes a character array to the writer.
   *
   * @param buf the buffer to write.
   * @param off the offset into the buffer
   * @param len the number of characters to write
   */
  final public void write(char []buf, int offset, int length)
    throws IOException
  {
    if (_isClosed)
      return;

    _out.print(buf, offset, length);
  }
  
  /**
   * Writes a character to the output.
   *
   * @param buf the buffer to write.
   */
  final public void write(int ch) throws IOException
  {
    if (_isClosed)
      return;

    _out.print(ch);
  }

  /**
   * Writes a subsection of a string to the output.
   */
  final public void write(String s, int off, int len)
    throws IOException
  {
    char []writeBuffer = _out.getCharBuffer();
    int size = writeBuffer.length;
    int writeLength = _out.getCharOffset();
    int end = off + len;

    while (off < end) {
      int sublen = end - off;

      if (size - writeLength < sublen) {
	if (size == writeLength) {
	  writeBuffer = _out.nextCharBuffer(writeLength);
	  writeLength = 0;
	  
	  if (size < sublen)
	    sublen = size;
	}
	else
	  sublen = size - writeLength;
      }

      int tail = off + sublen;
      s.getChars(off, tail, writeBuffer, writeLength);

      off = tail;
      writeLength += sublen;
    }

    _out.setCharOffset(writeLength);
  }

  /**
   * Returns the buffer size of the writer.
   */
  public int getBufferSize()
  {
    return _out.getBufferSize();
  }

  /**
   * Returns the remaining bytes in the buffer.
   */
  public int getRemaining()
  {
    return _out.getRemaining();
  }

  public void clear() throws IOException
  {
    if (_isClosed)
      return;

    clearBuffer();
  }

  public void clearBuffer() throws IOException
  {
    if (_isClosed)
      return;

    _out.clearBuffer();
  }

  public void flushBuffer()
    throws IOException
  {
    if (_isClosed)
      return;
  }

  /**
   * Flushes the output stream.
   */
  public void flush() throws IOException
  {
    if (_isClosed)
      return;

    _out.flushChar();
  }

  /**
   * Pops the enclosing writer.
   */
  AbstractJspWriter popWriter()
  {
    try {
      close();
    } catch (Throwable e) {
      log.log(Level.FINER, e.toString(), e);
    }

    return super.popWriter();
  }

  final public void close() throws IOException
  {
    _isClosed = true;

    _out = null;
    _parent = null;
    _pageContext = null;
  }
}
