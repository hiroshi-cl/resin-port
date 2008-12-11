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

package com.caucho.server.deploy;

import com.caucho.bam.BamBroker;
import com.caucho.bam.SimpleBamService;
import com.caucho.git.GitCommit;
import com.caucho.git.GitObjectStream;
import com.caucho.git.GitRepository;
import com.caucho.git.GitTree;
import com.caucho.git.GitType;
import com.caucho.jmx.Jmx;
import com.caucho.management.server.DeployControllerMXBean;
import com.caucho.management.server.EAppMXBean;
import com.caucho.management.server.EarDeployMXBean;
import com.caucho.management.server.WebAppMXBean;
import com.caucho.management.server.WebAppDeployMXBean;
import com.caucho.server.cluster.Server;
import com.caucho.server.resin.Resin;
import com.caucho.server.host.HostController;
import com.caucho.server.webapp.WebAppController;
import com.caucho.util.Alarm;
import com.caucho.util.L10N;
import com.caucho.util.LruCache;
import com.caucho.util.QDate;
import com.caucho.vfs.*;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.management.ObjectName;

abstract public class DeployRepository
{
  private static final Logger log
    = Logger.getLogger(DeployRepository.class.getName());

  private static final L10N L = new L10N(DeployRepository.class);

  private Server _server;

  private String _repositoryTag;

  private String _repositoryHash;
  private DeployTagMap _tagMap = new DeployTagMap();

  public DeployRepository(Server server)
  {
    _server = server;

    String id = _server.getServerId();
    if ("".equals(id))
      id = "default";

    _repositoryTag = "resin/repository/" + id;
  }

  /**
   * Initialize the repository
   */
  public void init()
  {
  }

  /**
   * Returns the .git repository tag
   */
  protected String getRepositoryTag()
  {
    return _repositoryTag;
  }

  /**
   * Updates the repository
   */
  public void update()
  {
    update(getTag(getRepositoryTag()));
  }

  /**
   * Updates based on a sha1 commit entry
   */
  protected boolean update(String sha1)
  {
    String oldSha1 = _tagMap.getCommitHash();

    if (sha1 == null || sha1.equals(oldSha1)) {
      return true;
    }
    else {
      try {
	DeployTagMap tagMap = new DeployTagMap(this, sha1);

	return setTagMap(tagMap);
      } catch (IOException e) {
	log.log(Level.FINE, e.toString(), e);
      }
    }

    return false;
  }

  //
  // deploy tag management
  //

  /**
   * Returns the tag commit hash
   */
  protected String getCommitHash()
  {
    return _tagMap.getCommitHash();
  }
  
  /**
   * Returns the tag map.
   */
  public Map<String,DeployTagEntry> getTagMap()
  {
    return _tagMap.getTagMap();
  }

  /**
   * Returns the tag root.
   */
  public String getTagRoot(String tag)
  {
    DeployTagEntry entry = getTagMap().get(tag);

    if (entry != null)
      return entry.getRoot();
    else
      return null;
  }

  /**
   * Adds a tag
   *
   * @param tag the symbolic tag for the repository
   * @param sha1 the root for the tag's content
   * @param user the user adding a tag.
   * @param server the server adding a tag.
   * @param message user's message for the commit
   * @param version symbolic version name for the commit
   */
  abstract public boolean setTag(String tag,
				 String sha1,
				 String user,
				 String server,
				 String message,
				 String version);
  
  /**
   * Removes a tag
   *
   * @param tag the symbolic tag for the repository
   * @param user the user adding a tag.
   * @param server the server adding a tag.
   * @param message user's message for the commit
   */
  abstract public boolean removeTag(String tag,
				    String user,
				    String server,
				    String message);

  /**
   * Creates a tag entry
   *
   * @param tag the symbolic tag for the repository
   * @param user the user adding a tag.
   * @param server the server adding a tag.
   * @param message user's message for the commit
   * @param version symbolic version name for the commit
   */
  protected DeployTagMap addTagData(String tag,
				    String root,
				    String user,
				    String server,
				    String message,
				    String version)
  {
    try {
      update();
	
      DeployTagMap deployTagMap = _tagMap;
      
      Map<String,DeployTagEntry> tagMap = deployTagMap.getTagMap();

      if (! validateFile(root))
	throw new DeployRepositoryException(L.l("'{0}' is an invalid .git file",
						root));

      DeployTagEntry oldEntry = tagMap.get(tag);
      String parent = null;

      DeployTagEntry entry
	= new DeployTagEntry(this, tag, root, parent);

      Map<String,DeployTagEntry> newTagMap
	= new TreeMap<String,DeployTagEntry>(tagMap);
    
      newTagMap.put(tag, entry);

      DeployTagMap newDeployTagMap = new DeployTagMap(this,
						      deployTagMap,
						      newTagMap);

      if (_tagMap == deployTagMap) {
	return newDeployTagMap;
      }
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new DeployRepositoryException(e);
    }

    return null;
  }

  /**
   * Removes a tag entry
   *
   * @param tag the symbolic tag for the repository
   * @param user the user adding a tag.
   * @param server the server adding a tag.
   * @param message user's message for the commit
   * @param version symbolic version name for the commit
   */
  protected DeployTagMap removeTagData(String tag,
				       String user,
				       String server,
				       String message)
  {
    try {
      update();
	
      DeployTagMap deployTagMap = _tagMap;
      
      Map<String,DeployTagEntry> tagMap = deployTagMap.getTagMap();

      DeployTagEntry oldEntry = tagMap.get(tag);

      if (oldEntry == null)
	return deployTagMap;

      Map<String,DeployTagEntry> newTagMap
	= new TreeMap<String,DeployTagEntry>(tagMap);
    
      newTagMap.remove(tag);

      DeployTagMap newDeployTagMap = new DeployTagMap(this,
						      deployTagMap,
						      newTagMap);

      if (_tagMap == deployTagMap) {
	return newDeployTagMap;
      }
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new DeployRepositoryException(e);
    }

    return null;
  }

  protected boolean setTagMap(DeployTagMap tagMap)
  {
    synchronized (this) {
      if (_tagMap.getSequence() < tagMap.getSequence()) {
	_tagMap = tagMap;

	setTag(getRepositoryTag(), tagMap.getCommitHash());

	if (log.isLoggable(Level.FINER))
	  log.finer(this + " updating deployment " + tagMap);

	return true;
      }
      else
	return false;
    }
  }

  //
  // git tag management
  //

  /**
   * Returns the sha1 stored at the gitTag
   */
  abstract protected String getTag(String gitTag);

  /**
   * Writes the sha1 stored at the gitTag
   */
  abstract protected void setTag(String gitTag, String sha1);
  
  //
  // git file management
  //

  /**
   * Returns true if the file exists.
   */
  abstract public boolean exists(String sha1);

  /**
   * Returns true if the file is a blob.
   */
  abstract public boolean isBlob(String sha1);

  /**
   * Returns true if the file is a blob.
   */
  abstract public GitType getType(String sha1);
  
  /**
   * Validates a file, checking that it and its dependencies exist.
   */
  public boolean validateFile(String sha1)
  {
    GitType type = getType(sha1);

    if (type == null)
      return false;
    
    return true;
  }

  /**
   * Adds a path to the repository.  If the path is a directory or a
   * jar scheme, adds the contents recursively.
   */
  abstract public String addPath(Path path);

  /**
   * Adds a stream to the repository.
   */
  abstract public String addInputStream(InputStream is)
    throws IOException;

  /**
   * Opens a stream to a git blob
   */
  abstract public InputStream openBlob(String sha1)
    throws IOException;

  /**
   * Reads a git tree from the repository
   */
  abstract public GitTree readTree(String sha1)
    throws IOException;

  /**
   * Adds a git tree to the repository
   */
  abstract public String addTree(GitTree tree)
    throws IOException;

  /**
   * Reads a git commit from the repository
   */
  abstract public GitCommit readCommit(String sha1)
    throws IOException;

  /**
   * Adds a git commit to the repository
   */
  abstract public String addCommit(GitCommit commit)
    throws IOException;

  /**
   * Opens a stream to the raw git file.
   */
  abstract public InputStream openRawGitFile(String sha1)
    throws IOException;

  /**
   * Writes a raw git file
   */
  abstract public void writeRawGitFile(String sha1, InputStream is)
    throws IOException;

  /**
   * Writes the contents to a stream.
   */
  abstract public void writeToStream(OutputStream os, String sha1);

  /**
   * Expands the repository to the filesystem.
   */
  abstract public void expandToPath(Path path, String root);

  public String toString()
  {
    return getClass().getSimpleName() + "[" + _repositoryTag + "]";
  }
}