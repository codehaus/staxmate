package org.codehaus.staxmate.util;

import javax.xml.namespace.NamespaceContext;
import javax.xml.stream.*;

import org.codehaus.stax2.*;
import org.codehaus.stax2.validation.XMLValidationSchema;
import org.codehaus.stax2.validation.XMLValidator;

/**
 * This adapter implements parts of {@link XMLStreamWriter2}, the
 * extended stream reader defined by Stax2 extension, by wrapping
 * a vanilla Stax 1.0 {@link XMLStreamWriter} implementation.
 */
public final class Stax2WriterAdapter
    //extends StreamWriterDelegate // such a thing doesn't exist...
    implements XMLStreamWriter2 /* From Stax2 */
{
    /**
     * Underlying Stax 1.0 compliant stream writer.
     */
    final XMLStreamWriter mDelegate;

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

    public Location getLocation()
    {
        // No easy way to keep track of it...
        return null;
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
        StringBuilder sb = new StringBuilder();
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
        // There is no clean way to implement this via Stax 1.0, alas...

        throw new UnsupportedOperationException("Not implemented");
    }

    public void writeRaw(char[] text, int offset, int length)
        throws XMLStreamException
    {
        writeRaw(new String(text, offset, length));
    }

    public void copyEventFromReader(XMLStreamReader2 r, boolean preserveEventData)
        throws XMLStreamException
    {
        // !!! TODO: implement...
        throw new UnsupportedOperationException("Not yet implemented");
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
}
