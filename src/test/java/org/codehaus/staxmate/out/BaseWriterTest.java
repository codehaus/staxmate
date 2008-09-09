package org.codehaus.staxmate.out;

import java.io.*;

import javax.xml.stream.*;

class BaseWriterTest
    extends org.codehaus.staxmate.BaseTest
{
    protected XMLStreamWriter getSimpleWriter(Writer w)
        throws XMLStreamException
    {
        XMLOutputFactory f = XMLOutputFactory.newInstance();
        return f.createXMLStreamWriter(w);
    }
}
