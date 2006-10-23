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
 * @author Scott Ferguson
 */

package com.caucho.quercus.lib.file;

import java.util.logging.Logger;
import java.util.logging.Level;

import java.io.*;

import com.caucho.quercus.QuercusModuleException;

import com.caucho.quercus.env.*;

import com.caucho.vfs.*;

/**
 * Represents a Quercus file open for reading
 */
public class AbstractBinaryInputOutput
  implements BinaryInput, BinaryOutput, Closeable
{
  private static final Logger log
    = Logger.getLogger(AbstractBinaryInputOutput.class.getName());

  private ReadStream _is;
  private WriteStream _os;

  protected AbstractBinaryInputOutput()
  {
  }

  protected AbstractBinaryInputOutput(ReadStream is, WriteStream os)
  {
    init(is, os);
  }

  public void init(ReadStream is, WriteStream os)
  {
    _is = is;
    _os = os;
  }

  //
  // read methods
  //
  
  /**
   * Returns the input stream.
   */
  public InputStream getInputStream()
  {
    return _is;
  }

  /**
   * Opens a copy.
   */
  public BinaryInput openCopy()
    throws IOException
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  public void setEncoding(String encoding)
    throws UnsupportedEncodingException
  {
    if (_is != null)
      _is.setEncoding(encoding);
  }

  /**
   *
   */
  public void unread()
    throws IOException
  {
    if (_is != null)
      _is.unread();
  }

  /**
   * Reads a character from a file, returning -1 on EOF.
   */
  public int read()
    throws IOException
  {
    if (_is != null)
      return _is.read();
    else
      return -1;
  }

  /**
   * Reads a buffer from a file, returning -1 on EOF.
   */
  public int read(byte []buffer, int offset, int length)
    throws IOException
  {
    if (_is != null) {
      return _is.read(buffer, offset, length);
    }
    else
      return -1;
  }

  /**
   * Reads into a binary builder.
   */
  public BinaryValue read(int length)
    throws IOException
  {
    if (_is == null)
      return null;

    BinaryBuilderValue bb = new BinaryBuilderValue();

    while (length > 0) {
      bb.prepareReadBuffer();

      int sublen = bb.getLength() - bb.getOffset();

      if (length < sublen)
	sublen = length;

      sublen = read(bb.getBuffer(), bb.getOffset(), sublen);

      if (sublen > 0) {
	bb.setOffset(bb.getOffset() + sublen);
	length -= sublen;
      }
      else
	return bb;
    }

    return bb;
  }

  /**
   * Reads the optional linefeed character from a \r\n
   */
  public boolean readOptionalLinefeed()
    throws IOException
  {
    if (_is == null)
      return false;
    
    int ch = _is.read();

    if (ch == '\n') {
      return true;
    }
    else {
      _is.unread();
      return false;
    }
  }

  public void writeToStream(OutputStream os, int length)
    throws IOException
  {
    if (_is != null) {
      _is.writeToStream(os, length);
    }
  }

  /**
   * Reads a line from a file, returning null on EOF.
   */
  public StringValue readLine(long length)
    throws IOException
  {
    if (_is == null)
      return null;
    
    StringBuilderValue sb = new StringBuilderValue();

    int ch;

    for (; length > 0 && (ch = _is.readChar()) >= 0; length--) {
      if (ch == '\n') {
	sb.append((char) ch);
	return sb;
      }
      else if (ch == '\r') {
	sb.append('\r');
	
	int ch2 = _is.read();

	if (ch == '\n')
	  sb.append('\n');
	else
	  _is.unread();
	
	return sb;
      }
      else
	sb.append((char) ch);
    }

    if (sb.length() == 0)
      return null;
    else
      return sb;
  }

  /**
   * Returns true on the EOF.
   */
  public boolean isEOF()
  {
    if (_is == null)
      return true;
    else {
      try {
        // XXX: not quite right for sockets
        return  _is.available() <= 0;
      } catch (IOException e) {
        log.log(Level.FINE, e.toString(), e);

        return true;
      }
    }
  }

  /**
   * Returns the current location in the file.
   */
  public long getPosition()
  {
    if (_is == null)
      return -1;
    else
      return _is.getPosition();
  }

  /**
   * Returns the current location in the file.
   */
  public boolean setPosition(long offset)
  {
    if (_is == null)
      return false;

    try {
      _is.setPosition(offset);

      return true;
    } catch (IOException e) {
      throw new QuercusModuleException(e);
    }
  }

  public long seek(long offset, int whence)
  {
    switch (whence) {
      case BinaryInput.SEEK_CUR:
        offset = getPosition() + offset;
        break;
      case BinaryInput.SEEK_END:
        // don't necessarily have an end
        offset = getPosition();
        break;
      case SEEK_SET:
        break;
      default:
        break;
    }

    setPosition(offset);

    return offset;
  }

  public Value stat()
  {
    return BooleanValue.FALSE;
  }

  /**
   * Closes the stream for reading.
   */
  public void closeRead()
  {
    ReadStream is = _is;
    _is = null;

    if (is != null)
      is.close();
  }

  //
  // write methods
  //

  /**
   * Returns self as the output stream.
   */
  public OutputStream getOutputStream()
  {
    return _os;
  }

  public void write(int ch)
    throws IOException
  {
    _os.write(ch);
  }

  public void write(byte []buffer, int offset, int length)
    throws IOException
  {
    _os.write(buffer, offset, length);
  }

  /**
   * Writes to a stream.
   */
  public int write(InputStream is, int length)
  {
    int writeLength = 0;

    TempBuffer tb = TempBuffer.allocate();
    byte []buffer = tb.getBuffer();

    try {
      while (length > 0) {
	int sublen;

	if (length < buffer.length)
	  sublen = length;
	else
	  sublen = buffer.length;

	sublen = is.read(buffer, 0, sublen);

	if (sublen < 0)
	  break;

	write(buffer, 0, sublen);

	writeLength += sublen;
	length -= sublen;
      }

      return writeLength;
    } catch (IOException e) {
      throw new QuercusModuleException(e);
    } finally {
      TempBuffer.free(tb);
    }
  }
  
  /**
   * Prints a string to a file.
   */
  public void print(char v)
    throws IOException
  {
    write((byte) v);
  }

  /**
   * Prints a string to a file.
   */
  public void print(String v)
    throws IOException
  {
    _os.print(v);
  }

  /**
   * Flushes the output.
   */
  public void flush()
    throws IOException
  {
    _os.flush();
  }


  /**
   * Closes the file.
   */
  public void closeWrite()
  {
    try {
      WriteStream os = _os;
      _os = null;

      if (os != null)
        os.close();
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
    }
  }

  /**
   * Closes the file.
   */
  public void close()
  {
    closeRead();
    closeWrite();
  }

  public Object toJavaObject()
  {
    return this;
  }

  public String getResourceType()
  {
    return "stream";
  }

  /**
   * Converts to a string.
   */
  public String toString()
  {
    if (_is != null)
      return "AbstractBinaryInputOutput[" + _is.getPath() + "]";
    else
      return "AbstractBinaryInputOutput[closed]";
  }
}

