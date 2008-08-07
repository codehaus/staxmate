package org.codehaus.staxmate.in;

import java.io.*;

import javax.xml.stream.*;

import org.codehaus.staxmate.SMInputFactory;
import org.codehaus.staxmate.in.*;

/**
 *
 * @author Michel Goldstein
 * @author Tatu Saloranta
 */
public class TestSimpleText
    extends BaseReaderTest
{
    public void testSimpleRead()
        throws XMLStreamException
    {
        final String text = "1";
        String XML = "<lvl1><lvl2>" + text + "</lvl2></lvl1>";
        XMLStreamReader sr = XMLInputFactory.newInstance().createXMLStreamReader(new StringReader(XML));
        SMInputCursor rootc = SMInputFactory.rootElementCursor(sr);
        rootc.getNext();
        String elemName = rootc.getLocalName();
        assertEquals("lvl1",elemName);
        SMInputCursor mainC = rootc.childElementCursor();
        while(mainC.getNext() != null) {
            SMInputCursor child = mainC.childCursor();
            child.getNext();
            String valText = child.getText();
            assertEquals(text,valText);
        }
    }
}