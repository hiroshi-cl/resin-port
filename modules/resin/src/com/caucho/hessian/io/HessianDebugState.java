/*
 * Copyright (c) 2001-2004 Caucho Technology, Inc.  All rights reserved.
 *
 * The Apache Software License, Version 1.1
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution, if
 *    any, must include the following acknowlegement:
 *       "This product includes software developed by the
 *        Caucho Technology (http://www.caucho.com/)."
 *    Alternately, this acknowlegement may appear in the software itself,
 *    if and wherever such third-party acknowlegements normally appear.
 *
 * 4. The names "Hessian", "Resin", and "Caucho" must not be used to
 *    endorse or promote products derived from this software without prior
 *    written permission. For written permission, please contact
 *    info@caucho.com.
 *
 * 5. Products derived from this software may not be called "Resin"
 *    nor may "Resin" appear in their names without prior written
 *    permission of Caucho Technology.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL CAUCHO TECHNOLOGY OR ITS CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY,
 * OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
 * OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * @author Scott Ferguson
 */

package com.caucho.hessian.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;

/**
 * Debugging input stream for Hessian requests.
 */
public class HessianDebugState implements Hessian2Constants
{
  private PrintWriter _dbg;

  private State _state;
  private ArrayList<State> _stateStack = new ArrayList<State>();

  private ArrayList<ObjectDef> _objectDefList
    = new ArrayList<ObjectDef>();

  private int _refId;
  private boolean _isNewline = true;
  private boolean _isObject = false;
  
  /**
   * Creates an uninitialized Hessian input stream.
   */
  public HessianDebugState(PrintWriter dbg)
  {
    _dbg = dbg;

    _state = new InitialState();
  }

  /**
   * Reads a character.
   */
  public void next(int ch)
    throws IOException
  {
    _state = _state.next(ch);
  }

  void pushStack(State state)
  {
    _stateStack.add(state);
  }

  State popStack()
  {
    return _stateStack.remove(_stateStack.size() - 1);
  }

  void println()
  {
    if (! _isNewline) {
      _dbg.println();
      _dbg.flush();
    }

    _isNewline = true;
  }

  abstract class State {
    State _next;

    State()
    {
    }

    State(State next)
    {
      _next = next;
    }
    
    abstract State next(int ch);

    boolean isShift(Object value)
    {
      return false;
    }

    State shift(Object value)
    {
      return this;
    }

    int depth()
    {
      if (_next != null)
	return _next.depth();
      else
	return 0;
    }

    void printIndent(int depth)
    {
      if (_isNewline) {
	for (int i = 0; i < depth() + depth - 1; i++)
	  _dbg.print("  ");
      }
    }

    void print(String string)
    {
      print(0, string);
    }

    void print(int depth, String string)
    {
      printIndent(depth);
      
      _dbg.print(string);
      _isNewline = false;
      _isObject = false;
    }

    void println(String string)
    {
      println(0, string);
    }

    void println(int depth, String string)
    {
      printIndent(depth);

      _dbg.println(string);
      _dbg.flush();
      _isNewline = true;
      _isObject = false;
    }

    void println()
    {
      if (! _isNewline) {
	_dbg.println();
	_dbg.flush();
      }

      _isNewline = true;
      _isObject = false;
    }

    void printObject(String string)
    {
      if (_isObject)
	println();
      
      printIndent(0);

      _dbg.print(string);
      _dbg.flush();

      _isNewline = false;
      _isObject = true;
    }
    
    protected State nextObject(int ch)
    {
      switch (ch) {
      case -1:
	println();
	return this;
	
      case 'N':
	if (isShift(null))
	  return shift(null);
	else {
	  println(1, "null");
	  return this;
	}
	
      case 'T':
	if (isShift(Boolean.TRUE))
	  return shift(Boolean.TRUE);
	else {
	  printObject("true");
	  return this;
	}
	
      case 'F':
	if (isShift(Boolean.FALSE))
	  return shift(Boolean.FALSE);
	else {
	  printObject("false");
	  return this;
	}

      case 0x80: case 0x81: case 0x82: case 0x83: 
      case 0x84: case 0x85: case 0x86: case 0x87: 
      case 0x88: case 0x89: case 0x8a: case 0x8b: 
      case 0x8c: case 0x8d: case 0x8e: case 0x8f: 

      case 0x90: case 0x91: case 0x92: case 0x93: 
      case 0x94: case 0x95: case 0x96: case 0x97: 
      case 0x98: case 0x99: case 0x9a: case 0x9b: 
      case 0x9c: case 0x9d: case 0x9e: case 0x9f: 

      case 0xa0: case 0xa1: case 0xa2: case 0xa3: 
      case 0xa4: case 0xa5: case 0xa6: case 0xa7: 
      case 0xa8: case 0xa9: case 0xaa: case 0xab: 
      case 0xac: case 0xad: case 0xae: case 0xaf: 

      case 0xb0: case 0xb1: case 0xb2: case 0xb3: 
      case 0xb4: case 0xb5: case 0xb6: case 0xb7: 
      case 0xb8: case 0xb9: case 0xba: case 0xbb: 
      case 0xbc: case 0xbd: case 0xbe: case 0xbf:
	{
	  Integer value = new Integer(ch - 0x90);
	  
	  if (isShift(value))
	    return shift(value);
	  else {
	    printObject(value.toString());
	    return this;
	  }
	}

      case 0xc0: case 0xc1: case 0xc2: case 0xc3: 
      case 0xc4: case 0xc5: case 0xc6: case 0xc7: 
      case 0xc8: case 0xc9: case 0xca: case 0xcb: 
      case 0xcc: case 0xcd: case 0xce: case 0xcf:
	return new IntegerState(this, "int", ch - 0xc8, 3);

      case 0xd0: case 0xd1: case 0xd2: case 0xd3: 
      case 0xd4: case 0xd5: case 0xd6: case 0xd7: 
	return new IntegerState(this, "int", ch - 0xd4, 2);

      case 'I':
	return new IntegerState(this, "int");

      case 'R':
	return new RefState(this, "Ref");

      case 'r':
	pushStack(this);
	return new RemoteState();

      case 'L':
	return new LongState(this);

      case 'd':
	return new DateState(this);

      case 'D':
	return new DoubleState(this);

      case 0x00:
	printObject("\"\"");
	return this;

      case 0x01: case 0x02: case 0x03:
      case 0x04: case 0x05: case 0x06: case 0x07:
      case 0x08: case 0x09: case 0x0a: case 0x0b:
      case 0x0c: case 0x0d: case 0x0e: case 0x0f:
	
      case 0x10: case 0x11: case 0x12: case 0x13:
      case 0x14: case 0x15: case 0x16: case 0x17:
      case 0x18: case 0x19: case 0x1a: case 0x1b:
      case 0x1c: case 0x1d: case 0x1e: case 0x1f:
	return new StringState(this, 'S', ch);

      case 'S': case 'X':
	return new StringState(this, 'S', true);

      case 's': case 'x':
	return new StringState(this, 'S', false);

      case 'B':
	pushStack(this);
	return new ByteState(true);

      case 'b':
	pushStack(this);
	return new ByteState(false);

      case 'M':
	return new MapState(this, _refId++);

      case 'V':
	return new ListState(this, _refId++);

      case 'O':
	return new ObjectDefState(this);

      case 'o':
	return new ObjectState(this, _refId++);

      case ' ':
	return this;
	
      default:
	println(String.valueOf((char) ch) + ": unexpected character");
	return this;
      }
    }
  }
  
  class InitialState extends State {
    State next(int ch)
    {
      println();
      
      if (ch == 'r') {
	pushStack(this);
	return new ReplyState();
      }
      else if (ch == 'c') {
	pushStack(this);
	return new CallState();
      }
      else
	return nextObject(ch);
    }
  }
  
  class IntegerState extends State {
    String _typeCode;
    
    int _length;
    int _value;

    IntegerState(State next, String typeCode)
    {
      super(next);

      _typeCode = typeCode;
    }

    IntegerState(State next, String typeCode, int value, int length)
    {
      super(next);

      _typeCode = typeCode;

      _value = value;
      _length = length;
    }

    State next(int ch)
    {
      _value = 256 * _value + (ch & 0xff);

      if (++_length == 4) {
	Integer value = new Integer(_value);
	
	if (_next.isShift(value))
	  return _next.shift(value);
	else {
	  printObject(value.toString());
	  
	  return _next;
	}
      }
      else
	return this;
    }
  }
  
  class RefState extends State {
    String _typeCode;
    
    int _length;
    int _value;

    RefState(State next, String typeCode)
    {
      super(next);

      _typeCode = typeCode;
    }

    RefState(State next, String typeCode, int value, int length)
    {
      super(next);

      _typeCode = typeCode;

      _value = value;
      _length = length;
    }

    State next(int ch)
    {
      _value = 256 * _value + (ch & 0xff);

      if (++_length == 4) {
	Integer value = new Integer(_value);
	
	if (_next.isShift(value))
	  return _next.shift(value);
	else {
	  printObject("ref(#" + value + ")");
	  
	  return _next;
	}
      }
      else
	return this;
    }
  }
  
  class LongState extends State {
    int _length;
    long _value;

    LongState(State next)
    {
      super(next);
    }
    
    State next(int ch)
    {
      _value = 256 * _value + (ch & 0xff);

      if (++_length == 8) {
	Long value = new Long(_value);
	
	if (_next.isShift(value))
	  return _next.shift(value);
	else {
	  printObject(value.toString() + " (long)");
	  
	  return _next;
	}
      }
      else
	return this;
    }
  }
  
  class DateState extends State {
    int _length;
    long _value;

    DateState(State next)
    {
      super(next);
    }
      
    
    State next(int ch)
    {
      _value = 256 * _value + (ch & 0xff);

      if (++_length == 8) {
	java.util.Date value = new java.util.Date(_value);

	if (_next.isShift(value))
	  return _next.shift(value);
	else {
	  printObject(value.toString());
	  
	  return _next;
	}
      }
      else
	return this;
    }
  }
  
  class DoubleState extends State {
    int _length;
    long _value;

    DoubleState(State next)
    {
      super(next);
    }
    
    State next(int ch)
    {
      _value = 256 * _value + (ch & 0xff);

      if (++_length == 8) {
	Double value = Double.longBitsToDouble(_value);

	if (_next.isShift(value))
	  return _next.shift(value);
	else {
	  printObject(value.toString());
	  
	  return _next;
	}
      }
      else
	return this;
    }
  }
  
  class StringState extends State {
    private static final int TOP = 0;
    private static final int UTF_2_1 = 1;
    private static final int UTF_3_1 = 2;
    private static final int UTF_3_2 = 3;

    char _typeCode;
    
    StringBuilder _value = new StringBuilder();
    int _lengthIndex;
    int _length;
    boolean _isLastChunk;
    
    int _utfState;
    char _ch;

    StringState(State next, char typeCode, boolean isLastChunk)
    {
      super(next);
      
      _typeCode = typeCode;
      _isLastChunk = isLastChunk;
    }

    StringState(State next, char typeCode, int length)
    {
      super(next);
      
      _typeCode = typeCode;
      _isLastChunk = true;
      _length = length;
      _lengthIndex = 2;
    }
    
    State next(int ch)
    {
      if (_lengthIndex < 2) {
	_length = 256 * _length + (ch & 0xff);
	
	if (++_lengthIndex == 2 && _length == 0 && _isLastChunk) {
	  if (_next.isShift(_value.toString()))
	    return _next.shift(_value.toString());
	  else {
	    printObject("\"" + _value + "\"");
	    return _next;
	  }
	}
	else
	  return this;
      }
      else if (_length == 0) {
	if (ch == 's' || ch == 'x') {
	  _isLastChunk = false;
	  _lengthIndex = 0;
	  return this;
	}
	else if (ch == 'S' || ch == 'X') {
	  _isLastChunk = true;
	  _lengthIndex = 0;
	  return this;
	}
	else {
	  println(String.valueOf((char) ch) + ": unexpected character");
	  return _next;
	}
      }

      switch (_utfState) {
      case TOP:
	if (ch < 0x80) {
	  _length--;

	  _value.append((char) ch);
	}
	else if (ch < 0xe0) {
	  _ch = (char) ((ch & 0x1f) << 6);
	  _utfState = UTF_2_1;
	}
	else {
	  _ch = (char) ((ch & 0xf) << 12);
	  _utfState = UTF_3_1;
	}
	break;

      case UTF_2_1:
      case UTF_3_2:
	_ch += ch & 0x3f;
	_value.append(_ch);
	_length--;
	_utfState = TOP;
	break;

      case UTF_3_1:
	_ch += (char) ((ch & 0x3f) << 6);
	_utfState = UTF_3_2;
	break;
      }

      if (_length == 0) {
	if (_next.isShift(_value.toString()))
	  return _next.shift(_value.toString());
	else {
	  printObject("\"" + _value + "\"");
	  
	  return _next;
	}
      }
      else
	return this;
    }
  }
  
  class ByteState extends State {
    int _lengthIndex;
    int _length;
    boolean _isLastChunk;

    ByteState(boolean isLastChunk)
    {
      _isLastChunk = isLastChunk;
    }
    
    State next(int ch)
    {
      if (_lengthIndex < 2) {
	_length = 256 * _length + (ch & 0xff);
	
	if (++_lengthIndex == 2) {
	  if (_isLastChunk)
	    println("B: " + _length);
	  else
	    println("b: " + _length);
	}

	if (_lengthIndex == 2 && _length == 0 && _isLastChunk) {
	  return popStack();
	}
	else
	  return this;
      }
      else if (_length == 0) {
	if (ch == 'b') {
	  _isLastChunk = false;
	  _lengthIndex = 0;
	  return this;
	}
	else if (ch == 'B') {
	  _isLastChunk = true;
	  _lengthIndex = 0;
	  return this;
	}
	else {
	  println(String.valueOf((char) ch) + ": unexpected character");
	  return popStack();
	}
      }

      _length--;

      if (_length == 0) {
	return popStack();
      }
      else
	return this;
    }
  }
  
  class MapState extends State {
    private static final int TYPE = 0;
    private static final int KEY = 1;
    private static final int VALUE = 2;

    private int _refId;

    private int _state;
    private boolean _hasData;

    MapState(State next, int refId)
    {
      super(next);
      
      _refId = refId;
      _state = TYPE;
    }

    @Override
    boolean isShift(Object value)
    {
      return _state == TYPE;
    }

    @Override
    State shift(Object object)
    {
      println();
      println("map " + object + "(#" + _refId + ")");
      
      _state = KEY;
      
      return this;
    }

    @Override
    int depth()
    {
      if (_state == TYPE)
	return _next.depth();
      else
	return _next.depth() + 2;
    }
    
    State next(int ch)
    {
      switch (_state) {
      case TYPE:
	if (ch == 't') {
	  return new StringState(this, 't', true);
	}
	else if (ch == 'z') {
	  println();
	  println("map (#" + _refId + ")");
	  return _next;
	}
	else {
	  _state = VALUE;
	  return nextObject(ch);
	}
	
      case KEY:
	if (ch == 'z') {
	  if (_hasData)
	    println("}");
	  
	  return _next;
	}
	else {
	  if (_hasData)
	    println("}");
	  
	  print("{");
	  _hasData = true;
	  
	  _state = VALUE;
	  return nextObject(ch);
	}
	
      case VALUE:
	print(", ");
	
	_state = KEY;
	return nextObject(ch);

      default:
	throw new IllegalStateException();
      }
    }
  }
  
  class ObjectDefState extends State {
    private static final int TYPE_COUNT = 0;
    private static final int TYPE = 1;
    private static final int COUNT = 2;
    private static final int FIELD = 3;

    private int _refId;

    private int _state;
    private boolean _hasData;
    private int _count;

    private String _type;
    private ArrayList<String> _fields = new ArrayList<String>();

    ObjectDefState(State next)
    {
      super(next);
      
      _state = TYPE_COUNT;
    }

    @Override
    boolean isShift(Object value)
    {
      return true;
    }

    @Override
    State shift(Object object)
    {
      if (_state == TYPE_COUNT) {
	int length = (Integer) object;

	_state = TYPE;

	return new StringState(this, 't', length);
      }
      else if (_state == TYPE) {
	_type = (String) object;

	println("defun " + _type);

	_objectDefList.add(new ObjectDef(_type, _fields));

	_state = COUNT;

	return this;
      }
      else if (_state == COUNT) {
	_count = (Integer) object;

	_state = FIELD;

	if (_count == 0)
	  return _next;
	else
	  return this;
      }
      else if (_state == FIELD) {
	String field = (String) object;

	_count--;

	_fields.add(field);

	println(field);

	if (_count == 0)
	  return _next;
	else
	  return this;
      }
      else {
	throw new UnsupportedOperationException();
      }
    }

    @Override
    int depth()
    {
      if (_state <= TYPE)
	return _next.depth();
      else
	return _next.depth() + 2;
    }
    
    State next(int ch)
    {
      switch (_state) {
      case TYPE_COUNT:
	return nextObject(ch);
	
      case TYPE:
	return nextObject(ch);
	
      case COUNT:
	return nextObject(ch);
	
      case FIELD:
	return nextObject(ch);

      default:
	throw new IllegalStateException();
      }
    }
  }
  
  class ObjectState extends State {
    private static final int TYPE = 0;
    private static final int FIELD = 1;

    private int _refId;

    private int _state;
    private ObjectDef _def;
    private int _count;

    ObjectState(State next, int refId)
    {
      super(next);

      _refId = refId;
      _state = TYPE;
    }

    @Override
    boolean isShift(Object value)
    {
      if (_state == TYPE)
	return true;
      else
	return false;
    }

    @Override
    State shift(Object object)
    {
      if (_state == TYPE) {
	int def = (Integer) object;

	_def = _objectDefList.get(def);

	println(_def.getType() + " object (#" + _refId + ")");

	_state = FIELD;

	if (_def.getFields().size() == 0)
	  return _next;
      }

      return this;
    }

    @Override
    int depth()
    {
      if (_state <= TYPE)
	return _next.depth();
      else
	return _next.depth() + 2;
    }
    
    State next(int ch)
    {
      switch (_state) {
      case TYPE:
	return nextObject(ch);
	
      case FIELD:
	println();
	print(_def.getFields().get(_count++) + ": ");

	if (_def.getFields().size() <= _count)
	  return _next.nextObject(ch);
	else
	  return nextObject(ch);

      default:
	throw new IllegalStateException();
      }
    }
  }
  
  class ListState extends State {
    private static final int TYPE = 0;
    private static final int LENGTH = 1;
    private static final int VALUE = 2;

    private int _refId;

    private int _state;
    private boolean _hasData;

    ListState(State next, int refId)
    {
      super(next);
      
      _refId = refId;
      _state = TYPE;
    }

    @Override
    boolean isShift(Object value)
    {
      return _state == TYPE || _state == LENGTH;
    }

    @Override
    State shift(Object object)
    {
      if (_state == TYPE) {
	println();
	println("list " + object + "(#" + _refId + ")");
      
	_state = LENGTH;
      
	return this;
      }
      else if (_state == LENGTH) {
	_state = VALUE;

	return this;
      }
      else
	return this;
    }

    @Override
    int depth()
    {
      if (_state <= LENGTH)
	return _next.depth();
      else
	return _next.depth() + 2;
    }
    
    State next(int ch)
    {
      switch (_state) {
      case TYPE:
	if (ch == 't') {
	  return new StringState(this, 't', true);
	}
	else if (ch == 'l') {
	  println();
	  println("list (#" + _refId + ")");
	  _state = LENGTH;
	  
	  return new IntegerState(this, "length");
	}
	else if (ch == 'z') {
	  println();
	  println("list (#" + _refId + ")");
	  return _next;
	}
	else {
	  _state = VALUE;
	  return nextObject(ch);
	}
	
      case LENGTH:
	if (ch == 'z') {
	  return _next;
	}
	else if (ch == 'l') {
	  return new IntegerState(this, "length");
	}
	else {
	  _state = VALUE;
	  
	  return nextObject(ch);
	}
	
      case VALUE:
	if (ch == 'z')
	  return _next;
	else
	  return nextObject(ch);

      default:
	throw new IllegalStateException();
      }
    }
  }
  
  class CallState extends State {
    private static final int MAJOR = 0;
    private static final int MINOR = 1;
    private static final int HEADER = 2;
    private static final int VALUE = 3;
    private static final int ARG = 4;

    private int _state;
    private int _major;
    private int _minor;
    
    State next(int ch)
    {
      switch (_state) {
      case MAJOR:
	_major = ch;
	_state = MINOR;
	return this;
	
      case MINOR:
	_minor = ch;
	_state = HEADER;
	println(-1, "call " + _major + "." + _minor);
	return this;
	
      case HEADER:
	if (ch == 'H') {
	  _state = VALUE;
	  return new StringState(this, 'H', true);
	}
 	else if (ch == 'm') {
	  _state = ARG;
	  return new StringState(this, 'm', true);
	}
	else {
	  println((char) ch + ": unexpected char");
	  return popStack();
	}
	
      case VALUE:
	_state = HEADER;
	return nextObject(ch);
	
      case ARG:
	if (ch == 'z')
	  return popStack();
	else
	  return nextObject(ch);

      default:
	throw new IllegalStateException();
      }
    }
  }
  
  class ReplyState extends State {
    private static final int MAJOR = 0;
    private static final int MINOR = 1;
    private static final int HEADER = 2;
    private static final int VALUE = 3;
    private static final int END = 4;

    private int _state;
    private int _major;
    private int _minor;
    
    State next(int ch)
    {
      switch (_state) {
      case MAJOR:
	if (ch == 't' || ch == 'S')
	  return new RemoteState().next(ch);
	
	_major = ch;
	_state = MINOR;
	return this;
	
      case MINOR:
	_minor = ch;
	_state = HEADER;
	println(-1, "reply " + _major + "." + _minor);
	return this;
	
      case HEADER:
	if (ch == 'H') {
	  _state = VALUE;
	  return new StringState(this, 'H', true);
	}
	else if (ch == 'f') {
	  println("f: fault");
	  _state = END;
	  return new MapState(this, 0);
	}
 	else {
	  _state = END;
	  return nextObject(ch);
	}
	
      case VALUE:
	_state = HEADER;
	return nextObject(ch);
	
      case END:
	return popStack().next(ch);

      default:
	throw new IllegalStateException();
      }
    }
  }
  
  class RemoteState extends State {
    private static final int TYPE = 0;
    private static final int VALUE = 1;
    private static final int END = 2;

    private int _state;
    private int _major;
    private int _minor;
    
    State next(int ch)
    {
      switch (_state) {
      case TYPE:
	println(-1, "remote");
	if (ch == 't') {
	  _state = VALUE;
	  return new StringState(this, 't', false);
	}
	else {
	  _state = END;
	  return nextObject(ch);
	}

      case VALUE:
	_state = END;
	return nextObject(ch);

      case END:
	return popStack().next(ch);

      default:
	throw new IllegalStateException();
      }
    }
  }

  static class ObjectDef {
    private String _type;
    private ArrayList<String> _fields;

    ObjectDef(String type, ArrayList<String> fields)
    {
      _type = type;
      _fields = fields;
    }

    String getType()
    {
      return _type;
    }

    ArrayList<String> getFields()
    {
      return _fields;
    }
  }
}
