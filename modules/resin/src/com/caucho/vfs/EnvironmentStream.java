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
 *
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.vfs;

import java.io.*;
import java.net.*;
import java.util.*;

import com.caucho.util.*;
import com.caucho.vfs.*;

import com.caucho.loader.EnvironmentLocal;

/**
 * A stream that varies depending on the environment class loader.
 */
public class EnvironmentStream extends StreamImpl {
  // static stdout stream
  private static PrintStream _systemOut;
  // static stderr stream
  private static PrintStream _systemErr;
  
  // static stdout stream
  private static EnvironmentStream _stdoutStream;
  // static stderr stream
  private static EnvironmentStream _stderrStream;
  
  // context variable storing the per-environment stream.
  private EnvironmentLocal<OutputStream> _environmentStream;

  /**
   * Create the environment stream.
   *
   * @param envVariable the variable for the underlying stream
   * @param defaultStream the stream used if outside an environment
   */
  public EnvironmentStream(String envVariable, OutputStream defaultStream)
  {
    _environmentStream = new EnvironmentLocal<OutputStream>(envVariable);
    _environmentStream.setGlobal(defaultStream);
  }

  /**
   * Create the environment stream.
   *
   * @param defaultStream the stream used if outside an environment
   */
  public EnvironmentStream(OutputStream defaultStream)
  {
    _environmentStream = new EnvironmentLocal<OutputStream>();
    _environmentStream.setGlobal(defaultStream);
  }

  /**
   * Returns the context stream's variable.
   */
  public String getVariable()
  {
    return _environmentStream.getVariable();
  }

  /**
   * Returns the global stream
   */
  public OutputStream getGlobalStream()
  {
    return (OutputStream) _environmentStream.getGlobal();
  }

  /**
   * Returns the context stream's variable.
   */
  public Object setGlobalStream(OutputStream defaultStream)
  {
    return _environmentStream.setGlobal(defaultStream);
  }

  /**
   * Returns the global stream
   */
  public OutputStream getStream()
  {
    return (OutputStream) _environmentStream.get();
  }

  /**
   * Returns the context stream's variable.
   */
  public Object setStream(OutputStream os)
  {
    return _environmentStream.set(os);
  }

  /**
   * True if the stream can write
   */
  public boolean canWrite()
  {
    OutputStream stream = getStream();

    return stream != null;
  }

  /**
   * Write data to the stream.
   */
  public void write(byte []buf, int offset, int length, boolean isEnd)
    throws IOException
  {
    OutputStream stream = getStream();

    if (stream == null)
      return;

    synchronized (stream) {
      stream.write(buf, offset, length);
      if (isEnd)
        stream.flush();
    }
  }

  /**
   * Flush data to the stream.
   */
  public void flush()
    throws IOException
  {
    OutputStream stream = getStream();

    if (stream == null)
      return;

    synchronized (stream) {
      stream.flush();
    }
  }

  /**
   * Flush data to the stream.
   */
  public void close()
    throws IOException
  {
    OutputStream stream = getStream();

    if (stream == null)
      return;

    synchronized (stream) {
      stream.flush();
    }
  }

  /**
   * Sets the backing stream for System.out
   */
  public static void setStdout(OutputStream os)
  {
    if (_stdoutStream == null) {
      OutputStream systemOut = System.out;
      
      _stdoutStream = new EnvironmentStream("caucho.stdout.stream",
                                            systemOut);
      WriteStream out = new WriteStream(_stdoutStream);
      out.setDisableClose(true);
      _systemOut = new PrintStream(out, true);
      System.setOut(_systemOut);

      if (os == systemOut)
        return;
    }

    if (os == _systemErr || os == _systemOut)
      return;

    if (os instanceof WriteStream) {
      WriteStream out = (WriteStream) os;

      if (out.getSource() == StdoutStream.create() ||
	  out.getSource() == StderrStream.create())
	return;
    }
    
    _stdoutStream.setStream(os);
  }

  /**
   * Returns the environment stream for System.out
   */
  public static EnvironmentStream getStdout()
  {
    return _stdoutStream;
  }

  /**
   * Sets path as the backing stream for System.err
   */
  public static void setStderr(OutputStream os)
  {
    if (_stderrStream == null) {
      OutputStream systemErr = System.err;
      
      _stderrStream = new EnvironmentStream("caucho.stderr.stream",
                                            systemErr);
      WriteStream err = new WriteStream(_stderrStream);
      err.setDisableClose(true);
      _systemErr = new PrintStream(err, true);
      System.setErr(_systemErr);
      
      if (os == systemErr)
        return;
    }

    if (os == _systemErr || os == _systemOut)
      return;

    if (os instanceof WriteStream) {
      WriteStream out = (WriteStream) os;

      if (out.getSource() == StdoutStream.create() ||
	  out.getSource() == StderrStream.create())
	return;
    }

    _stderrStream.setStream(os);
  }

  /**
   * Returns the environment stream for System.err
   */
  public static EnvironmentStream getStderr()
  {
    return _stderrStream;
  }
}
