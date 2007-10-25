package staxmate.in;

import java.io.*;

import javax.xml.stream.*;

import org.codehaus.staxmate.SMInputFactory;
import org.codehaus.staxmate.in.*;

/**
 * Basic unit tests for verifying that traversal using flattening
 * (non-nested) cursors works as expected
 */
public class TestFlattening
    extends BaseReaderTest
{
    public void testTwoLevelMixed()
        throws Exception
    {
        String XML = "<?xml version='1.0'?>"
            +"<root>\n"
            +"<leaf />"
            +"<leaf attr='xyz'>R&amp;b</leaf>"
            +"</root>";
        XMLStreamReader sr = getCoalescingReader(XML);
        SMInputCursor rootc = SMInputFactory.rootElementCursor(sr);
        assertEquals(SMEvent.START_ELEMENT, rootc.getNext());
        assertEquals(1, rootc.getParentCount());
        SMInputCursor leafc = rootc.descendantCursor();
        assertEquals(1, leafc.getParentCount());

        assertEquals(SMEvent.TEXT, leafc.getNext());
        assertEquals("\n", leafc.getText());
        assertEquals(1, leafc.getParentCount());

        assertEquals(SMEvent.START_ELEMENT, leafc.getNext());
        assertEquals("leaf", leafc.getLocalName());
        //        assertEquals(1, leafc.getParentCount());
        assertEquals(SMEvent.END_ELEMENT, leafc.getNext());
        assertEquals("leaf", leafc.getLocalName());
        //assertEquals(1, leafc.getParentCount());

        assertEquals(SMEvent.START_ELEMENT, leafc.getNext());
        assertEquals("leaf", leafc.getLocalName());
        //assertEquals(1, leafc.getParentCount());
        assertEquals(SMEvent.TEXT, leafc.getNext());
        assertEquals("R&b", leafc.getText());
        //assertEquals(2, leafc.getParentCount());
        assertEquals(SMEvent.END_ELEMENT, leafc.getNext());
        assertEquals("leaf", leafc.getLocalName());
        assertEquals(1, leafc.getParentCount());

        assertNull(leafc.getNext());

        assertNull(rootc.getNext());
        
        sr.close();
    }
}

