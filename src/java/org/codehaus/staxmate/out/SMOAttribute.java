package org.codehaus.staxmate.out;

import javax.xml.stream.XMLStreamException;

/**
 * Simple container class for storing definition of a buffered
 * element attribute.
 */
public class SMOAttribute
    extends SMSimpleOutput
{
    final SMNamespace _namespace;
    final String _localName;
    final String _value;

    public SMOAttribute(SMNamespace namespace, String localName, String value)
    {
        _namespace = namespace;
        _localName = localName;
        _value = value;
    }

    protected boolean doOutput(SMOutputContext ctxt, boolean canClose)
        throws XMLStreamException
    {
        ctxt.writeAttribute(_localName, _namespace, _value);
        return true;
    }
}
