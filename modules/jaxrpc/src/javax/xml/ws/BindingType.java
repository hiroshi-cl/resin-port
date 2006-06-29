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

package javax.xml.ws;
import java.lang.annotation.*;

/**
 * The BindingType annotation is used to specify the binding to use for a web
 * service endpoint implementation class. This annotation may be overriden
 * programmatically or via deployment descriptors, depending on the platform in
 * use. Since: JAX-WS 2.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface BindingType {
    String value() default "";
}

