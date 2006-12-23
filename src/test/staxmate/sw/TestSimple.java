package staxmate.sw;

import java.io.*;

import javax.xml.stream.*;

import org.codehaus.staxmate.SMOutputFactory;
import org.codehaus.staxmate.out.*;

public class TestSimple
    extends BaseWriterTest
{
    public void testSimple()
        throws Exception
    {
        StringWriter sw = new StringWriter();
        XMLStreamWriter xw = XMLOutputFactory.newInstance().createXMLStreamWriter(sw);
        SMOutputDocument doc = SMOutputFactory.createOutputDocument(xw);

        SMNamespace ns = doc.getNamespace("http://foo");
        SMNamespace ns2 = doc.getNamespace("urn://hihhei", "myns");

        /*
        doc.addComment("Comment!");
        */

        SMOutputElement elem = doc.addElement(ns, "root");
        elem.addCharacters("Rock & Roll");
        SMBufferedFragment frag = elem.createBufferedFragment();
        elem.addBuffered(frag);
        frag.addCharacters("[FRAG");
        frag.addComment("!!!");
        frag.addElement(ns, "tag");
        SMOutputElement elem2 = elem.addElement("branch");
        elem2.addElement(ns2, "leaf");
        frag.addCharacters("ment!]");
        frag.release();
        elem.addElement(ns2, "leaf2");
        //doc.closeRoot();

        // Uncomment for debugging:
        //System.out.println("Result:");
        //System.out.println(sw.toString());
    }
}
