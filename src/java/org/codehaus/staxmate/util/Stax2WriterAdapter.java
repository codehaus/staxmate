/* Stax2 API extension for Streaming Api for Xml processing (StAX).
 *
 * Copyright (c) 2006- Tatu Saloranta, tatu.saloranta@iki.fi
 *
 * Licensed under the License specified in file LICENSE, included with
 * the source code.
 * You may not use this file except in compliance with the License.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.codehaus.staxmate.util;

import javax.xml.namespace.NamespaceContext;
import javax.xml.stream.*;

import org.codehaus.stax2.*;
import org.codehaus.stax2.validation.ValidationProblemHandler;
import org.codehaus.stax2.validation.XMLValidationSchema;
import org.codehaus.stax2.validation.XMLValidator;

/**
 * This adapter implements parts of {@link XMLStreamWriter2}, the
 * extended stream reader defined by Stax2 extension, by wrapping
 * a vanilla Stax 1.0 {@link XMLStreamWriter} implementation.
 *<p>
 * Note: the implementation is incomplete as-is, since not all
 * features needed are accessible via basic Stax 1.0 interface.
 * However, it should be enough to allow StaxMate to handle
 * underlying Stax implementations in generic way, independent
 * of whether they are truly Stax2 compatible or not.
 */
public final class Stax2WriterAdapter
    //extends StreamWriterDelegate // such a thing doesn't exist... doh
    implements XMLStreamWriter2 /* From Stax2 */
               ,XMLStreamConstants
{
    /**
     * Underlying Stax 1.0 compliant stream writer.
     */
    final XMLStreamWriter mDelegate;

    /**
     * Encoding we have determined to be used, according to method
     * calls (write start document etc.)
     */
    String mEncoding;

    /*
    ////////////////////////////////////////////////////
    // Life-cycle methods
    ////////////////////////////////////////////////////
     */

    private Stax2WriterAdapter(XMLStreamWriter sw)
    {
        mDelegate = sw;
    }

    /**
     * Method that should be used to add dynamic support for
     * {@link XMLStreamWriter2}. Method will check whether the
     * stream reader passed happens to be a {@link XMLStreamWriter2};
     * and if it is, return it properly cast. If not, it will create
     * necessary wrapper to support features needed by StaxMate,
     * using vanilla Stax 1.0 interface.
     */
    public static XMLStreamWriter2 wrapIfNecessary(XMLStreamWriter sw)
    {
        if (sw instanceof XMLStreamWriter2) {
            return (XMLStreamWriter2) sw;
        }
        return new Stax2WriterAdapter(sw);
    }

    /*
    ////////////////////////////////////////////////////
    // Stax 1.0 delegation
    ////////////////////////////////////////////////////
     */

    public void close()
        throws XMLStreamException
    {
        mDelegate.close();
    }

    public void flush()
        throws XMLStreamException
    {
        mDelegate.flush();
    }
    
    public NamespaceContext getNamespaceContext()
    {
        return mDelegate.getNamespaceContext();
    }

    public String getPrefix(String uri)
        throws XMLStreamException
    {
        return mDelegate.getPrefix(uri);
    }

    public Object getProperty(String name)
    {
        return mDelegate.getProperty(name);
    }

    public void setDefaultNamespace(String uri)
        throws XMLStreamException
    {
        mDelegate.setDefaultNamespace(uri);
    }

    public void setNamespaceContext(NamespaceContext context)
        throws XMLStreamException
    {    
        mDelegate.setNamespaceContext(context);
    }

    public void setPrefix(String prefix, String uri)
        throws XMLStreamException
    {
        mDelegate.setPrefix(prefix, uri);
    
    }

    public void writeAttribute(String localName, String value)
        throws XMLStreamException
    {    
        mDelegate.writeAttribute(localName, value);
    }

    public void writeAttribute(String namespaceURI, String localName, String value)
        throws XMLStreamException
    {    
        mDelegate.writeAttribute(namespaceURI, localName, value);
    }

 public void writeAttribute(String prefix, String namespaceURI, String localName, String value)
        throws XMLStreamException
    {    
        mDelegate.writeAttribute(prefix, namespaceURI, localName, value);
    }

    public void writeCData(String data)
        throws XMLStreamException
    {    
        mDelegate.writeCData(data);
    }

    public void writeCharacters(char[] text, int start, int len)
        throws XMLStreamException
    {    
        mDelegate.writeCharacters(text, start, len);
    }

    public void writeCharacters(String text)
        throws XMLStreamException
    {    
        mDelegate.writeCharacters(text);
    }

    public void writeComment(String data)
        throws XMLStreamException
    {    
        mDelegate.writeComment(data);
    }

    public void writeDefaultNamespace(String namespaceURI)
        throws XMLStreamException
    {    
        mDelegate.writeDefaultNamespace(namespaceURI);
    }

    public void writeDTD(String dtd)
        throws XMLStreamException
    {    
        mDelegate.writeDTD(dtd);
    }

    public void writeEmptyElement(String localName)
        throws XMLStreamException
    {    
        mDelegate.writeEmptyElement(localName);
    }

    public void writeEmptyElement(String namespaceURI, String localName)
        throws XMLStreamException
    {    
        mDelegate.writeEmptyElement(namespaceURI, localName);
    }
    
    public void writeEmptyElement(String prefix, String localName, String namespaceURI)
        throws XMLStreamException
    {    
        mDelegate.writeEmptyElement(prefix, localName, namespaceURI);
    }

    public void writeEndDocument()
        throws XMLStreamException
    {    
        mDelegate.writeEndDocument();
    }

    public void writeEndElement()
        throws XMLStreamException
    {    
        mDelegate.writeEndElement();
    }

    public void writeEntityRef(String name)
        throws XMLStreamException
    {    
        mDelegate.writeEntityRef(name);
    }

    public void writeNamespace(String prefix, String namespaceURI)
        throws XMLStreamException
    {    
        mDelegate.writeNamespace(prefix, namespaceURI);
    }

    public void writeProcessingInstruction(String target)
        throws XMLStreamException
    {    
        mDelegate.writeProcessingInstruction(target);
    }

    public void writeProcessingInstruction(String target, String data)
        throws XMLStreamException
    {    
        mDelegate.writeProcessingInstruction(target, data);
    }

    public void writeStartDocument()
        throws XMLStreamException
    {    
        mDelegate.writeStartDocument();
    }

    public void writeStartDocument(String version)
        throws XMLStreamException
    {    
        mDelegate.writeStartDocument(version);
    }

    public void writeStartDocument(String encoding, String version)
        throws XMLStreamException
    {    
        mEncoding = encoding;
        mDelegate.writeStartDocument(encoding, version);
    }

    public void writeStartElement(String localName)
        throws XMLStreamException
    {    
        mDelegate.writeStartElement(localName);
    }

    public void writeStartElement(String namespaceURI, String localName)
        throws XMLStreamException
    {    
        mDelegate.writeStartElement(namespaceURI, localName);
    }

    public void writeStartElement(String prefix, String localName, String namespaceURI) 
        throws XMLStreamException
    {    
        mDelegate.writeStartElement(prefix, localName, namespaceURI);
    }

     /*
    ////////////////////////////////////////////////////
    // XMLStreamWriter2 (StAX2) implementation
    ////////////////////////////////////////////////////
     */

    public boolean isPropertySupported(String name)
    {
        /* No real clean way to check this, so let's just fake by
         * claiming nothing is supported
         */
        return false;
    }

    public boolean setProperty(String name, Object value)
    {
        throw new IllegalArgumentException("No settable property '"+name+"'");
    }

    public XMLStreamLocation2 getLocation()
    {
        // No easy way to keep track of it, without impl support
        return null;
    }

    public String getEncoding()
    {
        // We may have been able to infer it... if so:
        return mEncoding;
    }

    public void writeCData(char[] text, int start, int len)
        throws XMLStreamException
    {
        writeCData(new String(text, start, len));
    }

    public void writeDTD(String rootName, String systemId, String publicId,
                         String internalSubset)
        throws XMLStreamException
    {
        /* This may or may not work... depending on how well underlying
         * implementation follows stax 1.0 spec (it should work)
         */
        StringBuffer sb = new StringBuffer();
        sb.append("<!DOCTYPE");
        sb.append(rootName);
        if (systemId != null) {
            if (publicId != null) {
                sb.append(" PUBLIC \"");
                sb.append(publicId);
                sb.append("\" \"");
            } else {
                sb.append(" SYSTEM \"");
            }
            sb.append(systemId);
            sb.append('"');
        }
        // Hmmh. Should we output empty internal subset?
        if (internalSubset != null && internalSubset.length() > 0) {
            sb.append(" [");
            sb.append(internalSubset);
            sb.append(']');
        }
        sb.append('>');
        writeDTD(sb.toString());
    }

    public void writeFullEndElement()
        throws XMLStreamException
    {
        /* It may be possible to fake it, by pretending to write
         * character output, which in turn should prevent writing of
         * an empty element...
         */
        mDelegate.writeCharacters("");
        mDelegate.writeEndElement();
    }

    public void writeSpace(String text)
        throws XMLStreamException
    {
        /* Hmmh. Two choices: either try to write as regular characters,
         * or output as is via raw calls. Latter would be safer, if we
         * had access to it; former may escape incorrectly.
         * While this may not be optimal, let's try former
         */
        writeRaw(text);
    }

    public void writeSpace(char[] text, int offset, int length)
        throws XMLStreamException
    {
        // See comments above...
        writeRaw(text, offset, length);
    }

    public void writeStartDocument(String version, String encoding,
                                   boolean standAlone)
        throws XMLStreamException
    {
        // No good way to do it, so let's do what we can...
        writeStartDocument(encoding, version);
    }
    
    /*
    ///////////////////////////////
    // Stax2, Pass-through methods
    ///////////////////////////////
    */

    public void writeRaw(String text)
        throws XMLStreamException
    {
        writeRaw(text, 0, text.length());
    }

    public void writeRaw(String text, int offset, int len)
        throws XMLStreamException
    {
        // There is no clean way to implement this via Stax 1.0, alas...
        throw new UnsupportedOperationException("Not implemented");
    }

    public void writeRaw(char[] text, int offset, int length)
        throws XMLStreamException
    {
        writeRaw(new String(text, offset, length));
    }

    public void copyEventFromReader(XMLStreamReader2 sr, boolean preserveEventData)
        throws XMLStreamException
    {
        switch (sr.getEventType()) {
        case START_DOCUMENT:
            {
                String version = sr.getVersion();
                /* No real declaration? If so, we don't want to output
                 * anything, to replicate as closely as possible the
                 * source document
                 */
                if (version == null || version.length() == 0) {
                    ; // no output if no real input
                } else {
                    if (sr.standaloneSet()) {
                        writeStartDocument(sr.getVersion(),
                                           sr.getCharacterEncodingScheme(),
                                           sr.isStandalone());
                    } else {
                        writeStartDocument(sr.getCharacterEncodingScheme(),
                                           sr.getVersion());
                    }
                }
            }
            return;
            
        case END_DOCUMENT:
            writeEndDocument();
            return;
            
            // Element start/end events:
        case START_ELEMENT:
            /* Start element is bit trickier to output since there
             * may be differences between repairing/non-repairing
             * writers. But let's try a generic handling here.
             */
            copyStartElement(sr);
            return;
            
        case END_ELEMENT:
            writeEndElement();
            return;
            
        case SPACE:
            writeSpace(sr.getTextCharacters(), sr.getTextStart(), sr.getTextLength());
            return;
            
        case CDATA:
            writeCData(sr.getTextCharacters(), sr.getTextStart(), sr.getTextLength());
            return;
            
        case CHARACTERS:
            writeCharacters(sr.getTextCharacters(), sr.getTextStart(), sr.getTextLength());
            return;
            
        case COMMENT:
            writeComment(sr.getText());
            return;
            
        case PROCESSING_INSTRUCTION:
            writeProcessingInstruction(sr.getPITarget(), sr.getPIData());
            return;
            
        case DTD:
            {
                DTDInfo info = sr.getDTDInfo();
                if (info == null) {
                    /* Hmmmh. Can this happen for non-DTD-aware readers?
                     * And if so, what should we do?
                     */
                    throw new XMLStreamException("Current state DOCTYPE, but not DTDInfo Object returned -- reader doesn't support DTDs?");
                }
                writeDTD(info.getDTDRootName(), info.getDTDSystemId(),
                         info.getDTDPublicId(), info.getDTDInternalSubset());
            }
            return;
            
        case ENTITY_REFERENCE:
            writeEntityRef(sr.getLocalName());
            return;
            
        case ATTRIBUTE:
        case NAMESPACE:
        case ENTITY_DECLARATION:
        case NOTATION_DECLARATION:
            // Let's just fall back to throw the exception
        }
        throw new XMLStreamException("Unrecognized event type ("
                                     +sr.getEventType()+"); not sure how to copy");
    }

    /*
    ///////////////////////////////
    // Stax2, validation
    ///////////////////////////////
    */

    public XMLValidator validateAgainst(XMLValidationSchema schema)
        throws XMLStreamException
    {
        // !!! TODO: try to implement?
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public XMLValidator stopValidatingAgainst(XMLValidationSchema schema)
        throws XMLStreamException
    {
        return null;
    }

    public XMLValidator stopValidatingAgainst(XMLValidator validator)
        throws XMLStreamException
    {
        return null;
    }

    public ValidationProblemHandler setValidationProblemHandler(ValidationProblemHandler h)
    {
        /* Not a real problem: although we can't do anything with it
         * (without real validator integration)
         */
        return null;
    }

    /*
    ///////////////////////////////
    // Helper methods
    ///////////////////////////////
    */

    protected void copyStartElement(XMLStreamReader sr)
        throws XMLStreamException
    {
        // Any namespace declarations/bindings?
        int nsCount = sr.getNamespaceCount();
        if (nsCount > 0) { // yup, got some...
            /* First, need to (or at least, should?) add prefix bindings:
             * (may not be 100% required, but probably a good thing to do,
             * just so that app code has access to prefixes then)
             */
            for (int i = 0; i < nsCount; ++i) {
                String prefix = sr.getNamespacePrefix(i);
                String uri = sr.getNamespaceURI(i);
                if (prefix == null || prefix.length() == 0) { // default NS
                    setDefaultNamespace(uri);
                } else {
                    setPrefix(prefix, uri);
                }
            }
        }
        writeStartElement(sr.getPrefix(), sr.getLocalName(), sr.getNamespaceURI());
        
        if (nsCount > 0) {
            // And then output actual namespace declarations:
            for (int i = 0; i < nsCount; ++i) {
                String prefix = sr.getNamespacePrefix(i);
                String uri = sr.getNamespaceURI(i);
                
                if (prefix == null || prefix.length() == 0) { // default NS
                    writeDefaultNamespace(uri);
                } else {
                    writeNamespace(prefix, uri);
                }
            }
        }
        
        /* And then let's just output attributes. But should we copy the
         * implicit attributes (created via attribute defaulting?)
         */
        int attrCount = sr.getAttributeCount();
        if (attrCount > 0) {
            for (int i = 0; i < attrCount; ++i) {
                writeAttribute(sr.getAttributePrefix(i),
                               sr.getAttributeNamespace(i),
                               sr.getAttributeLocalName(i),
                               sr.getAttributeValue(i));
            }
        }
    }
}
