package staxmate.in;

import java.io.*;

import javax.xml.stream.*;

import org.codehaus.staxmate.SMInputFactory;
import org.codehaus.staxmate.in.*;

/**
 * Basic unit tests for verifying that traversal using nested cursors
 * works as expected
 */
public class TestNested
    extends BaseReaderTest
{
    public void testSimpleTwoLevel()
        throws Exception
    {
        String XML = "<?xml version='1.0'?>"
            +"<root>\n"
            +"<leaf />"
            +"<leaf attr='xyz'>text</leaf>"
            +"</root>\n";
        XMLStreamReader sr = XMLInputFactory.newInstance().createXMLStreamReader(new StringReader(XML));
        SMInputCursor rootc = SMInputFactory.rootElementCursor(sr);
        assertEquals(0, rootc.getParentCount());
        assertEquals(SMEvent.START_ELEMENT, rootc.getNext()); // should always have root
        assertEquals("root", rootc.getLocalName());
        assertEquals(0, rootc.getParentCount());
        SMInputCursor leafc = rootc.childElementCursor();

        assertEquals(1, leafc.getParentCount());
        assertEquals(SMEvent.START_ELEMENT, leafc.getNext());
        assertEquals("leaf", leafc.getLocalName());
        assertEquals(1, leafc.getParentCount());

        assertEquals(SMEvent.START_ELEMENT, leafc.getNext());
        assertEquals(1, leafc.getParentCount());
        assertEquals("leaf", leafc.getLocalName());
        assertEquals(1, leafc.getAttrCount());
        assertEquals("attr", leafc.getAttrLocalName(0));
        assertEquals("xyz", leafc.getAttrValue(0));

        assertEquals("text", leafc.collectDescendantText(true));

        assertNull(leafc.getNext());

        assertNull(rootc.getNext());
        
        sr.close();
    }

    public void testSimpleThreeLevel()
        throws XMLStreamException
    {
        String XML =
            /*
            "<root name='root'>"
            +"<branch name='br1'>"
            +"<leaf name='leaf1'>text</leaf>"
            +"<leaf name='leaf2'>text2</leaf>"
            +"</branch>"
            +"</root>"
            */
"<?xml version='1.0' encoding='UTF-8'?>\n"
+"<root name='123' xyx='abc' attr='!'>\n"
+"<pt name='...'><prop name='a'>Authority Non Buyable</prop><prop name='b'>Authority Non Buyable</prop><prop name='c'>false</prop></pt>\n"
+"<pt name='pt2'><prop name='1'>Apparel</prop><prop name='2'>Apparel</prop></pt>\n"
+"</root>"
            ;

        XMLStreamReader sr = XMLInputFactory.newInstance().createXMLStreamReader(new StringReader(XML));
        SMInputCursor rootc = SMInputFactory.rootElementCursor(sr);

        assertEquals(SMEvent.START_ELEMENT, rootc.getNext()); // should always have root
        assertEquals("root", rootc.getLocalName());
        assertEquals(3, rootc.getAttrCount());
        assertEquals(0, rootc.getParentCount());

        SMInputCursor brc = rootc.childElementCursor();
        assertEquals(SMEvent.START_ELEMENT, brc.getNext());
        assertEquals("pt", brc.getLocalName());
        assertEquals(1, brc.getAttrCount());
        assertEquals(1, brc.getParentCount());

        SMInputCursor leafc = brc.childElementCursor();
        assertEquals(SMEvent.START_ELEMENT, leafc.getNext());
        assertEquals(2, leafc.getParentCount());
        assertEquals("prop", leafc.getLocalName());
        assertEquals(1, leafc.getAttrCount());
        assertEquals("Authority Non Buyable", leafc.collectDescendantText(false));

        assertEquals(SMEvent.START_ELEMENT, leafc.getNext());
        assertEquals("prop", leafc.getLocalName());

        assertEquals(SMEvent.START_ELEMENT, leafc.getNext());
        assertEquals("prop", leafc.getLocalName());

        assertNull(leafc.getNext());

        // Enough, let's move to the next at branch level:
        assertEquals(SMEvent.START_ELEMENT, brc.getNext());
        assertEquals("pt", brc.getLocalName());

        // And then check that root is done:

        assertNull(rootc.getNext());
        
        sr.close();
    }

    /**
     * This a complementary test, and checks, to verify against regression
     * in hierarchic cursor synchronization.
     */
    public void testThreeLevel2()
        throws XMLStreamException
    {
        String XML =
"<?xml version='1.0' encoding='UTF-8'?>\n"
+"<root name='123' xyx='abc' attr='!'>\n"
+"<pt name='...'><prop name='a'>Authority Non Buyable</prop><prop name='b'>Authority Non Buyable</prop><prop name='c'>false</prop></pt>\n"
+"<pt name='pt2'><prop name='1'>Apparel</prop><prop name='2'>Apparel</prop></pt>\n"
+"</root>"
            ;
        XMLStreamReader sr = XMLInputFactory.newInstance().createXMLStreamReader(new StringReader(XML));
        SMInputCursor rootc = SMInputFactory.rootElementCursor(sr);
        rootc.getNext(); // should always have root
        assertEquals("root", rootc.getLocalName()); 
        assertEquals(0, rootc.getParentCount());
        SMInputCursor ptCursor = rootc.childElementCursor();
        while (ptCursor.getNext() != null) {
            assertEquals(1, ptCursor.getParentCount());
            assertEquals("pt", ptCursor.getLocalName());
            assertNotNull(ptCursor.getAttrValue("name"));
            SMInputCursor propCursor = ptCursor.childElementCursor();
            while (propCursor.getNext() != null) {
                assertEquals(2, propCursor.getParentCount());
                assertEquals("prop", propCursor.getLocalName());
                String propName = propCursor.getAttrValue("name");
                assertNotNull(propName);
                String value = propCursor.collectDescendantText(false);
                assertNotNull(value);
            }
        }
        sr.close();
    }
}
