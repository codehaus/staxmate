package org.codehaus.staxmate.in;

import javax.xml.stream.*;

class BaseReaderTest
    extends org.codehaus.staxmate.BaseTest
{
    protected void assertElem(SMInputCursor crsr, String expURI, String expLN)
        throws XMLStreamException
    {
        assertEquals(expLN, crsr.getLocalName());
        assertTrue(crsr.hasLocalName(expLN));
        assertTrue(crsr.hasName(expURI, expLN));

        String uri = crsr.getNsUri();
        if (expURI == null) {
            if (uri != null && uri.length() > 0) {
                fail("Expected element to have no namespace, got '"+uri+"'");
            }
        } else {
            if (!expURI.equals(uri)) {
                fail("Expected element to have non-empty namespace '"+expURI+"', got '"+uri+"'");
            }
        }
    }
}
