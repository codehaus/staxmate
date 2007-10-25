package staxmate.in;

import java.io.*;

import javax.xml.stream.*;

import junit.framework.TestCase;

class BaseReaderTest
    extends TestCase
{
    protected XMLStreamReader getCoalescingReader(String content)
        throws XMLStreamException
    {
        XMLInputFactory f = XMLInputFactory.newInstance();
        f.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);
        return f.createXMLStreamReader(new StringReader(content));
    }
}
