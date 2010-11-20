package org.codehaus.staxmate.in;

import java.io.StringReader;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;

import org.codehaus.stax2.XMLStreamReader2;

public class TestReadingFor37 extends ReaderTestBase
{
    public void testStaxMate37WithNative() throws Exception
    {
        _testStaxMate37(false);
    }

    public void testStaxMate37WithWrapper() throws Exception
    {
        _testStaxMate37(true);
    }
    
    /*
    ////////////////////////////////////////////////////
    // Actual tests
    ////////////////////////////////////////////////////
    */
    
    public void _testStaxMate37(boolean wrap) throws Exception
    {
        XMLInputFactory sf = getStaxInputFactory();
        String XML = "<root><a>xyz</a><b>abc</b></root>";
        XMLStreamReader sr = sf.createXMLStreamReader(new StringReader(XML));
        XMLStreamReader2 sr2;
        if (wrap || !(sr instanceof XMLStreamReader2)) {
            sr2 = forceWrapping(sr);
        } else {
            sr2 = (XMLStreamReader2) sr;
        }
        SMInputContext ctxt = new SMInputContext(sr2);
        SMInputCursor c = new SMHierarchicCursor(ctxt, null, SMFilterFactory.getElementOnlyFilter());
        c = c.advance().childElementCursor().advance();
//        SMInputCursor c = sf.rootElementCursor(sr).advance().childElementCursor().advance();
        assertEquals(SMEvent.START_ELEMENT, c.getCurrEvent());
        assertEquals("a", c.getLocalName());
        assertEquals("xyz", c.getElemStringValue());
        assertEquals(SMEvent.START_ELEMENT, c.getNext());
        assertEquals("b", c.getLocalName());
        assertNull(c.getNext());
    }
}
