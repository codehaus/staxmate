package org.codehaus.staxmate.util;

import java.io.IOException;
import java.io.Writer;

import javax.xml.namespace.NamespaceContext;
import javax.xml.stream.*;
import javax.xml.stream.util.StreamReaderDelegate;

import org.codehaus.stax2.*;
import org.codehaus.stax2.validation.*;

/**
 * This adapter implements parts of {@link XMLStreamReader2}, the
 * extended stream reader defined by Stax2 extension, by wrapping
 * a vanilla Stax 1.0 {@link XMLStreamReader} implementation.
 *<p>
 * Note: the implementation is incomplete as-is, since not all
 * features needed are accessible via basic Stax 1.0 interface.
 * However, it should be enough to allow StaxMate to handle
 * underlying Stax implementations in generic way, independent
 * of whether they are truly Stax2 compatible or not.
 */
public class Stax2ReaderAdapter
    extends StreamReaderDelegate /* from Stax 1.0 */
    implements XMLStreamReader2 /* From Stax2 */
               ,AttributeInfo
               ,DTDInfo
               ,LocationInfo
{
    /**
     * Number of open (start) elements currently.
     */
    protected int mDepth = 0;

    /*
    ////////////////////////////////////////////////////
    // Life-cycle methods
    ////////////////////////////////////////////////////
     */

    protected Stax2ReaderAdapter(XMLStreamReader sr)
    {
        super(sr);
    }

    /**
     * Method that should be used to add dynamic support for
     * {@link XMLStreamReader2}. Method will check whether the
     * stream reader passed happens to be a {@link XMLStreamReader2};
     * and if it is, return it properly cast. If not, it will create
     * necessary wrapper to support features needed by StaxMate,
     * using vanilla Stax 1.0 interface.
     */
    public static XMLStreamReader2 wrapIfNecessary(XMLStreamReader sr)
    {
        if (sr instanceof XMLStreamReader2) {
            return (XMLStreamReader2) sr;
        }
        return new Stax2ReaderAdapter(sr);
    }

    /*
    ////////////////////////////////////////////////////
    // Stax 1.0 methods overridden
    ////////////////////////////////////////////////////
     */

    public int next()
        throws XMLStreamException
    {
        int type = super.next();
        if (type == XMLStreamConstants.START_ELEMENT) {
            ++mDepth;
        } else if (type == XMLStreamConstants.END_ELEMENT) {
            --mDepth;
        }
        return type;
    }

    /*
    ////////////////////////////////////////////////////
    // XMLStreamReader2 (StAX2) implementation
    ////////////////////////////////////////////////////
     */

    // // // StAX2, per-reader configuration

    public Object getFeature(String name)
    {
        // No features defined
        return null;
    }

    public void setFeature(String name, Object value)
    {
        // No features defined
    }

    // NOTE: getProperty() defined in Stax 1.0 interface

    public boolean isPropertySupported(String name) {
        /* No way to cleanly implement this using just Stax 1.0
         * interface, so let's be conservative and decline any knowledge
         * of properties...
         */
        return false;
    }

    public boolean setProperty(String name, Object value)
    {
        return false; // could throw an exception too
    }

    // // // StAX2, additional traversal methods

    public void skipElement() throws XMLStreamException
    {
        if (getEventType() != START_ELEMENT) {
            throwNotStartElem();
        }
        int nesting = 1; // need one more end elements than start elements

        while (true) {
            int type = next();
            if (type == START_ELEMENT) {
                ++nesting;
            } else if (type == END_ELEMENT) {
                if (--nesting == 0) {
                    break;
                }
            }
        }
    }

    // // // StAX2, additional attribute access

    public AttributeInfo getAttributeInfo() throws XMLStreamException
    {
        if (getEventType() != START_ELEMENT) {
            throwNotStartElem();
        }
        return this;
    }

    // // // StAX2, Additional DTD access

    public DTDInfo getDTDInfo() throws XMLStreamException
    {
        if (getEventType() != DTD) {
            return null;
        }
        return this;
    }

    // // // StAX2, Additional location information

    /**
     * Location information is always accessible, for this reader.
     */
    public final LocationInfo getLocationInfo() {
        return this;
    }

    // // // StAX2, Pass-through text accessors

    public int getText(Writer w, boolean preserveContents)
        throws IOException, XMLStreamException
    {
        char[] cbuf = getTextCharacters();
        int start = getTextStart();
        int len = getTextLength();

        if (len > 0) {
            w.write(cbuf, start, len);
        }
        return len;
    }

    // // // StAX 2, Other accessors

    /**
     * @return Number of open elements in the stack; 0 when parser is in
     *  prolog/epilog, 1 inside root element and so on.
     */
    public int getDepth() {
        return mDepth;
    }

    /**
     * Alas, there is no way to find this out via Stax 1.0, so this
     * implementation always returns false.
     */
    public boolean isEmptyElement() throws XMLStreamException
    {
        return false;
    }

    public NamespaceContext getNonTransientNamespaceContext()
    {
        /* Too hard to construct without other info: let's bail
         * and return null; this is better than return a transient
         * one.
         */
        return null; // never gets here
    }

    public String getPrefixedName()
    {
        switch (getEventType()) {
        case START_ELEMENT:
        case END_ELEMENT:
            {
                String prefix = getPrefix();
                String ln = getLocalName();

                if (prefix == null) {
                    return ln;
                }
                StringBuffer sb = new StringBuffer(ln.length() + 1 + prefix.length());
                sb.append(prefix);
                sb.append(':');
                sb.append(ln);
                return sb.toString();
            }
        case ENTITY_REFERENCE:
            return getLocalName();
        case PROCESSING_INSTRUCTION:
            return getPITarget();
        case DTD:
            return getDTDRootName();

        }
        throw new IllegalStateException("Current state not START_ELEMENT, END_ELEMENT, ENTITY_REFERENCE, PROCESSING_INSTRUCTION or DTD");
    }

    public void closeCompletely() throws XMLStreamException
    {
        /* As usual, Stax 1.0 offers no generic way of doing just this.
         * But let's at least call the lame basic close()
         */
        close();
    }

    /*
    ////////////////////////////////////////////////////
    // AttributeInfo implementation (StAX 2)
    ////////////////////////////////////////////////////
     */

    // Already part of XMLStreamReader
    //public int getAttributeCount();

    public int findAttributeIndex(String nsURI, String localName)
    {
        if ("".equals(nsURI)) {
            nsURI = null;
        }
        for (int i = 0, len = getAttributeCount(); i < len; ++i) {
            if (getAttributeLocalName(i).equals(localName)) {
                String otherUri = getAttributeNamespace(i);
                if (nsURI == null) {
                    if (otherUri == null || otherUri.length() == 0) {
                        return i;
                    }
                } else {
                    if (nsURI.equals(otherUri)) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    public int getIdAttributeIndex()
    {
        for (int i = 0, len = getAttributeCount(); i < len; ++i) {
            if ("ID".equals(getAttributeType(i))) {
                return i;
            }
        }
        return -1;
    }

    public int getNotationAttributeIndex()
    {
        for (int i = 0, len = getAttributeCount(); i < len; ++i) {
            if ("NOTATION".equals(getAttributeType(i))) {
                return i;
            }
        }
        return -1;
    }

    /*
    ////////////////////////////////////////////////////
    // DTDInfo implementation (StAX2)
    ////////////////////////////////////////////////////
     */

    public Object getProcessedDTD() {
        return null;
    }

    public String getDTDRootName() {
        return null;
    }

    public String getDTDPublicId() {
        return null;
    }

    public String getDTDSystemId() {
        return null;
    }

    /**
     * @return Internal subset portion of the DOCTYPE declaration, if any;
     *   empty String if none
     */
    public String getDTDInternalSubset()
    {
        /* According to basic Stax API, getText() <b>should</b> return
         * the internal subset. Not all implementations agree, so
         * this may or may not work.
         */
        if (getEventType() == XMLStreamConstants.DTD) {
            return getText();
        }
        return null;
    }

    // // StAX2, v2.0

    public DTDValidationSchema getProcessedDTDSchema() {
        return null;
    }

    /*
    ////////////////////////////////////////////////////
    // LocationInfo implementation (StAX2)
    ////////////////////////////////////////////////////
     */

    // // // First, the "raw" offset accessors:

    public long getStartingByteOffset() {
        return -1L;
    }

    public long getStartingCharOffset() {
        return 0;
    }

    public long getEndingByteOffset() throws XMLStreamException
    {
        return -1;
    }

    public long getEndingCharOffset() throws XMLStreamException
    {
        return -1;
    }

    // // // and then the object-based access methods:

    public XMLStreamLocation2 getStartLocation()
    {
        /* We don't really know whether location given is current,
         * start or end, but it's the best approximation we have
         * without knowing more about impl:
         */
        return getCurrentLocation();
    }

    public XMLStreamLocation2 getCurrentLocation()
    {
        // Just need to adapt; no info on parent context, if any:
        return new Stax2LocationAdapter(getLocation());
    }

    public final XMLStreamLocation2 getEndLocation()
        throws XMLStreamException
    {
        /* We don't really know whether location given is current,
         * start or end, but it's the best approximation we have
         * without knowing more about impl:
         */
        return getCurrentLocation();
    }

    /*
    ////////////////////////////////////////////////////
    // Stax2 validation
    ////////////////////////////////////////////////////
     */

    public XMLValidator validateAgainst(XMLValidationSchema schema)
        throws XMLStreamException
    {
        throwUnsupported();
        return null;
    }

    public XMLValidator stopValidatingAgainst(XMLValidationSchema schema)
        throws XMLStreamException
    {
        throwUnsupported();
        return null;
    }

    public XMLValidator stopValidatingAgainst(XMLValidator validator)
        throws XMLStreamException
    {
        throwUnsupported();
        return null;
    }

    public ValidationProblemHandler setValidationProblemHandler(ValidationProblemHandler h)
    {
        return null;
    }

    /*
    ////////////////////////////////////////////////////
    // Internal methods
    ////////////////////////////////////////////////////
     */

    protected void throwUnsupported()
        throws XMLStreamException
    {
        throw new XMLStreamException("Unsupported method");
    }

    protected void throwNotStartElem()
    {
        throw new IllegalStateException("Current state not START_ELEMENT");
    }
}
