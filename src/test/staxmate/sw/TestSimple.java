package staxmate.sw;

import java.io.*;

import javax.xml.stream.*;
import static javax.xml.stream.XMLStreamConstants.*;

import org.codehaus.staxmate.SMOutputFactory;
import org.codehaus.staxmate.out.*;

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
 
        // Ok let's verify, then using just plain old Stax
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

    /* 29-Aug-2008, tatus: This test won't pass without fixes; leaving
     *    uncommented for now tho.
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
        System.out.println("Result:");
        System.out.println(sw.toString());
 
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

    // // // Helpers:

    protected void assertTokenType(int expType, int actType)
        throws XMLStreamException
    {
        assertEquals(expType, actType);
    }

    protected void assertTokenType(int expType, XMLStreamReader sr)
        throws XMLStreamException
    {
        assertTokenType(expType, sr.getEventType());
    }

    protected void assertElem(XMLStreamReader sr, String expURI, String expLN)
        throws XMLStreamException
    {
        assertEquals(expLN, sr.getLocalName());
        String actURI = sr.getNamespaceURI();
        if (expURI == null || expURI.length() == 0) {
            if (actURI != null && actURI.length() > 0) {
                fail("Expected no namespace, got URI '"+actURI+"'");
            }
        } else {
            assertEquals(expURI, sr.getNamespaceURI());
        }
    }

    protected XMLStreamWriter getSimpleWriter(Writer w)
        throws XMLStreamException
    {
        XMLOutputFactory f = XMLOutputFactory.newInstance();
        return f.createXMLStreamWriter(w);
    }

    protected XMLStreamReader getCoalescingReader(String content)
        throws XMLStreamException
    {
        XMLInputFactory f = XMLInputFactory.newInstance();
        f.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);
        return f.createXMLStreamReader(new StringReader(content));
    }
}
