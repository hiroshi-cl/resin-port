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
 * @author Nam Nguyen
 */

package com.caucho.quercus.lib;

import com.caucho.quercus.env.Env;
import com.caucho.quercus.lib.file.BinaryStream;

import java.io.Closeable;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * Represents a resource opened by proc_open().
 */
public class ProcOpenResource implements Closeable
{
  private static final Logger log
  = Logger.getLogger(ProcOpenResource.class.getName());
  
  private Env _env;
  
  private ProcOpenOutput _in;
  private ProcOpenInput _out;
  private ProcOpenInput _err;

  private Process _process;

  public ProcOpenResource(Env env,
                          Process process,
                          ProcOpenOutput in,
                          ProcOpenInput out,
                          ProcOpenInput err)
  {
    _env = env;
    _process = process;
    
    _in = in;
    _out = out;
    _err = err;
    
    env.addClose(this);
  }
  
  public void close()
  {
    pclose();
  }
  
  public int pclose()
  {
    try {
      if (_in != null)
        _in.close();
      
      _out.close();
      _err.close();
    
      return _process.waitFor();
    }
    catch (Exception e) {
      log.log(Level.FINE, e.getMessage());
      _env.warning(e);
      
      return -1;
    }
    finally {
      _env.removeClose(this);
    }
  }
  
  public boolean terminate()
  {
    if (_in != null)
      _in.close();

    _out.close();
    _err.close();
  
    _process.destroy();
    
    _env.removeClose(this);
    
    return true;
  }
}
