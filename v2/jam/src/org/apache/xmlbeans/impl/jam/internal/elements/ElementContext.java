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
package org.apache.xmlbeans.impl.jam.internal.elements;

import org.apache.xmlbeans.impl.jam.JamClassLoader;
import org.apache.xmlbeans.impl.jam.annotation.AnnotationProxy;
import org.apache.xmlbeans.impl.jam.provider.JamLogger;

/**
 * <p>Context object required by every ElementImpl.</p>
 *
 * @author Patrick Calahan &lt;email: pcal-at-bea-dot-com&gt;
 */
public interface ElementContext extends JamLogger {

  /**
   * <p>Returns the classloader the elements should use for type resolution.
   * </p>
   */
  public JamClassLoader getClassLoader();

  /**
   * <p>Creates an empty appropriate proxy for the named javadoc tag.</p>
   */
  public AnnotationProxy createProxyForTag(String tagname);

  /**
   * <p>Creates an empty appropriate proxy for the given 175 annotation
   * instance.</p>
   */
  public AnnotationProxy createProxyForAnnotationType(String jsr175typename);

}