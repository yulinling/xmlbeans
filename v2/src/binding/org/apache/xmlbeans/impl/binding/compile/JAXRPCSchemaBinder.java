/*
* The Apache Software License, Version 1.1
*
*
* Copyright (c) 2003 The Apache Software Foundation.  All rights
* reserved.
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
* 3. The end-user documentation included with the redistribution,
*    if any, must include the following acknowledgment:
*       "This product includes software developed by the
*        Apache Software Foundation (http://www.apache.org/)."
*    Alternately, this acknowledgment may appear in the software itself,
*    if and wherever such third-party acknowledgments normally appear.
*
* 4. The names "Apache" and "Apache Software Foundation" must
*    not be used to endorse or promote products derived from this
*    software without prior written permission. For written
*    permission, please contact apache@apache.org.
*
* 5. Products derived from this software may not be called "Apache
*    XMLBeans", nor may "Apache" appear in their name, without prior
*    written permission of the Apache Software Foundation.
*
* THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
* WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
* OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
* DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
* ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
* SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
* LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
* USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
* ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
* OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
* OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
* SUCH DAMAGE.
* ====================================================================
*
* This software consists of voluntary contributions made by many
* individuals on behalf of the Apache Software Foundation and was
* originally based on software copyright (c) 2003 BEA Systems
* Inc., <http://www.bea.com/>. For more information on the Apache Software
* Foundation, please see <http://www.apache.org/>.
*/

package org.apache.xmlbeans.impl.binding.compile;

import org.apache.xmlbeans.impl.binding.bts.BindingFile;
import org.apache.xmlbeans.impl.binding.bts.BindingType;
import org.apache.xmlbeans.impl.binding.bts.BindingLoader;
import org.apache.xmlbeans.impl.binding.bts.XmlName;
import org.apache.xmlbeans.impl.binding.bts.JavaName;
import org.apache.xmlbeans.impl.binding.bts.ByNameBean;
import org.apache.xmlbeans.impl.binding.bts.QNameProperty;
import org.apache.xmlbeans.impl.binding.bts.SimpleBindingType;
import org.apache.xmlbeans.impl.binding.compile.BindingFileGenerator;
import org.apache.xmlbeans.impl.binding.compile.JavaCodeGenerator;
import org.apache.xmlbeans.impl.binding.compile.JavaCodePrinter;
import org.apache.xmlbeans.impl.common.NameUtil;
import org.apache.xmlbeans.SchemaType;
import org.apache.xmlbeans.SchemaTypeSystem;
import org.apache.xmlbeans.SchemaProperty;
import org.apache.xmlbeans.SchemaLocalAttribute;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlOptions;
import org.apache.xmlbeans.soap.SOAPArrayType;
import org.apache.xmlbeans.soap.SchemaWSDLArrayType;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.HashMap;
import java.util.Collections;
import java.util.AbstractCollection;
import java.util.List;
import java.math.BigInteger;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.io.IOException;

/**
 * This is the "JAXRPC-style" Schema-to-bts compiler.
 */ 
public class JAXRPCSchemaBinder implements JavaCodeGenerator, BindingFileGenerator, SchemaToJavaResult
{
    private Set usedNames = new HashSet();
    private SchemaTypeSystem sts;
    private Map scratchFromXmlName = new LinkedHashMap();
    private Map scratchFromSchemaType = new HashMap(); // for convenience
    private Map scratchFromJavaNameString = new HashMap(); // for printing
    private BindingLoader path;
    private int structureCount;
    private BindingFile bindingFile = new BindingFile();

    private JAXRPCSchemaBinder(SchemaTypeSystem sts, BindingLoader path)
    {
        this.sts = sts;
        this.path = path;
    }
    
    /**
     * Generates a binding for the given set of schema types.
     * 
     * Defer to any previously defined bindings on the BindingLoader path
     * supplied.
     */
    public static SchemaToJavaResult bind(SchemaTypeSystem sts, BindingLoader path)
    {
        JAXRPCSchemaBinder binder = new JAXRPCSchemaBinder(sts, path);
        binder.bind();
        return binder;
    }
    
    // build up four lists:
    // 1. types <-> java classes
    // 2. elements -> java classes
    // 3. attributes -> java classes
    // 4. java classes -> elements (may not be unique)
    
    // each complex type turns into a by-name-strucutre
    // each simple type turns into a simple-type-mapping
    // each element turns into a delegated-element-binding
    // each attribute turns into a delegated-attribute-binding
    
    // javaclass -> element entered only if unique, otherwise warning
    
    // each entry is:
    // 1. created
    // 2. resolved (properties figured out etc)
    // creation can happen at any time
    // resolution must occur after the base type is resolved
    
    private void bind()
    {
        // create a scratch area for every type AND SOAP Array
        createScratchArea();
        
        // resolve or generate all names of java classes
        for (Iterator i = scratchIterator(); i.hasNext(); )
        {
            Scratch scratch = (Scratch)i.next(); 
            resolveJavaName(scratch);
            createBindingType(scratch);
        }
        
        // resolve or generate all java structure
        for (Iterator i = scratchIterator(); i.hasNext(); )
        {
            resolveJavaStructure((Scratch)i.next());
        }
    }

    /**
     * This function goes through all relevant schema types, plus soap
     * array types, and creates a scratch area for each.  Each
     * scratch area is also marked at this time with an XmlName,
     * a schema type, and a category.
     */ 
    private void createScratchArea()
    {
        for (Iterator i = allTypeIterator(); i.hasNext(); )
        {
            SchemaType sType = (SchemaType)i.next();
            XmlName xmlName = XmlName.forSchemaType(sType);
            Scratch scratch;
            
            if (sType.isSimpleType())
            {
                // simple types are atomic
                // todo: what about simple content, custom codecs, etc?
                scratch = new Scratch(sType, xmlName, Scratch.ATOMIC_TYPE);
            }
            else if (sType.isDocumentType())
            {
                scratch = new Scratch(sType, XmlName.forGlobalName(XmlName.ELEMENT, sType.getDocumentElementName()), Scratch.ELEMENT);
            }
            else if (sType.isAttributeType())
            {
                scratch = new Scratch(sType, XmlName.forGlobalName(XmlName.ATTRIBUTE, sType.getDocumentElementName()), Scratch.ELEMENT);
            }
            else if (isSoapArray(sType))
            {
                scratch = new Scratch(sType, xmlName, Scratch.SOAPARRAY_REF);
                xmlName = soapArrayTypeName(sType);
                scratch.setAsIf(xmlName);
                
                // soap arrays unroll like this
                while (xmlName.getComponentType() == XmlName.SOAP_ARRAY)
                {
                    scratch = new Scratch(null, xmlName, Scratch.SOAPARRAY);
                    scratchFromXmlName.put(xmlName, scratch);
                    xmlName = xmlName.getOuterComponent();
                }
            }
            else if (isLiteralArray(sType))
            {
                scratch = new Scratch(sType, xmlName, Scratch.LITERALARRAY_TYPE);
            }
            else
            {
                scratch = new Scratch(sType, xmlName, Scratch.STRUCT_TYPE);
            }
            
            scratchFromXmlName.put(xmlName, scratch);
            scratchFromSchemaType.put(sType, scratch);
            
        }
    }
    
    /**
     * Computes a JavaName for each scratch.  Notice that structures and
     * atoms can be computed directly, but arrays, elements, etc, need
     * to defer to other scratch areas, so this is a resolution
     * process that occurs in dependency order.
     */ 
    private void resolveJavaName(Scratch scratch)
    {
        // already resolved (we recurse to do in dependency order)
        if (scratch.getJavaName() != null)
            return;
        
        switch (scratch.getCategory())
        {
            case Scratch.ATOMIC_TYPE:
                {
                    resolveSimpleScratch(scratch);
                    return;
                }
                
            case Scratch.STRUCT_TYPE:
                {
                    structureCount += 1;
                    JavaName javaName = pickUniqueJavaName(scratch.getSchemaType());
                    scratch.setJavaName(javaName);
                    scratchFromJavaNameString.put(javaName.toString(), scratch);
                    return;
                }
                
            case Scratch.LITERALARRAY_TYPE:
                {
                    SchemaType itemType = getLiteralArrayItemType(scratch.getSchemaType());
                    Scratch itemScratch = scratchForSchemaType(itemType);
                    resolveJavaName(itemScratch);
                    scratch.setJavaName(JavaName.forArray(itemScratch.getJavaName(), 1));
                    return;
                }
                
            case Scratch.SOAPARRAY_REF:
                {
                    XmlName soapArrayName = scratch.getAsIf();
                    Scratch arrayScratch = scratchForXmlName(soapArrayName);
                    resolveJavaName(arrayScratch);
                    scratch.setJavaName(arrayScratch.getJavaName());
                    return;
                }
                
            case Scratch.SOAPARRAY:
                {
                    XmlName arrayName = scratch.getXmlName();
                    XmlName itemName = arrayName.getOuterComponent();
                    Scratch itemScratch = scratchForXmlName(itemName);
                    resolveJavaName(itemScratch);
                    scratch.setJavaName(JavaName.forArray(itemScratch.getJavaName(), arrayName.getNumber()));
                    return;
                }
                
            case Scratch.ELEMENT:
            case Scratch.ATTRIBUTE:
                {
                    SchemaType contentType = scratch.getSchemaType().getProperties()[0].getType();
                    Scratch contentScratch = scratchForSchemaType(contentType);
                    resolveJavaName(contentScratch);
                    scratch.setJavaName(contentScratch.getJavaName());
                    return;
                }
                
            default:
                throw new IllegalStateException("Unrecognized category");
        }
    }

    /**
     * Computes a BindingType for a scratch.
     */ 
    private void createBindingType(Scratch scratch)
    {
        assert(scratch.getBindingType() == null);
        
        switch (scratch.getCategory())
        {
            case Scratch.ATOMIC_TYPE:
                SimpleBindingType simpleResult = new SimpleBindingType(scratch.getJavaName(), scratch.getXmlName(), false);
                simpleResult.setAsIfXmlType(scratch.getAsIf());
                scratch.setBindingType(simpleResult);
                bindingFile.addBindingType(simpleResult, false, true);
                break;
                
            case Scratch.STRUCT_TYPE:
                ByNameBean byNameResult = new ByNameBean(scratch.getJavaName(), scratch.getXmlName(), false);
                scratch.setBindingType(byNameResult);
                bindingFile.addBindingType(byNameResult, true, true);
                break;
                
            case Scratch.LITERALARRAY_TYPE:
                throw new UnsupportedOperationException();
                
            case Scratch.SOAPARRAY_REF:
                throw new UnsupportedOperationException();
                
            case Scratch.SOAPARRAY:
                throw new UnsupportedOperationException();
                
            case Scratch.ELEMENT:
            case Scratch.ATTRIBUTE:
                throw new UnsupportedOperationException();
                
            default:
                throw new IllegalStateException("Unrecognized category");
        }
    }

    /**
     * Now we resolve the structural aspects (property names) for each
     * scratch.  Notice that we only process
     * @param scratch
     */ 
    private void resolveJavaStructure(Scratch scratch)
    {
        if (scratch.getCategory() != Scratch.STRUCT_TYPE)
            return;
        
        if (scratch.isStructureResolved())
            return;
        
        scratch.setStructureResolved(true);
        
        SchemaType baseType = scratch.getSchemaType().getBaseType();
        Collection baseProperties = null;
        if (baseType != null)
            baseProperties = extractProperties(baseType);
        if (baseProperties == null)
            baseProperties = Collections.EMPTY_LIST;
        
        // sort properties based on QName attr/elt
        Map seenAttrProps = new HashMap();
        Map seenEltProps = new HashMap();
        Set seenMethodNames = new HashSet();
        seenMethodNames.add("getClass");
        
        for (Iterator i = baseProperties.iterator(); i.hasNext(); )
        {
            QNameProperty prop = (QNameProperty)i.next();
            if (prop.isAttribute())
                seenAttrProps.put(prop.getQName(), prop);
            else
                seenEltProps.put(prop.getQName(), prop);
            
            // todo: probably this collision avoidance should be using Java introspection instead
            if (prop.getGetterName() != null)
                seenMethodNames.add(prop.getGetterName());
            if (prop.getSetterName() != null)
                seenMethodNames.add(prop.getSetterName());
        }
        
        // now deal with remaining props
        SchemaProperty[] props = scratch.getSchemaType().getProperties();
        for (int i = 0; i < props.length; i++)
        {
            QNameProperty prop = (QNameProperty)(props[i].isAttribute() ? seenAttrProps : seenEltProps).get(props[i].getName());
            if (prop != null)
            {
                // already seen property: verify multiplicity looks cool
                if (prop.isMultiple() != isMultiple(props[i]))
                {
                    // todo: signal nicer error
                    throw new IllegalStateException("Can't change multiplicity");
                }
                
                // todo: think about optionality and nillability too
            }
            else
            {
                SchemaType sType = props[i].getType();
                BindingType bType = bindingTypeForSchemaType(sType);
                
                String propName = pickUniquePropertyName(props[i].getName(), seenMethodNames);
                String getter = "get" + propName;
                String setter = "set" + propName;
                boolean isMultiple = isMultiple(props[i]);
                JavaName collection = null;
                if (isMultiple)
                    collection = JavaName.forArray(bType.getJavaName(), 1);
                
                prop = new QNameProperty();
                prop.setQName(props[i].getName());
                prop.setAttribute(props[i].isAttribute());
                prop.setSetterName(setter);
                prop.setGetterName(getter);
                prop.setCollectionClass(collection);
                prop.setBindingType(bType);
                prop.setNillable(props[i].hasNillable() != SchemaProperty.NEVER);
                prop.setOptional(isOptional(props[i]));
                prop.setMultiple(isMultiple);
            }
            scratch.addQNameProperty(prop);
        }
    }
    
    /**
     * Picks a property name without colliding with names of
     * previously picked getters and setters.
     */ 
    private String pickUniquePropertyName(QName name, Set seenMethodNames)
    {
        String baseName = NameUtil.upperCamelCase(name.getLocalPart());
        String propName = baseName;
        for (int i = 1; ; i += 1)
        {
            String getter = "get" + propName;
            String setter = "set" + propName;
            
            if (!seenMethodNames.contains(getter) &&
                    !seenMethodNames.contains(setter))
            {
                seenMethodNames.add(getter);
                seenMethodNames.add(setter);
                return propName;
            }
            propName = baseName + i;
        }
    }

    /**
     * True if the given SchemaProperty has maxOccurs > 1
     */ 
    private static boolean isMultiple(SchemaProperty prop)
    {
        return (prop.getMaxOccurs() == null || prop.getMaxOccurs().compareTo(BigInteger.ONE) > 0);
    }
    
    /**
     * True if the given SchemaProperty has minOccurs < 1
     */ 
    private static boolean isOptional(SchemaProperty prop)
    {
        return (prop.getMinOccurs().signum() == 0);
    }
    
    /**
     * Returns a collection of QNameProperties for a given schema type that
     * may either be on the path or the current scratch area.
     */ 
    private Collection extractProperties(SchemaType sType)
    {
        // case 1: it's in the current area
        Scratch scratch = scratchForSchemaType(sType);
        if (scratch != null)
        {
            resolveJavaStructure(scratch);
            return scratch.getQNameProperties();
        }
        
        // case 2: it's in the path
        BindingType bType = path.getBindingTypeForXmlPojo(XmlName.forSchemaType(sType));
        if (!(bType instanceof ByNameBean))
        {
            return null;
        }
        Collection result = new ArrayList();
        ByNameBean bnb = (ByNameBean)bType;
        for (Iterator i = bnb.getProperties().iterator(); i.hasNext(); )
        {
            result.add(i.next());
        }
        
        return result;
    }
    
    /**
     * True for a schema type that is a SOAP array.
     */ 
    private static boolean isSoapArray(SchemaType sType)
    {
        // SOAP Array definition must be put on the compiletime classpath
        while (sType != null)
        {
            String signature = XmlName.forSchemaType(sType).toString();
            
            // captures both SOAP 1.1 and SOAP 1.2+
            if (signature.equals("t=Array@http://schemas.xmlsoap.org/soap/encoding/") ||
                    signature.startsWith("t=Array@http://www.w3.org/")
                    && signature.endsWith("/soap-encoding"))
                return true;
            sType = sType.getBaseType();
        }
        return false;
    }
    
    private static final QName arrayType = new QName("http://schemas.xmlsoap.org/soap/encoding/", "arrayType");
    
    /**
     * Returns an XmlName describing a SOAP array.
     */ 
    private static XmlName soapArrayTypeName(SchemaType sType)
    {
        // first, look for wsdl:arrayType default - this will help us with multidimensional arrays
        SOAPArrayType defaultArrayType = null;
        SchemaLocalAttribute attr = sType.getAttributeModel().getAttribute(arrayType);
        if (attr != null)
            defaultArrayType = ((SchemaWSDLArrayType)attr).getWSDLArrayType();
        
        // method 1: trust wsdl:arrayType
        if (defaultArrayType != null)
            return XmlName.forSoapArrayType(defaultArrayType);
        
        // method 2: SOAP 1.2 equivalent?
        // todo: track what do WSDLs do in the world of SOAP 1.2.
        
        // method 3: look at the type of a unique element.
        SchemaType itemType = XmlObject.type;
        SchemaProperty[] props = sType.getElementProperties();
        if (props.length == 1)
            itemType = props[0].getType();
        
        return XmlName.forNestedNumber(XmlName.SOAP_ARRAY, 1, XmlName.forSchemaType(itemType));
    }
    
    /**
     * Climbs the structure of a schema type to find the namespace within
     * which it was defined.
     */ 
    private String findContainingNamespace(SchemaType sType)
    {
        for (;;)
        {
            if (sType.isDocumentType())
                return sType.getDocumentElementName().getNamespaceURI();
            else if (sType.isAttributeType())
                return sType.getAttributeTypeAttributeName().getNamespaceURI();
            else if (sType.getName() != null)
                return sType.getName().getNamespaceURI();
            sType = sType.getOuterType();
        }
    }

    /**
     * Picks a unique fully-qualified Java class name for the given schema
     * type.  Uses and updates the "usedNames" set.
     */ 
    private JavaName pickUniqueJavaName(SchemaType sType)
    {
        QName qname = null;
        while (qname == null)
        {
            if (sType.isDocumentType())
                qname = sType.getDocumentElementName();
            else if (sType.isAttributeType())
                qname = sType.getAttributeTypeAttributeName();
            else if (sType.getName() != null)
                qname = sType.getName();
            else if (sType.getContainerField() != null)
            {
                qname = sType.getContainerField().getName();
                if (qname.getNamespaceURI().length() == 0)
                    qname = new QName(findContainingNamespace(sType), qname.getLocalPart());
            }
            sType = sType.getOuterType();
        }
        
        String baseName = NameUtil.getClassNameFromQName(qname);
        String pickedName = baseName;
        
        for (int i = 1; usedNames.contains(pickedName); i += 1)
            pickedName = baseName + i;
        
        usedNames.add(pickedName);
        
        return JavaName.forString(pickedName);
    }

    /**
     * Resolves an atomic scratch all at once, including its
     * JavaName and basedOn fields.
     * 
     * This resolution method sets up a scratch so that is
     * is "based on" another binding type.  It finds the
     * underlying binding type by climing the base type
     * chain, and grabbing the first hit.
     */ 
    private void resolveSimpleScratch(Scratch scratch)
    {
        assert(scratch.getCategory() == Scratch.ATOMIC_TYPE);
                
        if (scratch.getJavaName() != null)
            return;
        
        SchemaType baseType = scratch.getSchemaType().getBaseType();
        while (baseType != null)
        {
            // find a base type within this type system
            Scratch basedOnScratch = scratchForSchemaType(baseType);
            if (basedOnScratch != null)
            {
                if (basedOnScratch.getCategory() != Scratch.ATOMIC_TYPE)
                    throw new IllegalStateException("Atomic types should only inherit from atomic types");
                resolveSimpleScratch(basedOnScratch);
                scratch.setJavaName(basedOnScratch.getJavaName());
                scratch.setAsIf(basedOnScratch.getXmlName());
                return;
            }
            
            // or if not within this type system, find the base type on the path
            XmlName treatAs = XmlName.forSchemaType(baseType);
            BindingType basedOnBinding = path.getBindingTypeForXmlPojo(treatAs);
            if (basedOnBinding != null)
            {
                scratch.setJavaName(basedOnBinding.getJavaName());
                scratch.setAsIf(treatAs);
                return;
            }
            
            // or go to the next base type up
            baseType = baseType.getBaseType();
        }
        
        // builtin at least should give us xs:anyType
        throw new IllegalStateException("Builtin binding type loader is not on path.");
    }
    
    /**
     * Looks on both the path and in the current scratch area for
     * the binding type corresponding to the given schema type.  Must
     * be called after all the binding types have been created.
     */ 
    private BindingType bindingTypeForSchemaType(SchemaType sType)
    {
        Scratch scratch = scratchForSchemaType(sType);
        if (scratch != null)
            return scratch.getBindingType();
        return path.getBindingTypeForXmlPojo(XmlName.forSchemaType(sType));
    }

    /**
     * Returns the scratch area for a given schema type.  Notice that
     * SOAP arrays have an XmlName but not a schema type.
     */ 
    private Scratch scratchForSchemaType(SchemaType sType)
    {
        return (Scratch)scratchFromSchemaType.get(sType);
    }
    
    /**
     * Returns the scratch area for a given XmlName.
     */ 
    private Scratch scratchForXmlName(XmlName xmlName)
    {
        return (Scratch)scratchFromXmlName.get(xmlName);
    }

    /**
     * Returns the scratch area for a given JavaName.  Notice that only
     * structures generate a java class, so not non-strucuture scratch areas
     * cannot be referenced this way.
     */ 
    private Scratch scratchForJavaNameString(String javaName)
    {
        return (Scratch)scratchFromJavaNameString.get(javaName);
    }
    
    /**
     * Extracts the schema type for the array items for a literal array.
     */ 
    private static SchemaType getLiteralArrayItemType(SchemaType sType)
    {
        // consider: must the type be named "ArrayOf..."?
        
        if (sType.isSimpleType() || sType.getContentType() == SchemaType.SIMPLE_CONTENT)
            return null;
        SchemaProperty[] prop = sType.getProperties();
        if (prop.length != 1 || prop[0].isAttribute())
            return null;
        BigInteger max = prop[0].getMaxOccurs();
        if (max != null && max.compareTo(BigInteger.ONE) <= 0)
            return null;
        return prop[0].getType();
    }

    /**
     * True if the given schema type is interpreted as a .NET-style
     * array.
     */ 
    private static boolean isLiteralArray(SchemaType sType)
    {
        return getLiteralArrayItemType(sType) != null;
    }
    
    /**
     * Scratch area corresponding to a schema type, used for the binding
     * computation.
     */ 
    private static class Scratch
    {
        Scratch(SchemaType schemaType, XmlName xmlName, int category)
        {
            this.schemaType = schemaType;
            this.xmlName = xmlName;
            this.category = category;
        }
        
        private BindingType bindingType;
        private SchemaType schemaType; // may be null
        private JavaName javaName;
        private XmlName xmlName;

        private int category;

        // atomic types get a treatAs
        private XmlName asIf;
        private boolean isStructureResolved;

        // categories of Scratch, established at ctor time
        public static final int ATOMIC_TYPE = 1;
        public static final int STRUCT_TYPE = 2;
        public static final int LITERALARRAY_TYPE = 3;
        public static final int SOAPARRAY_REF = 4;
        public static final int SOAPARRAY = 5;
        public static final int ELEMENT = 6;
        public static final int ATTRIBUTE = 7;

        public int getCategory()
        {
            return category;
        }

        public JavaName getJavaName()
        {
            return javaName;
        }

        public void setJavaName(JavaName javaName)
        {
            this.javaName = javaName;
        }

        public BindingType getBindingType()
        {
            return bindingType;
        }

        public void setBindingType(BindingType bindingType)
        {
            this.bindingType = bindingType;
        }

        public SchemaType getSchemaType()
        {
            return schemaType;
        }

        public XmlName getXmlName()
        {
            return xmlName;
        }

        public void setXmlName(XmlName xmlName)
        {
            this.xmlName = xmlName;
        }

        public XmlName getAsIf()
        {
            return asIf;
        }

        public void setAsIf(XmlName xmlName)
        {
            this.asIf = xmlName;
        }
        
        public void addQNameProperty(QNameProperty prop)
        {
            if (!(bindingType instanceof ByNameBean))
                throw new IllegalStateException();
            ((ByNameBean)bindingType).addProperty(prop);
        }
        
        public Collection getQNameProperties()
        {
            if (!(bindingType instanceof ByNameBean))
                throw new IllegalStateException();
            return ((ByNameBean)bindingType).getProperties();
        }

        public boolean isStructureResolved()
        {
            return this.isStructureResolved;
        }
        
        public void setStructureResolved(boolean isStructureResolved)
        {
            this.isStructureResolved = isStructureResolved;
        }
    }
    
    /**
     * Returns an iterator for all the Scratch's
     */ 
    private Iterator scratchIterator()
    {
        return scratchFromXmlName.values().iterator();
    }
    
    /**
     * Returns an iterator for all the schema types
     */ 
    private Iterator allTypeIterator()
    {
        class AllTypeIterator implements Iterator
        {
            int index;
            List allSeenTypes;
            
            AllTypeIterator(SchemaTypeSystem sts)
            {
                allSeenTypes = new ArrayList();
                allSeenTypes.addAll(Arrays.asList(sts.documentTypes()));
                allSeenTypes.addAll(Arrays.asList(sts.attributeTypes()));
                allSeenTypes.addAll(Arrays.asList(sts.globalTypes()));
                index = 0;
            }
            
            public boolean hasNext()
            {
                return index < allSeenTypes.size();
            }

            public Object next()
            {
                SchemaType next = (SchemaType)allSeenTypes.get(index);
                allSeenTypes.addAll(Arrays.asList(next.getAnonymousTypes()));
                index += 1;
                return next;
            }

            public void remove()
            {
                throw new UnsupportedOperationException();
            }
        }
        
        return new AllTypeIterator(sts);
    }
    
    /**
     * Iterates over all the top-level Java class names generated
     * by this binding.  Used by getToplevelClasses.
     */ 
    private class TopLevelClassNameIterator implements Iterator
    {
        private final Iterator si = scratchIterator();
        private Scratch next = nextStructure();
        
        public boolean hasNext()
        {
            return next != null;
        }

        public Object next()
        {
            String result = next.getJavaName().toString();
            next = nextStructure();
            return result;
        }

        private Scratch nextStructure()
        {
            while (si.hasNext())
            {
                Scratch scratch = (Scratch)si.next();
                if (scratch.getCategory() == Scratch.STRUCT_TYPE)
                    return scratch;
            }
            return null;
        }

        public void remove()
        {
            throw new UnsupportedOperationException();
        }
    }


    /**
     * Returns a collection of fully-qualified Java class name strings
     * generated by this binding.
     */
    public Collection getToplevelClasses()
    {
        return new AbstractCollection()
        {
            public Iterator iterator()
            {
                return new TopLevelClassNameIterator();
            }
    
            public int size()
            {
                return structureCount;
            }
        };
    }

    /**
     * Prints the Java source code for the given generated java class name.
     */
    public void printSourceCode(String topLevelClassName, OutputStream output) throws IOException
    {
        Scratch scratch = scratchForJavaNameString(topLevelClassName);
        if (scratch == null)
            throw new IllegalArgumentException();
        Writer writer = new OutputStreamWriter(output);
        ScratchCodePrinter printer = new ScratchCodePrinter(scratch, writer);
        printer.print();
        writer.flush();
    }
    
    /**
     * Handles the printing for one generated java class.
     */
    private class ScratchCodePrinter extends JavaCodePrinter
    {
        private Scratch scratch;

        ScratchCodePrinter(Scratch scratch, Writer writer)
        {
            super(writer);
            assert(scratch.getCategory() == Scratch.STRUCT_TYPE);
            this.scratch = scratch;
        }
        
        private void print() throws IOException
        {
            JavaName javaName = scratch.getJavaName();
            
            String packageName = javaName.getPackage();
            String shortClassName = javaName.getShortClassName();
            BindingType baseType = bindingTypeForSchemaType(scratch.getSchemaType().getBaseType());
            String baseJavaname = null;
            if (baseType != null)
            {
                baseJavaname = baseType.getJavaName().toString();
                if (baseJavaname.equals("java.lang.Object"))
                    baseJavaname = null;
            }
            
            line("package " + packageName);
            line();
            javadoc("Generated from schema type " + scratch.getXmlName());
            line("class " + shortClassName + (baseJavaname != null ? " extends " + baseJavaname : ""));
            startBlock();
            
            Collection props = scratch.getQNameProperties();
            Map fieldNames = new HashMap();
            Set seenFieldNames = new HashSet();
            
            // pick field names
            for (Iterator i = props.iterator(); i.hasNext(); )
            {
                QNameProperty prop = (QNameProperty)i.next();
                fieldNames.put(prop, pickUniqueFieldName(prop.getGetterName(), seenFieldNames));
            }
            
            // print private fields
            for (Iterator i = props.iterator(); i.hasNext(); )
            {
                QNameProperty prop = (QNameProperty)i.next();
                JavaName jType = prop.getJavaTypeName();
                if (prop.getCollectionClass() != null)
                    jType = prop.getCollectionClass();
                
                line("private " + jType.toString() + " " + fieldNames.get(prop) + ";");
            }
            
            // print getters and setters
            for (Iterator i = props.iterator(); i.hasNext(); )
            {
                QNameProperty prop = (QNameProperty)i.next();
                JavaName jType = prop.getJavaTypeName();
                if (prop.getCollectionClass() != null)
                    jType = prop.getCollectionClass();
                String fieldName = (String)fieldNames.get(prop);
                
                line();
                line("public " + jType.toString() + " " + prop.getGetterName() + "()");
                startBlock();
                line("return " + fieldName + ";");
                endBlock();
                line();
                line("public void " + prop.getSetterName() + "(" + jType.toString() + " " + fieldName + ")");
                startBlock();
                line("this." + fieldName + " = " + fieldName + ";");
                endBlock();
            }
            
            endBlock();
        }
        
        private String pickUniqueFieldName(String getter, Set seenNames)
        {
            String baseName;
            
            if (getter.length() > 3 && getter.startsWith("get"))
                baseName = Character.toLowerCase(getter.charAt(3)) + getter.substring(4);
            else
                baseName = "field";
            
            String fieldName = baseName;
            for (int i = 1; seenNames.contains(fieldName); i += 1)
                fieldName = baseName + i;
            
            seenNames.add(fieldName);
            return fieldName;
        }
    }

    /**
     * Prints the binding file generated by this binding.
     */ 
    public void printBindingFile(OutputStream output) throws IOException
    {
        bindingFile.write().save(output, new XmlOptions().setSavePrettyPrint());
    }

    /**
     * Returns a BindingFileGenerator for this SchemaToJavaResult.
     */ 
    public BindingFileGenerator getBindingFileGenerator()
    {
        return this;
    }

    /**
     * Returns the JavaCodeGenerator for this SchemaToJavaResult.
     */ 
    public JavaCodeGenerator getJavaCodeGenerator()
    {
        return this;
    }
}