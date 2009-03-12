package org.codehaus.staxmate.out;

import java.io.*;

import javax.xml.stream.*;
import static javax.xml.stream.XMLStreamConstants.*;

import org.codehaus.staxmate.SMOutputFactory;
import org.codehaus.staxmate.out.*;

public class TestNamespaces
    extends BaseWriterTest
{
    /**
     * Test to verify that [STAXMATE-25] works as expected.
     */
    public void testPredeclaredNs()
        throws XMLStreamException
    {
        StringWriter sw = new StringWriter();
        SMOutputDocument doc = createSimpleDoc(sw);

        final String NS_URI1 = "http://foo1";
        SMNamespace ns1 = doc.getNamespace(NS_URI1);
        final String NS_URI2 = "http://foo2";
        SMNamespace ns2 = doc.getNamespace(NS_URI2, "prefix");

        SMOutputElement elem = doc.addElement("root"); // no NS
        elem.predeclareNamespace(ns1);
        elem.predeclareNamespace(ns2);
        elem.addElement(ns2, "leaf");
        doc.closeRoot();

        // Ok let's verify using just plain old Stax
        XMLStreamReader sr = getCoalescingReader(sw.toString());
        assertTokenType(START_ELEMENT, sr.next());
        assertElem(sr, null, "root");
        assertEquals(2, sr.getNamespaceCount());
        assertTokenType(START_ELEMENT, sr.next());
        assertElem(sr, ns2.getURI(), "leaf");
        assertEquals(0, sr.getNamespaceCount());
        assertTokenType(END_ELEMENT, sr.next());
        assertTokenType(END_ELEMENT, sr.next());
        sr.close();
    }
}
