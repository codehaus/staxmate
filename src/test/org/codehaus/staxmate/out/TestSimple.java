package org.codehaus.staxmate.out;

import java.io.*;

import javax.xml.stream.*;
import static javax.xml.stream.XMLStreamConstants.*;

import org.codehaus.staxmate.SMOutputFactory;

public class TestSimple
    extends BaseWriterTest
{
    public void testSimple()
        throws Exception
    {
    }

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
}
