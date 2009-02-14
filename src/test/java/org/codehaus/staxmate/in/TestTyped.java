package org.codehaus.staxmate.in;

import java.io.*;

import javax.xml.stream.*;

import org.codehaus.staxmate.SMInputFactory;

/**
 * Basic unit tests to verify that typed access methods work as expected.
 *
 * @author Tatu Saloranta
 */
public class TestTyped
    extends BaseReaderTest
{
    /*
    ////////////////////////////////////////////////////
    // Tests for typed attributes
    ////////////////////////////////////////////////////
    */

    public void testTypedBooleanAttr()
        throws XMLStreamException
    {
        SMInputFactory sf = new SMInputFactory(XMLInputFactory.newInstance());
        String XML = "<root attr='true' attr2='1' attr3='' />";
        SMInputCursor rootc = sf.rootElementCursor(new StringReader(XML)).advance();
        assertEquals("root", rootc.getLocalName());
        assertTrue(rootc.getAttrBooleanValue(0));
        // as per XML Schema, '0' and '1' are valid too
        assertTrue(rootc.getAttrBooleanValue(1));
        // empty is not, but works with defaults:
        assertTrue(rootc.getAttrBooleanValue(2, true));
    }

    public void testTypedIntAttr()
        throws XMLStreamException
    {
        SMInputFactory sf = new SMInputFactory(XMLInputFactory.newInstance());
        String XML = "<root attr='-37' attr2='foobar' />";
        SMInputCursor rootc = sf.rootElementCursor(new StringReader(XML)).advance();
        assertEquals("root", rootc.getLocalName());
        assertEquals(-37, rootc.getAttrIntValue(0));
        // and then default
        assertEquals(13, rootc.getAttrIntValue(1, 13));
    }

    public void testTypedLongAttr()
        throws XMLStreamException
    {
        SMInputFactory sf = new SMInputFactory(XMLInputFactory.newInstance());
        String XML = "<root attr='-37' attr2='' />";
        SMInputCursor rootc = sf.rootElementCursor(new StringReader(XML)).advance();
        assertEquals("root", rootc.getLocalName());
        assertEquals(-37L, rootc.getAttrLongValue(0));
        // and then default
        assertEquals(13L, rootc.getAttrLongValue(1, 13L));
    }

    public void testTypedDoubleAttr()
        throws XMLStreamException
    {
        SMInputFactory sf = new SMInputFactory(XMLInputFactory.newInstance());
        String XML = "<root attr='-0.1' attr2='' />";
        SMInputCursor rootc = sf.rootElementCursor(new StringReader(XML)).advance();
        assertEquals("root", rootc.getLocalName());
        assertEquals(-0.1, rootc.getAttrDoubleValue(0));
        // and then default
        assertEquals(0.25, rootc.getAttrDoubleValue(1, 0.25));
    }

    /*
    ////////////////////////////////////////////////////
    // Simple tests for typed elements
    ////////////////////////////////////////////////////
    */

    public void testTypedBooleanElem()
        throws XMLStreamException
    {
        SMInputFactory sf = new SMInputFactory(XMLInputFactory.newInstance());
        String XML = "<root><a>true</a><b>   0 </b><c>...</c></root>";
        SMInputCursor rootc = sf.rootElementCursor(new StringReader(XML)).advance();
        assertEquals("root", rootc.getLocalName());
        SMInputCursor crsr = rootc.childElementCursor().advance();
        assertEquals("a", crsr.getLocalName());
        assertTrue(crsr.getElemBooleanValue());
        assertNotNull(crsr.getNext());
        assertEquals("b", crsr.getLocalName());
        assertFalse(crsr.getElemBooleanValue());
        assertNotNull(crsr.getNext());
        assertEquals("c", crsr.getLocalName());
        // this would fail, if not for default:
        assertTrue(crsr.getElemBooleanValue(true));
        assertNull(crsr.getNext());
    }

    /*
    ////////////////////////////////////////////////////
    // And then tests to see that traversal works
    ////////////////////////////////////////////////////
    */
}
