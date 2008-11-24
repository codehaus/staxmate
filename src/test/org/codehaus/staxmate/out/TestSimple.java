package org.codehaus.staxmate.out;

import java.io.*;

import javax.xml.stream.*;
import static javax.xml.stream.XMLStreamConstants.*;

import org.codehaus.staxmate.SMOutputFactory;

public class TestSimple
    extends BaseWriterTest
{
    /**
     * Simple test to verify namespace bindings.
     */
    public void testSimpleNS()
        throws Exception
    {
        StringWriter sw = new StringWriter();
        XMLStreamWriter xw = getSimpleWriter(sw);
        SMOutputDocument doc = SMOutputFactory.createOutputDocument(xw);

        final String NS_URI = "http://foo";
        SMNamespace ns = doc.getNamespace(NS_URI);

        SMOutputElement elem = doc.addElement("root"); // no NS
        elem.addElement(ns, "leaf1");
        elem.addElement(ns, "leaf2");
        doc.closeRoot();
 
        // Ok let's verify, then using just plain old Stax
        XMLStreamReader sr = getCoalescingReader(sw.toString());
        assertTokenType(START_ELEMENT, sr.next());
        assertElem(sr, null, "root");

        assertTokenType(START_ELEMENT, sr.next());
        assertElem(sr, NS_URI, "leaf1");
        assertTokenType(END_ELEMENT, sr.next());
        assertTokenType(START_ELEMENT, sr.next());
        assertElem(sr, NS_URI, "leaf2");
        assertTokenType(END_ELEMENT, sr.next());

        assertTokenType(END_ELEMENT, sr.next());

        sr.close();
    }

    /**
     * Another namespace binding test; this time checking
     * whether use of optional prefix argument might confuse writer.
     */
    public void testPrefixedNS()
        throws Exception
    {
        StringWriter sw = new StringWriter();
        XMLStreamWriter xw = getSimpleWriter(sw);
        SMOutputDocument doc = SMOutputFactory.createOutputDocument(xw);

        final String NS_URI1 = "http://foo";
        SMNamespace ns1 = doc.getNamespace(NS_URI1);
        final String NS_URI2 = "http://bar";
        final String NS_PREFIX2 = "pr";
        SMNamespace ns2 = doc.getNamespace(NS_URI2, NS_PREFIX2);

        SMOutputElement elem = doc.addElement("root"); // no NS
        elem.addElement(ns1, "leaf1");
        elem.addElement(ns2, "leaf2");
        doc.closeRoot();
 
        // Ok let's verify using just plain old Stax
        XMLStreamReader sr = getCoalescingReader(sw.toString());
        assertTokenType(START_ELEMENT, sr.next());
        assertElem(sr, null, "root");

        assertTokenType(START_ELEMENT, sr.next());
        assertElem(sr, NS_URI1, "leaf1");
        assertTokenType(END_ELEMENT, sr.next());
        assertTokenType(START_ELEMENT, sr.next());
        assertElem(sr, NS_URI2, "leaf2");
        assertEquals(NS_PREFIX2, sr.getPrefix());
        assertTokenType(END_ELEMENT, sr.next());

        assertTokenType(END_ELEMENT, sr.next());

        sr.close();
    }

    /**
     * Unit test for checking fix for [STAXMATE-20], incorrect
     * scoping for non-default namespaces.
     */
    public void testPrefixedNS2()
        throws Exception
    {
        StringWriter sw = new StringWriter();
        XMLStreamWriter xw = getSimpleWriter(sw);
        SMOutputDocument doc = SMOutputFactory.createOutputDocument(xw);

        final String NS_PREFIX = "pr";
        final String NS_URI = "http://foo";
        SMNamespace ns = doc.getNamespace(NS_URI, NS_PREFIX);

        SMOutputElement elem = doc.addElement("root"); // no NS
        elem.addElement(ns, "leaf1");
        elem.addElement(ns, "leaf2");
        doc.closeRoot();
 
        // Ok let's verify using just plain old Stax
        XMLStreamReader sr = getCoalescingReader(sw.toString());
        assertTokenType(START_ELEMENT, sr.next());
        assertElem(sr, null, "root");

        assertTokenType(START_ELEMENT, sr.next());
        assertElem(sr, NS_URI, "leaf1");
        assertEquals(NS_PREFIX, sr.getPrefix());
        assertTokenType(END_ELEMENT, sr.next());

        assertTokenType(START_ELEMENT, sr.next());
        assertElem(sr, NS_URI, "leaf2");
        assertEquals(NS_PREFIX, sr.getPrefix());
        assertTokenType(END_ELEMENT, sr.next());

        assertTokenType(END_ELEMENT, sr.next());

        sr.close();
    }

    /**
     * Unit test for verifying that attribute-namespace binding
     * works correctly, distinct from handling of element namespaces.
     */
    public void testAttrNS2()
        throws Exception
    {
        StringWriter sw = new StringWriter();
        XMLStreamWriter xw = getSimpleWriter(sw);
        SMOutputDocument doc = SMOutputFactory.createOutputDocument(xw);

        final String NS_URI = "http://foo";
        // this should create default namespace
        SMNamespace ns = doc.getNamespace(NS_URI);

        SMOutputElement elem = doc.addElement(ns, "root");
        /* Note: attributes can NOT use default namespace, must generate
         * additional binding.
         */
        elem.addAttribute(ns, "attr", "value");
        doc.closeRoot();
 
        // Ok let's verify using just plain old Stax
        XMLStreamReader sr = getCoalescingReader(sw.toString());
        assertTokenType(START_ELEMENT, sr.next());
        assertElem(sr, NS_URI, "root");

        assertEquals("attr", sr.getAttributeLocalName(0));
        assertEquals("value", sr.getAttributeValue(0));
        assertEquals(NS_URI, sr.getAttributeNamespace(0));

        assertTokenType(END_ELEMENT, sr.next());

        sr.close();
    }

    /**
     * Unit test for verifying that buffered output works ok
     */
    public void testBuffered()
        throws Exception
    {
        StringWriter sw = new StringWriter();
        XMLStreamWriter xw = getSimpleWriter(sw);
        SMOutputDocument doc = SMOutputFactory.createOutputDocument(xw);

        final String NS_URI1 = "http://foo";
        final String NS_PREFIX2 = "myns";
        final String NS_URI2 = "urn://hihhei";

        SMNamespace ns1 = doc.getNamespace(NS_URI1);
        SMNamespace ns2 = doc.getNamespace(NS_URI2, NS_PREFIX2);

        final String COMMENT_CONTENT = "Comment!";
        doc.addComment(COMMENT_CONTENT);

        SMOutputElement elem = doc.addElement(ns1, "root");
        final String TEXT_CONTENT1 = "Rock & Roll";
        elem.addCharacters(TEXT_CONTENT1);
        SMBufferedFragment frag = elem.createBufferedFragment();
        elem.addBuffered(frag);
        final String TEXT_CONTENT2 = "[FRAG";
        frag.addCharacters(TEXT_CONTENT2);
        final String COMMENT_CONTENT2 = "!!!";
        frag.addComment(COMMENT_CONTENT2);
        frag.addElement(ns1, "tag");
        SMOutputElement elem2 = elem.addElement("branch");
        elem2.addElement(ns2, "leaf");
        final String TEXT_CONTENT3 = "ment!]";
        frag.addCharacters(TEXT_CONTENT3);
        frag.release();
        elem.addElement(ns2, "leaf2");
        doc.closeRoot();

        // Uncomment for debugging:
        //System.out.println("Result:");
        //System.out.println(sw.toString());
 
        // Ok let's verify, then:
        XMLStreamReader sr = getCoalescingReader(sw.toString());
        // but just using plain old Stax...

        assertTokenType(COMMENT, sr.next());
        assertEquals(COMMENT_CONTENT, sr.getText());

        assertTokenType(START_ELEMENT, sr.next());
        assertElem(sr, NS_URI1, "root");

        assertTokenType(CHARACTERS, sr.next());
        assertEquals(TEXT_CONTENT1 + TEXT_CONTENT2, sr.getText());

        assertTokenType(COMMENT, sr.next());
        assertEquals(COMMENT_CONTENT2, sr.getText());

        assertTokenType(START_ELEMENT, sr.next());
        assertElem(sr, NS_URI1, "tag");
        assertTokenType(END_ELEMENT, sr.next());
        assertElem(sr, NS_URI1, "tag");
        assertTokenType(CHARACTERS, sr.next());
        assertEquals(TEXT_CONTENT3, sr.getText());

        assertTokenType(START_ELEMENT, sr.next());
        assertElem(sr, null, "branch");
        assertTokenType(START_ELEMENT, sr.next());
        assertElem(sr, NS_URI2, "leaf");
        // ideally will also use the prefix passed?
        assertEquals(NS_PREFIX2, sr.getPrefix());
        assertTokenType(END_ELEMENT, sr.next());
        assertElem(sr, NS_URI2, "leaf");
        assertTokenType(END_ELEMENT, sr.next());
        assertElem(sr, null, "branch");

        assertTokenType(START_ELEMENT, sr.next());
        assertElem(sr, NS_URI2, "leaf2");
        assertTokenType(END_ELEMENT, sr.next());
        assertElem(sr, NS_URI2, "leaf2");

        assertTokenType(END_ELEMENT, sr.next());
        assertElem(sr, NS_URI1, "root");

        sr.close();
   }

    /**
     * Even simpler unit test that verifies that it is ok to pass
     * null namespaces
     */
    public void testBufferedNoNs()
        throws Exception
    {
        StringWriter sw = new StringWriter();
        XMLStreamWriter xw = getSimpleWriter(sw);
        SMOutputDocument doc = SMOutputFactory.createOutputDocument(xw);

        SMOutputElement elem = doc.addElement("root");
        SMBufferedElement leafFrag = elem.createBufferedElement(null, "leaf");
        elem.addAndReleaseBuffered(leafFrag);
        doc.closeRoot();

        // Ok let's verify, then:
        XMLStreamReader sr = getCoalescingReader(sw.toString());
        // but just using plain old Stax...
        assertTokenType(START_ELEMENT, sr.next());
        assertElem(sr, null, "root");
        assertEquals(0, sr.getAttributeCount());
        assertEquals(0, sr.getNamespaceCount());
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals(0, sr.getAttributeCount());
        assertEquals(0, sr.getNamespaceCount());
        assertElem(sr, null, "leaf");
        assertTokenType(END_ELEMENT, sr.next());
        assertTokenType(END_ELEMENT, sr.next());
        assertTokenType(END_DOCUMENT, sr.next());
        sr.close();
    }

    public void testBufferedWithAttr()
        throws Exception
    {
        StringWriter sw = new StringWriter();
        XMLStreamWriter xw = getSimpleWriter(sw);
        SMOutputDocument doc = SMOutputFactory.createOutputDocument(xw);

        SMOutputElement elem = doc.addElement("root");
        SMBufferedElement leafFrag = elem.createBufferedElement(null, "leaf");
        leafFrag.addAttribute("attr", "value");
        elem.addAndReleaseBuffered(leafFrag);
        doc.closeRoot();

        // Ok let's verify, then:
        XMLStreamReader sr = getCoalescingReader(sw.toString());
        // but just using plain old Stax...
        assertTokenType(START_ELEMENT, sr.next());
        assertElem(sr, null, "root");
        assertTokenType(START_ELEMENT, sr.next());
        assertEquals(1, sr.getAttributeCount());
        assertEquals(0, sr.getNamespaceCount());
        assertElem(sr, null, "leaf");
        assertEquals("attr", sr.getAttributeLocalName(0));
        assertEquals("value", sr.getAttributeValue(0));
        assertTokenType(END_ELEMENT, sr.next());
        assertTokenType(END_ELEMENT, sr.next());
        assertTokenType(END_DOCUMENT, sr.next());
        sr.close();
    }
}
