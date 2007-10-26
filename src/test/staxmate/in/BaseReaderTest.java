package staxmate.in;

import java.io.*;

import javax.xml.stream.*;

import junit.framework.TestCase;

import org.codehaus.staxmate.in.SMInputCursor;

class BaseReaderTest
    extends TestCase
{
    protected void assertElem(SMInputCursor crsr, String expURI, String expLN)
        throws XMLStreamException
    {
        assertEquals(expLN, crsr.getLocalName());
        assertTrue(crsr.hasLocalName(expLN));
        assertTrue(crsr.hasName(expURI, expLN));

        String uri = crsr.getNsUri();
        if (expURI == null) {
            if (uri != null && uri.length() > 0) {
                fail("Expected element to have no namespace, got '"+uri+"'");
            }
        } else {
            if (!expURI.equals(uri)) {
                fail("Expected element to have non-empty namespace '"+expURI+"', got '"+uri+"'");
            }
        }
    }

    protected XMLStreamReader getCoalescingReader(String content)
        throws XMLStreamException
    {
        XMLInputFactory f = XMLInputFactory.newInstance();
        f.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);
        return f.createXMLStreamReader(new StringReader(content));
    }
}
