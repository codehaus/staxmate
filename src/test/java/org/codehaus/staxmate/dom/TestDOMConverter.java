package org.codehaus.staxmate.dom;

import java.io.*;

import javax.xml.parsers.*;
import javax.xml.stream.*;
import static javax.xml.stream.XMLStreamConstants.*;

import org.w3c.dom.*;

import org.codehaus.staxmate.BaseTest;

public class TestDOMConverter
    extends BaseTest
{
    /**
     * Unit test that verifies that a proper DOM tree can be constructed
     * from Stax stream reader.
     */
    public void testDOMReadFromStax()
        throws XMLStreamException
    {
        String XML =
            "<root>abc<?proc    instr?>"
            +"<leaf xmlns:a='http://foo' attr='3' a:b='c' /></root>"
            +"<!--comment-stuff-->"
            ;
        DOMConverter conv = new DOMConverter();
        XMLStreamReader sr = getCoalescingReader(XML);
        Document doc = conv.buildDocument(sr);
        assertNotNull(doc);
        Node root = doc.getFirstChild();
        assertNotNull(root);
        // should be <root> elem
        assertEquals(Node.ELEMENT_NODE, root.getNodeType());
        assertEquals("root", root.getNodeName());

        // First, let's check stuff beyond root
        Node n = root.getNextSibling();
        assertEquals(Node.COMMENT_NODE, n.getNodeType());
        assertEquals("comment-stuff", n.getNodeValue());

        // Then children of root

        n = root.getFirstChild();
        assertEquals(Node.TEXT_NODE, n.getNodeType());
        assertEquals("abc", n.getNodeValue());
        n = n.getNextSibling();
        assertEquals(Node.PROCESSING_INSTRUCTION_NODE, n.getNodeType());
        assertEquals("proc", n.getNodeName());
        assertEquals("instr", n.getNodeValue());
        n = n.getNextSibling();
        assertEquals(Node.ELEMENT_NODE, n.getNodeType());
        assertEquals("leaf", n.getNodeName());
        assertEmpty(n.getNamespaceURI());

        assertNull(n.getFirstChild());
        NamedNodeMap attrs = n.getAttributes();
        assertEquals(3, attrs.getLength());

        Attr a = (Attr) attrs.getNamedItem("attr");
        assertNotNull(a);
        assertEquals("attr", a.getName());
        assertEmpty(a.getNamespaceURI());
        assertEquals("3", a.getValue());

        a = (Attr) attrs.getNamedItem("xmlns:a");
        assertNotNull(a);
        assertEquals("xmlns:a", a.getName());
        assertEquals("http://foo", a.getValue());

        a = (Attr) attrs.getNamedItemNS("http://foo", "b");
        assertNotNull(a);
        assertEquals("a:b", a.getName());
        assertEquals("a", a.getPrefix());
        assertEquals("b", a.getLocalName());
        assertEquals("http://foo", a.getNamespaceURI());
        assertEquals("c", a.getValue());

        assertNull(n.getNextSibling());
    }

    public void testDOMWrittenUsingStax()
        throws Exception
    {
        String XML =
            "<root>abc<?proc instr?><!--comment-stuff-->"
            +"<leaf xmlns:a='http://foo' attr='3' a:b='c' /></root>";
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        InputStream in = new ByteArrayInputStream(XML.getBytes("UTF-8"));
        Document doc = db.parse(in);

        StringWriter sw = new StringWriter(XML.length() + 16);
        DOMConverter conv = new DOMConverter();
        XMLStreamWriter strw = getSimpleWriter(sw);
        conv.writeDocument(doc, strw);

        String docStr = sw.toString();

        System.err.println("DOC = '"+docStr+"'");


        XMLStreamReader sr = getCoalescingReader(docStr);
        assertTokenType(START_ELEMENT, sr.next());

        assertTokenType(CHARACTERS, sr.next());
        assertTokenType(PROCESSING_INSTRUCTION, sr.next());
        assertTokenType(COMMENT, sr.next());

        assertTokenType(START_ELEMENT, sr.next());
        assertTokenType(END_ELEMENT, sr.next());

        assertTokenType(END_ELEMENT, sr.next());

        assertTokenType(END_DOCUMENT, sr.next());
    }


    private void assertEmpty(String str)
    {
        assertTrue((str == null) || str.length() == 0);
    }
}
