/*   Copyright 2004 The Apache Software Foundation
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.xmlbeans.impl.jam.editable;

import org.apache.xmlbeans.impl.jam.JConstructor;
import org.apache.xmlbeans.impl.jam.JClass;

/**
 * Editable representation of a java constructor.
 *
 * @author Patrick Calahan <pcal@bea.com>
 */
public interface EConstructor extends JConstructor, EMember {

  public void addException(String exceptionClassName);

  public void addException(JClass exceptionClass);

  public void removeException(String exceptionClassName);

  public void removeException(JClass exceptionClass);

  public EParameter addNewParameter(String typeName, String paramName);

  public EParameter addNewParameter(JClass type, String paramName);

  public void removeParameter(EParameter parameter);

  public EParameter[] getEditableParameters();

}