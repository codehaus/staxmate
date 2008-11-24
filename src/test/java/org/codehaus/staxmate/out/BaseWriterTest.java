package org.codehaus.staxmate.out;

import java.io.*;

import javax.xml.stream.*;

import org.codehaus.staxmate.*;

class BaseWriterTest
    extends org.codehaus.staxmate.BaseTest
{
    protected XMLStreamWriter getSimpleWriter(Writer w)
        throws XMLStreamException
    {
        XMLOutputFactory f = XMLOutputFactory.newInstance();
        return f.createXMLStreamWriter(w);
    }

    protected SMOutputDocument createSimpleDoc(Writer w)
        throws XMLStreamException
    {
        XMLOutputFactory f = XMLOutputFactory.newInstance();
        SMOutputFactory smo = new SMOutputFactory(f);
        return smo.createOutputDocument(w);
    }
}
