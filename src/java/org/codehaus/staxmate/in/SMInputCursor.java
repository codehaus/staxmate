package org.codehaus.staxmate.in;

import java.io.IOException;
import java.io.Writer;

import javax.xml.namespace.QName;
import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader; // for javadocs

import org.codehaus.stax2.DTDInfo;
import org.codehaus.stax2.XMLStreamReader2;

/**
 * Base class for reader-side cursors that form the main input-side
 * abstraction offered by StaxMate.
 *<p>
 * Note: since cursors are thin wrappers around {@link XMLStreamReader2},
 * and since not all Stax implementations implement
 * {@link XMLStreamReader2}, some wrapping may be involved.
 *
 * @author Tatu Saloranta
 */
public abstract class SMInputCursor
{
    /*
    ////////////////////////////////////////////
    // Constants, tracking
    ////////////////////////////////////////////
     */

    // // // Constants for element tracking:

    /**
     * This enumeration lists different tracking behaviors available
     * for cursors. Tracking is a feature that can be used to store
     * information about traversed sub-trees, to allow for a limited
     * access to information that is not limited to ancestor stack.
     * Using tracking will consume more memory, but generally less
     * than constructing a full in-memory tree object model (such
     * as DOM), since it the represenation is compact, read-only,
     * and only subset of a full tree (depending on tracking setting).
     */
    public enum Tracking
    {
        /**
         * Value that indicates that no element state information should
         * be tracked. This means that {@link #getTrackedElement} will always
         * return null for this element, as well as that if immediate child
         * cursors do have tracking enabled, element states it saves have
         * no parent element information available.
         */
        NONE,

        /**
         * Value that indicates that element basic state information should
         * be tracked, including linkage to the parent element (but only
         * if the parent cursor was tracking elements).
         * This means that {@link #getTrackedElement} will return non-null
         * values, as soon as this cursor has been advanced over its first
         * element node. However, element will return null from its
         * {@link SMElementInfo#getPreviousSibling} since sibling information
         * is not tracked.
         */
        PARENTS,

        /**
         * Value that indicates full element state information should
         * be tracked for all "visible" elements: visible meaning that element
         * node was accepted by the filter this cursor uses.
         * This means that {@link #getTrackedElement} will return non-null
         * values, as soon as this cursor has been advanced over its first
         * element node, and that element will return non-null from its
         * {@link SMElementInfo#getPreviousSibling} unless it's the first element
         * iterated by this cursor.
         */
        VISIBLE_SIBLINGS,

        /**
         * Value that indicates full element state information should
         * be tracked for ALL elements (including ones not visible to the
         * caller via {@link #getNext} method).
         * This means that {@link #getTrackedElement} will return non-null
         * values, as soon as this cursor has been advanced over its first
         * element node, and that element will return non-null from its
         * {@link SMElementInfo#getPreviousSibling} unless it's the first element
         * iterated by this cursor.
         */
        ALL_SIBLINGS
    }

    /*
    ////////////////////////////////////////////
    // Constants, initial cursor state
    ////////////////////////////////////////////
     */

    // // // Constants for the cursor state

    protected enum State {
        /**
         * Initial means that the cursor has been constructed, but hasn't
         * yet been advanced. No data can be accessed yet, but the cursor
         * can be advanced.
         */
        INITIAL,

        /**
         * Active means that cursor's data is valid and can be accessed;
         * plus it can be advanced as well.
         */
        ACTIVE,

        /**
         * Status that indicates that although cursor would be active, there
         * is a child cursor active which means that this cursor can not
         * be used to access data: only the innermost child cursor can.
         * It can still be advanced, however.
         */
        HAS_CHILD,

        /**
         * Closed cursors are ones that do not point to accessible data, nor
         * can be advanced any further.
         */
        CLOSED
    }

    /*
    ////////////////////////////////////////////
    // Constants, other
    ////////////////////////////////////////////
     */

    /**
     * This is the mapping array, indexed by Stax 1.0 event type integer
     * code, value being matching {@link SMEvent} enumeration value.
     */
    protected final static SMEvent[] sEventsByIds =
        SMEvent.constructIdToEventMapping();


    /*
    ////////////////////////////////////////////
    // Configuration
    ////////////////////////////////////////////
     */

    /**
     * Underlying stream reader used. It will either be a native
     * {@link XMLStreamReader2} instance, or a regular (Stax 1.0)
     * {@link javax.xml.stream.XMLStreamReader} wrapped in an
     * adapter.
     */
    protected final XMLStreamReader2 mStreamReader;

    /**
     * Optional filter object that can be used to filter out events of
     * types caller is not interested in.
     */
    protected SMFilter mFilter = null;

    /**
     * Whether element information is to be tracked or not, and if it is,
     * how much of it will be stored. See {@link Tracking} for details.
     */
    protected Tracking mElemTracking = Tracking.NONE;

    /**
     * Optional factory instance that is used to create element info
     * objects if element tracking is enabled. If null, will use default
     * generation mechanism, implemented by SMInputCursor itself.
     *<p>
     * Note that by default, this factory will be passed down to child
     * and descendant cursors this cursor creates, so usually one
     * only needs to set the factory of the root cursor.
     */
    protected ElementInfoFactory mElemInfoFactory;

    /*
    ////////////////////////////////////////////
    // Iteration state
    ////////////////////////////////////////////
     */

    protected SMEvent mCurrEvent = null;

    /**
     * Current state of the cursor.
     */
    protected State mState = State.INITIAL;

    /**
     * Number of nodes iterated over by this cursor, including the
     * current one.
     */
    protected int mNodeCount = 0;

    /**
     * Number of start elements iterated over by this cursor, including the
     * current one.
     */
    protected int mElemCount = 0;

    /**
     * Number of enclosing start elements for this cursor; 0 for root
     * cursors
     */
    protected final int mParentCount;

    /**
     * Element that was last "tracked"; element over which cursor was
     * moved, and of which state has been saved for further use. At this
     * point, it can be null if no elements have yet been iterater over.
     * Alternatively, if it's not null, it may be currently pointed to
     * or not; if it's not, either child cursor is active, or this
     * cursor points to a non-start-element node.
     */
    protected SMElementInfo mTrackedElement = null;

    /**
     * Element parent cursor tracked when this cursor was created,
     * if any.
     */
    protected SMElementInfo mParentTrackedElement = null;

    /**
     * Cursor that has been opened for iterating child nodes of the
     * start element node this cursor points to. Needed to keep
     * cursor hierarchy synchronized, independent of which ones are
     * traversed.
     */
    protected SMInputCursor mChildCursor = null;

    /*
    ////////////////////////////////////////////
    // Additional data
    ////////////////////////////////////////////
     */

    /**
     * Non-typesafe payload data that applications can use, to pass
     * an extra argument along with cursors. Not used by the framework
     * itself for anything.
     */
    protected Object mData;

    /*
    ////////////////////////////////////////////
    // Life cycle, configuration
    ////////////////////////////////////////////
     */

    public SMInputCursor(SMInputCursor parent, XMLStreamReader2 sr, SMFilter filter)
    {
        mStreamReader = sr;
        mFilter = filter;
        /* By default, we use parent cursor's element tracking setting;
         * or "no tracking" if we have no parent
         */
        if (parent == null) {
            mElemTracking = Tracking.NONE;
            mParentTrackedElement = null;
            mParentCount = 0;
            mElemInfoFactory = null;
        } else {
            mElemTracking = parent.getElementTracking();
            mParentTrackedElement = parent.getTrackedElement();
            mParentCount = parent.getDepth() + 1;
            mElemInfoFactory = parent.getElementInfoFactory();
        }
    }

    public final void setFilter(SMFilter f) {
        mFilter = f;
    }

    /**
     * Changes tracking mode of this cursor to the new specified
     * mode. Default mode for cursors is the one their parent uses;
     * {@link Tracking#NONE} for root cursors with no parent.
     */
    public final void setElementTracking(Tracking tracking) {
        mElemTracking = tracking;
    }

    public final Tracking getElementTracking() {
        return mElemTracking;
    }

    public final void setElementInfoFactory(ElementInfoFactory f) {
        mElemInfoFactory = f;
    }

    public final ElementInfoFactory getElementInfoFactory() {
        return mElemInfoFactory;
    }

    /*
    ///////////////////////////////////////////////////
    // Public API, accessing cursor state information
    ///////////////////////////////////////////////////
     */

    /**
     * @return Number of nodes cursor has traversed (including ones
     *   filtered out). Starts with 0, and is incremented each time
     *   underlying stream reader's {@link XMLStreamReader#next} method
     *   is called, but not counting child cursors' node counts.
     */
    public int getNodeCount() {
        return mNodeCount;
    }

    /**
     * @return Number of start elements cursor has traversed (including ones
     *   filtered out). Starts with 0, and is incremented each time
     *   underlying stream reader's {@link XMLStreamReader#next} method
     *   is called and has moved over a start element, but not counting
     *   child cursors' element counts.
     */
    public int getElementCount() {
        return mNodeCount;
    }

    /**
     * Returns number of parent elements current node (or, the last
     * node cursor pointed to, or in absence of one [before cursor
     * has been advanced for the first time], it would point if advanced)
     * has.
     *<p>
     * For example, here are expected results for <code>getDepth()</code>
     * for an example XML document:
     *<pre>
     *  &lt;!-- Comment outside tree --> [0]
     *  &lt;root> [0]
     *    Text [1]
     *    &lt;branch> [1]
     *      Inner text [2]
     *      &lt;child /> [2]
     *    &lt;/branch> [1]
     *  &lt;/root> [0]
     *</pre>
     * Numbers in bracket are depths that would be returned when the
     * cursor points to the node.
     *<p>
     * Note: depths are different from what some APIs (such as XmlPull)
     * return.
     *
     * @return Number of enclosing nesting levels, ie. number of parent
     *   start elements for the node that cursor currently points to (or,
     *   in case of initial state, that it will point to if scope has
     *   node(s)).
     */
    public abstract int getDepth();

    /**
     * Returns the type of event this cursor either currently points to
     * (if in valid state), or pointed to (if ever iterated forward), or
     * null if just created.
     *
     * @return Type of event this cursor points to, if it currently points
     *   to one, or last one it pointed to otherwise (if ever pointed to
     *   a valid event), or null if neither.
     */
    public SMEvent getCurrEvent() {
        return mCurrEvent;
    }

    /**
     * Convenience method doing 
     */
    public int getCurrEventCode() {
        return (mCurrEvent == null) ? 0 : mCurrEvent.getEventCode();
    }

    /*
    ////////////////////////////////////////////
    // Public API, accessing tracked elements
    ////////////////////////////////////////////
     */

    /**
     * @return Information about last "tracked" element; element we have
     *    last iterated over when tracking has been enabled.
     */
    public SMElementInfo getTrackedElement() {
        return mTrackedElement;
    }

    /**
     * @return Information about the tracked element the parent cursor
     *    had, if parent cursor existed and was tracking element
     *    information.
     */
    public SMElementInfo getParentTrackedElement() {
        return mParentTrackedElement;
    }

    /*
    ////////////////////////////////////////////////
    // Public API, accessing current document state
    ////////////////////////////////////////////////
     */

    public final boolean readerAccessible() {
        return (mState == State.ACTIVE);
    }

    /**
     * Method that can be used to get direct access to the underlying
     * stream reader. This is usually needed to access some of less
     * often needed accessors for which there is no convenience method
     * in StaxMate API.
     *
     * @return Stream reader the cursor uses for getting XML events
     */
    public final XMLStreamReader2 getStreamReader() {
        return mStreamReader;
    }

    public Location getLocation()
        throws XMLStreamException
    {
        if (!readerAccessible()) {
            notAccessible("getLocation");
            return null;
        }
        return mStreamReader.getLocation();
    }

    /*
    ////////////////////////////////////////////////
    // Public API, accessing document text content
    ////////////////////////////////////////////////
     */

    /**
     * Method that can be used when this cursor points to a textual
     * event; something for which {@link XMLStreamReader#getText} can
     * be called. Note that it does not advance the cursor, or combine
     * multiple textual events.
     *
     * @return Textual content of the current event that this cursor
     *   points to, if any
     *
     * @throws XMLStreamException if either the underlying parser has
     *   problems (possibly including event type not being of textual
     *   type, see Stax 1.0 specs for details); or if this cursor does
     *   not currently point to an event.
     */
    public String getText()
        throws XMLStreamException
    {
        if (!readerAccessible()) {
            return notAccessible("getText");
        }
        return mStreamReader.getText();
    }

    /**
     * Method that can collect all text contained within START_ELEMENT
     * currently pointed by this cursor. Collection is done recursively
     * through all descendant text (CHARACTER, CDATA; optionally SPACE) nodes,
     * ignoring nodes of other types. After collecting text, cursor
     * will be positioned at the END_ELEMENT matching initial START_ELEMENT
     * and thus needs to be advanced to access the next sibling event.
     *
     * @param includeIgnorable Whether text for events of type SPACE should
     *   be ignored in the results or not. If false, SPACE events will be
     *   skipped; if true, white space will be included in results.
     */
    public String collectDescendantText(boolean includeIgnorable)
        throws XMLStreamException
    {
        if (!readerAccessible()) {
            return notAccessible("getText");
        }
        if (getCurrEvent() != SMEvent.START_ELEMENT) {
            throwXsEx("Can not call 'getText()' when cursor is not positioned over START_ELEMENT (current event "+currentEventStr()+")"); 
        }

        SMFilter f = includeIgnorable
            ? SMFilterFactory.getTextOnlyFilter()
            : SMFilterFactory.getNonIgnorableTextFilter();
        SMInputCursor childIt = descendantCursor(f);

        /* Cursor should only return actual text nodes, so no type
         * checks are needed, except for checks for EOF. But we can
         * also slightly optimize things, by avoiding StringBuilder
         * construction if there's just one node.
         */
        if (childIt.getNext() == null) {
            return "";
        }
        String text = childIt.getText(); // has to be a text event
        if (childIt.getNext() == null) {
            return text;
        }

        int size = text.length();
        StringBuffer sb = new StringBuffer((size < 500) ? 500 : size);
        sb.append(text);
        XMLStreamReader2 sr = childIt.getStreamReader();
        do {
            // Let's assume char array access is more efficient...
            sb.append(sr.getTextCharacters(), sr.getTextStart(),
                      sr.getTextLength());
        } while (childIt.getNext() != null);

        return sb.toString();
    }

    /**
     * Method similar to {@link #collectDescendantText}, but will write
     * the text to specified Writer instead of collecting it into a
     * String.
     *
     * @param w Writer to use for outputting text found
     * @param includeIgnorable Whether text for events of type SPACE should
     *   be ignored in the results or not. If false, SPACE events will be
     *   skipped; if true, white space will be included in results.
     */
    public void processDescendantText(Writer w, boolean includeIgnorable)
        throws IOException, XMLStreamException
    {
        SMFilter f = includeIgnorable
            ? SMFilterFactory.getTextOnlyFilter()
            : SMFilterFactory.getNonIgnorableTextFilter();
        SMInputCursor childIt = descendantCursor(f);

        // Any text in there?
        XMLStreamReader2 sr = childIt.getStreamReader();
        while (childIt.getNext() != null) {
            /* 'true' indicates that we are not to lose the text contained
             * (can call getText() multiple times, idempotency). While this
             * may not be as efficient as allowing content to be discarded,
             * let's play it safe. Another method could be added for
             * the alternative (fast but dangerous) behaviour as needed.
             */
            sr.getText(w, true);
        }
    }

    /*
    ////////////////////////////////////////////////////
    // Public API, accessing current element information
    ////////////////////////////////////////////////////
     */

    public QName getQName()
        throws XMLStreamException
    {
        if (!readerAccessible()) {
            notAccessible("getName");
            return null; // never gets here
        }
        return mStreamReader.getName();
    }

    /**
     * For events with fully qualified names (START_ELEMENT, END_ELEMENT,
     * ATTRIBUTE, NAMESPACE), returns the local component of the full
     * name. For events with only non-qualified name (PROCESSING_INSTRUCTION,
     * entity and notation declarations, references), returns the name.
     * For other events, returns null.
     *
     * @return Local component of the name
     */
    public String getLocalName()
        throws XMLStreamException
    {
        if (!readerAccessible()) {
            return notAccessible("getLocalName");
        }
        int type = getCurrEventCode();
        switch (type) {
        case XMLStreamConstants.START_ELEMENT:
        case XMLStreamConstants.END_ELEMENT:
        case XMLStreamConstants.ENTITY_REFERENCE:
            return mStreamReader.getLocalName();
        case XMLStreamConstants.PROCESSING_INSTRUCTION:
            return mStreamReader.getPITarget();
        case XMLStreamConstants.DTD:
            {
                DTDInfo dtd = mStreamReader.getDTDInfo();
                return (dtd == null) ? null : dtd.getDTDRootName();
            }
        }

        return null;
    }

    public String getPrefix()
        throws XMLStreamException
    {
        if (!readerAccessible()) {
            return notAccessible("getPrefix");
        }
        return mStreamReader.getPrefix();
    }

    public String getNsUri()
        throws XMLStreamException
    {
        if (!readerAccessible()) {
            return notAccessible("getNsUri");
        }
        return mStreamReader.getNamespaceURI();
    }

    /**
     * Returns a String representation of either the fully-qualified name
     * (if the event has full name) or the local name (if event does not
     * have full name but has local name); or if no name available, throws
     * stream exception.
     */
    public String getPrefixedName()
        throws XMLStreamException
    {
        if (!readerAccessible()) {
            return notAccessible("getPrefixedName");
        }
        return mStreamReader.getPrefixedName();
    }

    /*
    ////////////////////////////////////////////////////
    // Public API, accessing current element's attribute
    // information
    ////////////////////////////////////////////////////
     */

    public int getAttrCount()
        throws XMLStreamException
    {
        if (!readerAccessible()) {
            notAccessible("getAttrCount");
            return 0; // never gets here
        }
        return mStreamReader.getAttributeCount();
    }

    public int findAttrIndex(String uri, String localName)
        throws XMLStreamException
    {
        if (!readerAccessible()) {
            notAccessible("getAttrCount");
            return -1; // never gets here
        }

        return mStreamReader.getAttributeInfo().findAttributeIndex(uri, localName);
    }

    public QName getAttrName(int index)
        throws XMLStreamException
    {
        if (!readerAccessible()) {
            notAccessible("getAttrName");
            return null; // never gets here
        }
        return mStreamReader.getAttributeName(index);
    }

    public String getAttrLocalName(int index)
        throws XMLStreamException
    {
        if (!readerAccessible()) {
            return notAccessible("getAttrLocalName");
        }
        return mStreamReader.getAttributeLocalName(index);
    }

    public String getAttrPrefix(int index)
        throws XMLStreamException
    {
        if (!readerAccessible()) {
            return notAccessible("getAttrPrefix");
        }
        return mStreamReader.getAttributePrefix(index);
    }

    public String getAttrNsUri(int index)
        throws XMLStreamException
    {
        if (!readerAccessible()) {
            return notAccessible("getAttrNsUri");
        }
        return mStreamReader.getAttributeNamespace(index);
    }

    public String getAttrValue(int index)
        throws XMLStreamException
    {
        if (!readerAccessible()) {
            return notAccessible("getAttributeValue");
        }
        return mStreamReader.getAttributeValue(index);
    }

    /**
     * Convenience accessor method to access an attribute that is
     * not in a namespace (has no prefix).
     */
    public String getAttrValue(String localName)
        throws XMLStreamException
    {
        if (!readerAccessible()) {
            return notAccessible("getAttributeValue");
        }
        /* If we are to believe StAX specs, null would mean "do not
         * check namespace" -- that's pretty much never what anyone
         * really wants (or, at least should use), so let's pass
         * "" to indicate "no namespace"
         */
        return mStreamReader.getAttributeValue("", localName);
    }

    public String getAttrValue(String uri, String localName)
        throws XMLStreamException
    {
        if (!readerAccessible()) {
            return notAccessible("getAttrValue");
        }
        return mStreamReader.getAttributeValue(uri, localName);
    }

    /*
    ////////////////////////////////////////////////////
    // Public API, accessing typed attribute value
    // information
    ////////////////////////////////////////////////////
     */

    public int getAttrIntValue(int index)
        throws NumberFormatException, XMLStreamException
    {
        if (!readerAccessible()) {
            notAccessible("getAttrIntValue");
            return -1; // never gets here
        }
        /* For now, let's just get it as String and convert: in future,
         * may be able to use more efficient access method(s)
         */
        String value = mStreamReader.getAttributeValue(index);
        return doParseInt(value);
    }

    public int getAttrIntValue(String uri, String localName)
        throws XMLStreamException
    {
        if (!readerAccessible()) {
            notAccessible("getAttrIntValue");
            return -1; // never gets here
        }
        /* For now, let's just get it as String and convert: in future,
         * may be able to use more efficient access method(s)
         */
        String value = mStreamReader.getAttributeValue(uri, localName);
        return doParseInt(value);
    }

    public int getAttrIntValue(int index, int defValue)
        throws NumberFormatException, XMLStreamException
    {
        if (!readerAccessible()) {
            notAccessible("getAttrIntValue");
            return -1; // never gets here
        }
        /* For now, let's just get it as String and convert: in future,
         * may be able to use more efficient access method(s)
         */
        String valueStr = mStreamReader.getAttributeValue(index);
        return doParseInt(valueStr, defValue);
    }

    public int getAttrIntValue(String uri, String localName, int defValue)
        throws XMLStreamException
    {
        if (!readerAccessible()) {
            notAccessible("getAttrIntValue");
            return -1; // never gets here
        }
        /* For now, let's just get it as String and convert: in future,
         * may be able to use more efficient access method(s)
         */
        String valueStr = mStreamReader.getAttributeValue(uri, localName);
        /* Also: conversion itself should be trivial to handle faster...
         */
        return doParseInt(valueStr, defValue);
    }

    /*
    ////////////////////////////////////////////////
    // Public API, accessing extra application data
    ////////////////////////////////////////////////
     */

    public Object getData() {
        return mData;
    }

    public void setData(Object o) {
        mData = o;
    }

    /*
    ////////////////////////////////////////////
    // Public API, iteration
    ////////////////////////////////////////////
     */

    /**
     * Main iterating method.
     *
     * @return Type of event (from {@link XMLStreamConstants}, such as
     *   {@link XMLStreamConstants#START_ELEMENT}, if a new node was
     *   iterated over; <code>null</code> when there are no more
     *   nodes this cursor can iterate over.
     */
    public abstract SMEvent getNext()
        throws XMLStreamException;

    /**
     * Method that will create a new nested cursor for iterating
     * over all (immediate) child nodes of the start element this cursor
     * currently points to.
     * If cursor does not point to a start element,
     * it will throw {@link IllegalStateException}; if it does not support
     * concept of child cursors, it will throw
     * {@link UnsupportedOperationException}
     *
     * @param f Filter child cursor is to use for filtering out
     *    'unwanted' nodes; may be null for no filtering
     *
     * @throws IllegalStateException If cursor can not be created due
     *   to the state cursor is in.
     * @throws UnsupportedOperationException If cursor does not allow
     *   creation of child cursors.
     */
    public SMInputCursor childCursor(SMFilter f)
        throws XMLStreamException
    {
        if (mState != State.ACTIVE) {
            if (mState == State.HAS_CHILD) {
                throw new IllegalStateException("Child cursor already requested.");
            }
            throw new IllegalStateException("Can not iterate children: cursor does not point to a start element (state "+getStateDesc()+")");
        }
        if (mCurrEvent != SMEvent.START_ELEMENT) {
            throw new IllegalStateException("Can not iterate children: cursor does not point to a start element (pointing to "+mCurrEvent+")");
        }

        mChildCursor = constructChildCursor(f);
        mState = State.HAS_CHILD;
        return mChildCursor;
    }

    public final SMInputCursor childCursor()
        throws XMLStreamException
    {
        return childCursor(null);
    }

    /**
     * Method that will create a new nested cursor for iterating
     * over all the descendant (children and grandchildren) nodes of
     * the start element this cursor currently points to.
     * If cursor does not point to a start element,
     * it will throw {@link IllegalStateException}; if it does not support
     * concept of descendant cursors, it will throw
     * {@link UnsupportedOperationException}
     *
     * @throws IllegalStateException If cursor can not be created due
     *   to the state cursor is in (or for some cursors, if they never
     *   allow creating such cursors)
     * @throws UnsupportedOperationException If cursor does not allow
     *   creation of descendant cursors.
     */
    public SMInputCursor descendantCursor(SMFilter f)
        throws XMLStreamException
    {
        if (mState != State.ACTIVE) {
            if (mState == State.HAS_CHILD) {
                throw new IllegalStateException("Child cursor already requested.");
            }
            throw new IllegalStateException("Can not iterate children: cursor does not point to a start element (state "+getStateDesc()+")");
        }
        if (mCurrEvent != SMEvent.START_ELEMENT) {
            throw new IllegalStateException("Can not iterate children: cursor does not point to a start element (pointing to "+mCurrEvent+")");
        }

        mChildCursor = constructDescendantCursor(f);
        mState = State.HAS_CHILD;
        return mChildCursor;
    }

    public final SMInputCursor descendantCursor()
        throws XMLStreamException
    {
        return descendantCursor(null);
    }

    /**
     * Convenience method; equivalent to 
     *<code>childCursor(SMFilterFactory.getElementOnlyFilter());</code>
     */
    public final SMInputCursor childElementCursor()
        throws XMLStreamException
    {
        return childCursor(SMFilterFactory.getElementOnlyFilter());
    }

    /**
     * Convenience method; equivalent to 
     *<code>childCursor(SMFilterFactory.getElementOnlyFilter(elemName));</code>
     * Will only return START_ELEMENT and END_ELEMENT events, whose element
     * name matches given qname.
     */
    public final SMInputCursor childElementCursor(QName elemName)
        throws XMLStreamException
    {
        return childCursor(SMFilterFactory.getElementOnlyFilter(elemName));
    }

    /**
     * Convenience method; equivalent to 
     *<code>childCursor(SMFilterFactory.getElementOnlyFilter(elemName));</code>
     * Will only return START_ELEMENT and END_ELEMENT events, whose element's
     * local name matches given local name, and that does not belong to
     * a namespace.
     */
    public final SMInputCursor childElementCursor(String elemLocalName)
        throws XMLStreamException
    {
        return childCursor(SMFilterFactory.getElementOnlyFilter(elemLocalName));
    }

    /**
     * Convenience method; equivalent to 
     *<code>descendantCursor(SMFilterFactory.getElementOnlyFilter());</code>
     */
    public final SMInputCursor descendantElementCursor()
        throws XMLStreamException
    {
        return descendantCursor(SMFilterFactory.getElementOnlyFilter());
    }

    /**
     * Convenience method; equivalent to 
     *<code>descendantCursor(SMFilterFactory.getElementOnlyFilter(elemName));</code>
     * Will only return START_ELEMENT and END_ELEMENT events, whose element
     * name matches given qname.
     */
    public final SMInputCursor descendantElementCursor(QName elemName)
        throws XMLStreamException
    {
        return descendantCursor(SMFilterFactory.getElementOnlyFilter(elemName));
    }

    /**
     * Convenience method; equivalent to 
     *<code>descendantCursor(SMFilterFactory.getElementOnlyFilter(elemLocalName));</code>.
     * Will only return START_ELEMENT and END_ELEMENT events, whose element
     * local name matches given local name, and that do not belong to a
     * namespace
     */
    public final SMInputCursor descendantElementCursor(String elemLocalName)
        throws XMLStreamException
    {
        return descendantCursor(SMFilterFactory.getElementOnlyFilter(elemLocalName));
    }

    /**
     * Convenience method; equivalent to 
     *<code>childCursor(SMFilterFactory.getMixedFilter());</code>
     */
    public final SMInputCursor childMixedCursor()
        throws XMLStreamException
    {
        return childCursor(SMFilterFactory.getMixedFilter());
    }

    /**
     * Convenience method; equivalent to 
     *<code>descendantCursor(SMFilterFactory.getMixedFilter());</code>
     */
    public final SMInputCursor descendantMixedCursor()
        throws XMLStreamException
    {
        return descendantCursor(SMFilterFactory.getMixedFilter());
    }

    /*
    ////////////////////////////////////////////
    // Methods sub-classes need or can override
    // to customize behaviour:
    ////////////////////////////////////////////
     */

    /**
     * Method called by the parent cursor, to skip over the scope
     * this cursor iterates, and of its sub-scopes if any.
     */
    protected abstract void skipTree()
        throws XMLStreamException;

    /**
     * Method cursor calls when it needs to track element state information;
     * if so, it calls this method to take a snapshot of the element.
     *<p>
     * Note caller already suppresses calls so that this method is only
     * called when information needs to be preserved. Further, previous
     * element is only passed if such linkage is to be preserved (reason
     * for not always doing it is the increased memory usage).
     *<p>
     * Finally, note that this method does NOT implement
     * {@link ElementInfoFactory}, as its signature does not include the
     * cursor argument, as that's passed as this pointer already.
     */
    protected SMElementInfo constructElementInfo(SMElementInfo parent,
                                                 SMElementInfo prevSibling)
        throws XMLStreamException
    {
        if (mElemInfoFactory != null) {
            return mElemInfoFactory.constructElementInfo(this, parent, prevSibling);
        }
        XMLStreamReader2 sr = mStreamReader;
        return new DefaultElementInfo(parent, prevSibling,
                                      sr.getPrefix(), sr.getNamespaceURI(), sr.getLocalName(),
                                      mNodeCount-1, mElemCount-1, getDepth());
    }

    protected abstract SMInputCursor constructChildCursor(SMFilter f)
        throws XMLStreamException;

    protected abstract SMInputCursor constructDescendantCursor(SMFilter f)
        throws XMLStreamException;

    /*
    ////////////////////////////////////////////
    // Internal parsing methods
    ////////////////////////////////////////////
     */

    protected int doParseInt(String valueStr)
        throws NumberFormatException
    {
        // !!! Let's optimize once time allows it...
        return Integer.parseInt(valueStr);
    }

    protected int doParseInt(String valueStr, int defValue)
    {
        if (valueStr == null || valueStr.length() == 0) {
            return defValue;
        }

        // !!! Let's optimize once time allows it...
        try {
            return Integer.parseInt(valueStr);
        } catch (NumberFormatException nex) {
            return defValue;
        }
    }

    /*
    ////////////////////////////////////////////
    // Package methods
    ////////////////////////////////////////////
     */

    protected final boolean isRootCursor() {
        return (mParentCount == 0);
    }

    /**
     * @param depth Number of enclosing 'extra' START_ELEMENTs to match;
     *   usually either 0 or 1
     */
    protected void skipSubTree(int depth)
        throws XMLStreamException
    {
        while (true) {
            int type = mStreamReader.next();
            if (type == XMLStreamConstants.START_ELEMENT) {
                ++depth;
            } else if (type == XMLStreamConstants.END_ELEMENT) {
                if (--depth < 0) {
                    break;
                }
            } else if (type == XMLStreamConstants.END_DOCUMENT) {
                // An error...
                throw new IllegalStateException("Unexpected END_DOCUMENT encountered when skipping a sub-tree.");
            }
        }
    }

    protected String notAccessible(String method)
        throws XMLStreamException
    {
        if (mChildCursor != null) {
            throwXsEx("Can not call '"+method+"(): cursor does not point to a valid node, as it has an active open child cursor.");
        }
        throwXsEx("Can not call '"+method+"(): cursor does not point to a valid node (curr event "+getCurrEvent()+"; cursor state "
                  +getStateDesc());
        return null;
    }

    protected String getStateDesc() {
        return mState.toString();
    }

    /**
     * @return Human-readable description of the underlying Stax event
     *   this cursor points to.
     */
    protected String currentEventStr()
    {
        return (mCurrEvent == null) ? "null" : mCurrEvent.toString();
    }

    protected void throwXsEx(String msg)
        throws XMLStreamException
    {
        // !!! TODO: use StaxMate-specific sub-classes of XMLStreamException?
        throw new XMLStreamException(msg, mStreamReader.getLocation());
    }

    public String toString() {
        return "{" + getClass().getName()+": "+mState+", curr evt: "
            +mCurrEvent+"}";
    }
}
