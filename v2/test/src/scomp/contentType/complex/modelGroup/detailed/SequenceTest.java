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
package scomp.contentType.complex.modelGroup.detailed;

import xbean.scomp.contentType.modelGroup.SequenceEltDocument;
import xbean.scomp.contentType.modelGroup.SequenceT;
import org.apache.xmlbeans.XmlString;
import scomp.common.BaseCase;

import java.math.BigInteger;

/**
 * @owner: ykadiysk
 * Date: Jul 16, 2004
 * Time: 3:25:57 PM
 */
public class SequenceTest extends BaseCase {

    public void testWrongOrder() throws Throwable{
        SequenceEltDocument doc=SequenceEltDocument.Factory.parse(
                "<SequenceElt xmlns=\"http://xbean/scomp/contentType/ModelGroup\">" +
                "<child1>1</child1>" +
                "<child3>2</child3>" +
                "<child2>Foobar</child2>" +
                "</SequenceElt>   ");

            assertTrue (! doc.validate(validateOptions) );
            showErrors();
      
    }
    public void testWrongCardinality(){
          SequenceEltDocument doc=SequenceEltDocument.Factory.newInstance();
        SequenceT elt=doc.addNewSequenceElt();
        XmlString valueStr=XmlString.Factory.newInstance();
        valueStr.setStringValue("foobar");
        BigInteger valueInt=new BigInteger("-3");
        elt.xsetChild2Array(new XmlString[]{});
        elt.setChild3Array(new BigInteger[]{valueInt});
        elt.addChild3(valueInt);
        elt.setChild3Array(1,BigInteger.TEN);
            assertEquals("<xml-fragment><child3>-3</child3>" +
                    "<child3>10</child3></xml-fragment>",elt.xmlText());
        assertTrue( !elt.validate(validateOptions) );
        assertEquals(3,errorList.size());

        showErrors();
        //assert each error individually here

    }
}