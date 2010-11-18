package org.codehaus.staxmate.in;

import java.io.StringReader;

import javax.xml.stream.XMLInputFactory;

import org.codehaus.staxmate.SMInputFactory;

/**
 * 
 * @author tatu
 *
 */
public class TestReadingFor37 extends ReaderTestBase
{
    /*
    @Override
    protected XMLInputFactory getStaxInputFactory() {
        try {
            return (XMLInputFactory) Class.forName("com.sun.xml.internal.stream.XMLInputFactoryImpl").newInstance();
        } catch (Exception e) {
            fail("Unexpected problem: "+e);
            return null;
        }
    }
    */

    /*
    ////////////////////////////////////////////////////
    // Actual tests
    ////////////////////////////////////////////////////
    */
    
    public void testStaxMate37() throws Exception
    {
        SMInputFactory sf = getInputFactory();
        String XML = "<root><a>xyz</a><b>abc</b></root>";
        SMInputCursor c = sf.rootElementCursor(new StringReader(XML)).advance().childElementCursor().advance();
        assertEquals("a", c.getLocalName());
        assertEquals("xyz", c.getElemStringValue());
        assertEquals(SMEvent.START_ELEMENT, c.getNext());
        assertEquals("b", c.getLocalName());
        assertNull(c.getNext());
    }
}
