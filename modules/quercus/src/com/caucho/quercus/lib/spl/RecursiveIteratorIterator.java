/*
 * Copyright (c) 1998-2007 Caucho Technology -- all rights reserved
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
 * @author Sam
 */

package com.caucho.quercus.lib.spl;

import com.caucho.quercus.annotation.Name;
import com.caucho.quercus.annotation.Optional;
import com.caucho.quercus.env.*;
import com.caucho.quercus.QuercusModuleException;
import com.caucho.util.L10N;

import java.util.ArrayList;
import java.util.EnumSet;

public class RecursiveIteratorIterator
  implements OuterIterator
{
  private final static L10N L = new L10N(RecursiveIteratorIterator.class);

  public static final int LEAVES_ONLY = 0;
  public static final int SELF_FIRST = 1;
  public static final int CHILD_FIRST = 2;

  public static final int CATCH_GET_CHILD = 16;

  private static final StringValue _GET_CHILDREN
    = new InternUnicodeValue("getChildren");

  private static final StringValue _HAS_CHILDREN
    = new InternUnicodeValue("hasChildren");

  private static final StringValue _REWIND
    = new InternUnicodeValue("rewind");

  private static final StringValue _VALID
    = new InternUnicodeValue("valid");

  private final Env _env;
  private int _brokenXXX = 10;

  private enum Mode { LEAVES_ONLY, SELF_FIRST, CHILD_FIRST }
  private enum Flags { CATCH_GET_CHILD }

  private final Mode _mode;
  private final EnumSet<Flags> _flags;

  private int _maxDepth = -1;

  private Stack _stack = new Stack();

  /**
   * @param it a {@link RecursiveIterator}
   *
   * @param mode
   * <dl>
   *   <dt>LEAVES_ONLY</dt>
   *   <dd>(default) only iterate leaves</dd>
   *   <dt><dt>SELF_FIRST</dt></dt>
   *   <dd>iterate parents prior to children</dd>
   *   <dt>CHILD_FIRST </dt>
   *   <dd>iterate children prior to parents</dd>
   * </dl>
   *
   * @param flags
   * <dl>
   *   <dt>CATCH_GET_CHILD</dt>
   *   <dd>ignore exceptions in getChildren() call</dd>
   * </dl>
   */
  @Name("__construct")
  public RecursiveIteratorIterator(Env env,
                                   ObjectValue it,
                                   @Optional("") int mode,
                                   @Optional("0") int flags)
  {
    _env = env;
    _stack.add(it);

    switch (mode) {
      case LEAVES_ONLY:
        _mode = Mode.LEAVES_ONLY;
        break;
      case SELF_FIRST:
        _mode = Mode.SELF_FIRST;
        break;
      case CHILD_FIRST:
        _mode = Mode.CHILD_FIRST;
        break;
      default:
        _mode = Mode.LEAVES_ONLY;
        break;
    }

    if ((flags & CATCH_GET_CHILD) != 0)
      _flags = EnumSet.of(Flags.CATCH_GET_CHILD);
    else
      _flags = EnumSet.noneOf(Flags.class);
  }

  private void advance()
  {
    if (valid())
      nextElement();
  }

  private void advanceToNextSibling()
  {
    if (valid()) {
      switch (_mode) {
        case SELF_FIRST:
          if (callHasChildren())
            nextElement();
          break;

        case LEAVES_ONLY:
          nextElement();
      }
    }
  }

  /**
   * Template method provided for overriding classes, called right after
   * getChildren() and rewind() on the  child, default implemenation does
   * nothing.
   */
  public void beginChildren()
  {
  }

  /**
   * Template method provided for overriding classes, default implementation does
   * nothing.
   */
  public void beginIteration()
  {
  }

  /**
   * Returns current iterator's children.
   */
  public Value callGetChildren()
  {
    ObjectValue last = _stack.getLast();

    if (last instanceof RecursiveIterator)
      return last.callMethod(_env, _GET_CHILDREN);

    return UnsetValue.UNSET;
  }

  /**
   * Returns true if current iterator children.
   */
  public boolean callHasChildren()
  {
    ObjectValue last = _stack.getLast();

    return last.callMethod(_env, _HAS_CHILDREN).toBoolean();
  }

  public Value current()
  {
    return _stack.getLast().current();
  }

  /**
   * Template method provided for overriding classes, called after
   * an iteterator has been used up.
   */
  public void endChildren()
  {
  }

  /**
   * Template method provided for overriding classe, default implementation does
   * nothing.
   */
  public void endIteration()
  {
  }

  /**
   * Returns the current depth.
   */
  public int getDepth()
  {
    return _stack.size() - 1;
  }

  public ObjectValue getInnerIterator()
  {
    return null;
  }

  /**
   * Returns the maximum depth.
   */
  public int getMaxDepth()
  {
    return _maxDepth;
  }

  /**
   * Returns the iterator at the given depth
   * @param depth the depth, default is current depth
   */
  public Value getSubIterator(@Optional("-1") int depth)
  {
    if (depth == -1)
      return _stack.getLast();
    else if (depth >= _stack.size())
      return UnsetValue.UNSET;
    else
      return _stack.get(depth);
  }
  
  public Value key()
  {
    return _stack.getLast().key();
  }

  public void next()
  {
    while (!_stack.isEmpty()) {
      if (_brokenXXX-- <= 0)
        return;

      ObjectValue iter = _stack.getLast();

      if (iter.callMethod(_env, _VALID).toBoolean()) {

        if (!isRecursed(iter) && callHasChildren()) {

          if (_maxDepth == -1 || _maxDepth > _stack.size() - 1) {
            setRecursed(iter, true);

            Value sub;

            try {
              sub = callGetChildren();
            }
            catch (RuntimeException ex) {
              if (!_flags.contains(Flags.CATCH_GET_CHILD)) {
                throw QuercusModuleException.create(ex);
              }

              iter.next();
              continue;
            }

            if (!sub.isNull()) {
              setRecursed(sub, false);
              sub.callMethod(_env, _REWIND);

              if (sub.callMethod(_env, _VALID).toBoolean()) {
                ObjectValue subObjectValue = (ObjectValue) sub;

                _stack.add(subObjectValue);

                if (!subObjectValue.isA("RecursiveIterator"))
                  throw new QuercusModuleException(L.l("{0}::getChildren() must return an object that implements RecursiveIterator",
                                                       subObjectValue.getName()));

                beginChildren();
                return;
              }
            }
          }
          else {
            if (_mode == Mode.LEAVES_ONLY) {
              iter.next();
              continue;
            }
            else
              return;
          }

          iter.next();
          setRecursed(iter, false);

          if (iter.callMethod(_env, _VALID).toBoolean())
            return;
        }
      }
      else if (_stack.hasChildren()) {
        _stack.removeLast();
        iter = _stack.getLast();
        endChildren();
        advance();
      }

      advanceToNextSibling();
    }
  }

  private boolean isRecursed(Value iter)
  {
    return iter.getField(_env, "recursed").toBoolean();
  }

  private void setRecursed(Value iter, boolean isRecursed)
  {
    iter.putField(_env, "recursed", BooleanValue.create(isRecursed));
  }

  /**
   * Template method provided for overriding classes, called when
   * the next element is available
   */
  public void nextElement()
  {
  }

  /**
   * Rewind to the first iteratoir, calling endChildren() along the way
   * as appropriate.
   */
  public void rewind()
  {
    while (_stack.hasChildren()) {
      _stack.removeLast();
      endChildren();
    }

    _stack.get(0).callMethod(_env, _REWIND);

    setRecursed(_stack.getLast(), false);

    advanceToNextSibling();
  }

  /**
   * Stes the maximum depth.
   */
  public void setMaxDepth(int maxDepth)
  {
    _maxDepth = maxDepth;
  }

  /**
   * Return true if the iterator is valid.
   */
  public boolean valid()
  {
    if (_brokenXXX <= 0)
      return false;

    for (int i = _stack.size() - 1; i >= 0; i--) {
      if (_stack.get(i).callMethod(_env, _VALID).toBoolean())
        return true;
    }

    return false;
  }

  private class Stack
    extends ArrayList<ObjectValue>
  {
    public ObjectValue getLast()
    {
      return get(size() - 1);
    }

    public ObjectValue removeLast()
    {
      return remove(size() - 1);
    }

    public boolean hasChildren()
    {
      return size() > 1;
    }
  }
}
