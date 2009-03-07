package org.codehaus.staxmate;

import java.io.*;

import javax.xml.stream.*;
import static javax.xml.stream.XMLStreamConstants.*;

import org.codehaus.staxmate.in.SMInputCursor;

public abstract class BaseTest
    extends junit.framework.TestCase
{
    protected SMInputFactory _inputFactory;

    /*
    ////////////////////////////////////////////////////////
    // Factory methods
    ////////////////////////////////////////////////////////
     */

    protected SMInputFactory getInputFactory()
    {
        if (_inputFactory == null) {
            _inputFactory = new SMInputFactory(XMLInputFactory.newInstance());
        }
        return _inputFactory;
    }

    protected XMLStreamReader getCoalescingReader(String content)
        throws XMLStreamException
    {
        XMLInputFactory f = XMLInputFactory.newInstance();
        f.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);
        return f.createXMLStreamReader(new StringReader(content));
    }

    protected XMLStreamWriter getSimpleWriter(Writer w)
        throws XMLStreamException
    {
        XMLOutputFactory f = XMLOutputFactory.newInstance();
        return f.createXMLStreamWriter(w);
    }

    /*
    ////////////////////////////////////////////////////////
    // Assertion support
    ////////////////////////////////////////////////////////
     */

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

    protected void assertTokenType(int expType, SMInputCursor crsr)
        throws XMLStreamException
    {
        assertTokenType(expType, crsr.getCurrEventCode());
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

    /*
    ////////////////////////////////////////////////////////
    // Other accessors
    ////////////////////////////////////////////////////////
     */

    /**
     * Note: calling this method will move stream to the next
     * non-textual event.
     */
    protected String collectAllText(XMLStreamReader sr)
        throws XMLStreamException
    {
        StringBuilder sb = new StringBuilder(100);
        while (true) {
            int type = sr.getEventType();
            if (type == CHARACTERS || type == SPACE || type == CDATA) {
                sb.append(sr.getText());
                sr.next();
            } else {
                break;
            }
        }
        return sb.toString();
    }
}
