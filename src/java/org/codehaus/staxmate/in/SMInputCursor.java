package org.codehaus.staxmate.in;

import java.io.IOException;
import java.io.Writer;

import javax.xml.namespace.QName;
import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader; // for javadocs

import org.codehaus.stax2.DTDInfo;
import org.codehaus.stax2.LocationInfo;
import org.codehaus.stax2.XMLStreamReader2;

import org.codehaus.staxmate.util.DataUtil;

/**
 * Base class for reader-side cursors that form the main input-side
 * abstraction offered by StaxMate.
 *<p>
 * Implementation Note: since cursors are thin wrappers around
 * {@link XMLStreamReader2},
 * and since not all Stax implementations implement
 * {@link XMLStreamReader2}, some wrapping may be involved in exposing
 * basic Stax 1.0 stream readers as Stax2 stream readers.
 * Without native support, not all stax2 features may be available,
 * but cursors will try to limit their usage to known working subset.
 *
 * @author Tatu Saloranta
 */
public abstract class SMInputCursor
    extends CursorBase
{
    /*
    ////////////////////////////////////////////
    // Constants, tracking
    ////////////////////////////////////////////
     */

    // // // Constants for element tracking:

    /**
     * Different tracking behaviors available for cursors.
     * Tracking is a feature that can be used to store
     * information about traversed sub-trees, to allow for a limited
     * access to information that is not limited to ancestor stack.
     * Using tracking will consume more memory, but generally less
     * than constructing a full in-memory tree object model (such
     * as DOM), since it the represenation is compact, read-only,
     * and only subset of a full tree. Size (and hence memory overhead)
     * of that sub-tree depends on tracking settings.
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
    // Configuration
    ////////////////////////////////////////////
     */

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
        super(sr, (parent == null) ? 0 : sr.getDepth());
        mFilter = filter;
        /* By default, we use parent cursor's element tracking setting;
         * or "no tracking" if we have no parent
         */
        if (parent == null) {
            mElemTracking = Tracking.NONE;
            mParentTrackedElement = null;
            mElemInfoFactory = null;
        } else {
            mElemTracking = parent.getElementTracking();
            mParentTrackedElement = parent.getTrackedElement();
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
     *<p>
     * See also {@link #getPathDesc} for information on how
     * to display tracked path/element information.
     */
    public final void setElementTracking(Tracking tracking) {
        mElemTracking = tracking;
    }

    public final Tracking getElementTracking() {
        return mElemTracking;
    }

    /**
     * Set element info factory used for constructing
     * {@link SMElementInfo} instances during traversal for this
     * cursor, as well as all of its children.
     */
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
     * Method to access number of nodes cursor has traversed
     * (including ones that were filtered out, if any).
     * Starts with 0, and is incremented each time
     *   underlying stream reader's {@link XMLStreamReader#next} method
     *   is called, but not counting child cursors' node counts.
     *
     * @return Number of nodes (events) cursor has traversed
     */
    public int getNodeCount() {
        return mNodeCount;
    }

    /**
     * Method to access number of start elements cursor has traversed
     * (including ones that were filtered out, if any).
     * Starts with 0, and is incremented each time
     * underlying stream reader's {@link XMLStreamReader#next} method
     * is called and has moved over a start element, but not counting
     * child cursors' element counts.
     *
     * @return Number of start elements cursor has traversed
     */
    public int getElementCount() {
        return mElemCount;
    }

    /**
     * @deprecated Use {@link #getParentCount()} instead
     */
    @Deprecated
    public final int getDepth() {
        return getParentCount();
    }

    /**
     * Number of parent elements that the token/event cursor points to has,
     * if it points to one. If not, either most recent valid parent
     * count (if cursor is closed), or the depth that it will have
     * once is is advanced. One practical result is that a nested
     * cursor instance will always have a single constant value it
     * returns, whereas flattening cursors can return different
     * values during traversal. Another thing to notice that matching
     * START_ELEMENT and END_ELEMENT will always correspond to the
     * same parent count.
     *<p>
     * For example, here are expected return values
     * for an example XML document:
     *<pre>
     *  &lt;!-- Comment outside tree --> [0]
     *  &lt;root> [0]
     *    Text [1]
     *    &lt;branch> [1]
     *      Inner text [2]
     *      &lt;child /> [2]/[2]
     *    &lt;/branch> [1]
     *  &lt;/root> [0]
     *</pre>
     * Numbers in bracket are depths that would be returned when the
     * cursor points to the node.
     *<p>
     * Note: depths are different from what many other xml processing
     * APIs (such as Stax and XmlPull)return.
     *
     * @return Number of enclosing nesting levels, ie. number of parent
     *   start elements for the node that cursor currently points to (or,
     *   in case of initial state, that it will point to if scope has
     *   node(s)).
     */
    public abstract int getParentCount();

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

    /**
     * @return True if this cursor iterates over root level of
     *   the underlying stream reader
     */
    public final boolean isRootCursor() {
        return (mBaseDepth == 0);
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

    /**
     * Method that can be used to check whether this cursor is
     * currently valid; that is, it is the cursor that points
     * to the event underlying stream is at. Only one cursor
     * at any given time is valid in this sense, although other
     * cursors may be made valid by advancing them (and by process
     * invalidating the cursor that was valid at that point).
     * It is also possible that none of cursors is valid at
     * some point: this is the case when formerly valid cursor
     * reached end of its contet (END_ELEMENT).
     *
     * @return True if the cursor is currently valid; false if not
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

    /**
     * Method to access starting Location of event (as defined by Stax
     * specification)
     * that this cursor points to.
     * Method can only be called if the
     * cursor is valid (as per {@link #readerAccessible}); if not,
     * an exception is thrown
     *
     * @return Location of the event this cursor points to
     */
    public Location getCursorLocation()
        throws XMLStreamException
    {
        if (!readerAccessible()) {
            throw notAccessible("getLocation");
        }
        // Let's try to get actual exact location via Stax2 first:
        LocationInfo li = mStreamReader.getLocationInfo();
        if (li != null) {
            Location loc = li.getStartLocation();
            if (loc != null) {
                return loc;
            }
        }
        // If not, fall back to regular method
        return mStreamReader.getLocation();
    }

    /**
     * Method to access Location that the underlying stream reader points
     * to.
     *
     * @return Location of the event the underlying stream reader points
     *   to (independent of whether this cursor points to that event)
     */
    public Location getStreamLocation()
    {
        // Let's try to get actual exact location via Stax2 first:
        LocationInfo li = mStreamReader.getLocationInfo();
        if (li != null) {
            Location loc = li.getCurrentLocation();
            if (loc != null) {
                return loc;
            }
        }
        // If not, fall back to regular method
        return mStreamReader.getLocation();
    }

    /**
     * Same as calling {@link #getCursorLocation}
     *
     * @deprecated
     */
    @Deprecated
    public Location getLocation()
        throws XMLStreamException
    {
        if (!readerAccessible()) {
            throw notAccessible("getLocation");
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
            throw notAccessible("getText");
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
            throw notAccessible("getText");
        }
        if (getCurrEvent() != SMEvent.START_ELEMENT) {
            throw constructStreamException("Can not call 'getText()' when cursor is not positioned over START_ELEMENT (current event "+currentEventStr()+")"); 
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
            throw notAccessible("getName");
        }
        return mStreamReader.getName();
    }

    /**
     * For events with fully qualified names (START_ELEMENT, END_ELEMENT,
     * ATTRIBUTE, NAMESPACE) returns the local component of the full
     * name; for events with only non-qualified name (PROCESSING_INSTRUCTION,
     * entity and notation declarations, references) returns the name, and
     * for other events, returns null.
     *
     * @return Local component of the name
     */
    public String getLocalName()
        throws XMLStreamException
    {
        if (!readerAccessible()) {
            throw notAccessible("getLocalName");
        }
        switch (getCurrEventCode()) {
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

    /**
     * Method for accessing namespace prefix of the START_ELEMENT this
     * cursor points to.
     *
     * @return Prefix of currently pointed-to START_ELEMENT,
     *   if it has one; "" if none
     *
     * @throws XMLStreamException if cursor does not point to START_ELEMENT
     */
    public String getPrefix()
        throws XMLStreamException
    {
        if (!readerAccessible()) {
            throw notAccessible("getPrefix");
        }
        String prefix = mStreamReader.getPrefix();
        // some impls may return null instead, let's convert
        return (prefix == null) ? "" : prefix;
    }

    /**
     * Method for accessing namespace URI of the START_ELEMENT this
     * cursor points to.
     *
     * @return Namespace URI of currently pointed-to START_ELEMENT,
     *   if it has one; "" if none
     *
     * @throws XMLStreamException if cursor does not point to START_ELEMENT
     */
    public String getNsUri()
        throws XMLStreamException
    {
        if (!readerAccessible()) {
            throw notAccessible("getNsUri");
        }
        String uri = mStreamReader.getNamespaceURI();
        // some impls may return null instead, let's convert
        return (uri == null) ? "" : uri;
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
            throw notAccessible("getPrefixedName");
        }
        return mStreamReader.getPrefixedName();
    }

    /**
     * Method for verifying whether current named event (one for which
     * {@link #getLocalName} can be called)
     * has the specified local name or not.
     *
     * @return True if the local name associated with the event is
     *   as expected
     */
    public boolean hasLocalName(String expName)
        throws XMLStreamException
    {
        if (!readerAccessible()) {
            throw notAccessible("hasName");
        }
        if (expName == null) {
            throw new IllegalArgumentException("Can not pass null name to method");
        }
        String name = getLocalName();
        return (name != null) && expName.equals(name);
    }

    /**
     * Method for verifying whether current named event (one for which
     * {@link #getLocalName} can be called) has the specified
     * fully-qualified name or not.
     * Both namespace URI and local name must match for the result
     * to be true.
     *
     * @return True if the fully-qualified name associated with the event is
     *   as expected
     */
    public boolean hasName(String expNsURI, String expLN)
        throws XMLStreamException
    {
        if (!readerAccessible()) {
            throw notAccessible("hasName");
        }

        int type = getCurrEventCode();
        String uri;
        String ln;

        switch (type) {
        case XMLStreamConstants.START_ELEMENT:
        case XMLStreamConstants.END_ELEMENT:
            ln = mStreamReader.getLocalName();
            uri = mStreamReader.getNamespaceURI();
            break;

        case XMLStreamConstants.ENTITY_REFERENCE:
            ln = mStreamReader.getLocalName();
            uri = null;
            break;
        case XMLStreamConstants.PROCESSING_INSTRUCTION:
            ln = mStreamReader.getPITarget();
            uri = null;
            break;
        case XMLStreamConstants.DTD:
            {
                DTDInfo dtd = mStreamReader.getDTDInfo();
                ln = (dtd == null) ? null : dtd.getDTDRootName();
            }
            uri = null;
            break;
        default:
            return false;
        }

        if (ln == null || !ln.equals(expLN)) {
            return false;
        }
        if (expNsURI == null || expNsURI.length() == 0) { // no namespace
            return (uri == null) || (uri.length() == 0);
        }

        return (uri != null) && expNsURI.equals(uri);
    }

    /*
    ////////////////////////////////////////////////////
    // Public API, accessing current element's attribute
    // information
    ////////////////////////////////////////////////////
     */

    /**
     * Method that can be called when this cursor points to START_ELEMENT,
     * and which will return number of attributes with values for the
     * start element. This includes both explicit attribute values and
     * possible implied default values (when DTD support is enabled
     * by the underlying stream reader).
     *
     * @throws XMLStreamException if either the underlying parser has
     *   problems (cursor not valid or not pointing to START_ELEMENT)
     */
    public int getAttrCount()
        throws XMLStreamException
    {
        if (!readerAccessible()) {
            throw notAccessible("getAttrCount");
        }
        return mStreamReader.getAttributeCount();
    }

    /**
     * Method that can be called when this cursor points to START_ELEMENT,
     * and which will return index of specified attribute, if it
     * exists for this element. If not, -1 is returned to denote "not found".
     *
     * @throws XMLStreamException if either the underlying parser has
     *   problems (cursor not valid or not pointing to START_ELEMENT)
     */
    public int findAttrIndex(String uri, String localName)
        throws XMLStreamException
    {
        if (!readerAccessible()) {
            throw notAccessible("getAttrCount");
        }
        return mStreamReader.getAttributeInfo().findAttributeIndex(uri, localName);
    }

    /**
     * Method that can be called when this cursor points to START_ELEMENT,
     * and returns fully qualified name
     * of the attribute at specified index.
     * Index has to be between [0, {@link #getAttrCount}[; otherwise
     * {@link IllegalArgumentException} will be thrown.
     * 
     * @param index Index of the attribute
     *
     * @throws XMLStreamException if either the underlying parser has
     *   problems (cursor not valid or not pointing to START_ELEMENT),
     *   or if invalid attribute 
     * @throws IllegalArgumentException if attribute index is invalid
     *   (less than 0 or greater than the last valid index
     *   [getAttributeCount()-1])
     */
    public QName getAttrName(int index)
        throws XMLStreamException
    {
        if (!readerAccessible()) {
            throw notAccessible("getAttrName");
        }
        return mStreamReader.getAttributeName(index);
    }

    /**
     * Method that can be called when this cursor points to START_ELEMENT,
     * and returns local name
     * of the attribute at specified index.
     * Index has to be between [0, {@link #getAttrCount}[; otherwise
     * {@link IllegalArgumentException} will be thrown.
     * 
     * @param index Index of the attribute
     *
     * @throws XMLStreamException if either the underlying parser has
     *   problems (cursor not valid or not pointing to START_ELEMENT),
     *   or if invalid attribute 
     * @throws IllegalArgumentException if attribute index is invalid
     *   (less than 0 or greater than the last valid index
     *   [getAttributeCount()-1])
     */
    public String getAttrLocalName(int index)
        throws XMLStreamException
    {
        if (!readerAccessible()) {
            throw notAccessible("getAttrLocalName");
        }
        return mStreamReader.getAttributeLocalName(index);
    }

    /**
     * Method that can be called when this cursor points to START_ELEMENT,
     * and returns namespace prefix
     * of the attribute at specified index (if it has any), or
     * empty String if attribute has no prefix (does not belong to
     * a namespace).
     * Index has to be between [0, {@link #getAttrCount}[; otherwise
     * {@link IllegalArgumentException} will be thrown.
     * 
     * @param index Index of the attribute
     *
     * @throws XMLStreamException if either the underlying parser has
     *   problems (cursor not valid or not pointing to START_ELEMENT),
     *   or if invalid attribute 
     * @throws IllegalArgumentException if attribute index is invalid
     *   (less than 0 or greater than the last valid index
     *   [getAttributeCount()-1])
     */
    public String getAttrPrefix(int index)
        throws XMLStreamException
    {
        if (!readerAccessible()) {
            throw notAccessible("getAttrPrefix");
        }
        String prefix = mStreamReader.getAttributePrefix(index);
        // some impls may return null instead, let's convert
        return (prefix == null) ? "" : prefix;
    }

    /**
     * Method that can be called when this cursor points to START_ELEMENT,
     * and returns namespace URI
     * of the attribute at specified index (non-empty String if it has
     * one, and empty String if attribute does not belong to a namespace)
     * Index has to be between [0, {@link #getAttrCount}[; otherwise
     * {@link IllegalArgumentException} will be thrown.
     * 
     * @param index Index of the attribute
     *
     * @throws XMLStreamException if either the underlying parser has
     *   problems (cursor not valid or not pointing to START_ELEMENT),
     *   or if invalid attribute 
     * @throws IllegalArgumentException if attribute index is invalid
     *   (less than 0 or greater than the last valid index
     *   [getAttributeCount()-1])
     */
    public String getAttrNsUri(int index)
        throws XMLStreamException
    {
        if (!readerAccessible()) {
            throw notAccessible("getAttrNsUri");
        }
        String uri = mStreamReader.getAttributeNamespace(index);
        // some impls may return null instead, let's convert
        return (uri == null) ? "" : uri;
    }

    /**
     * Method that can be called when this cursor points to START_ELEMENT,
     * and returns unmodified textual value
     * of the attribute at specified index (non-empty String if it has
     * one, and empty String if attribute does not belong to a namespace)
     * Index has to be between [0, {@link #getAttrCount}[; otherwise
     * {@link IllegalArgumentException} will be thrown.
     * 
     * @param index Index of the attribute
     *
     * @throws XMLStreamException if either the underlying parser has
     *   problems (cursor not valid or not pointing to START_ELEMENT),
     *   or if invalid attribute 
     * @throws IllegalArgumentException if attribute index is invalid
     *   (less than 0 or greater than the last valid index
     *   [getAttributeCount()-1])
     */
    public String getAttrValue(int index)
        throws XMLStreamException
    {
        if (!readerAccessible()) {
            throw notAccessible("getAttributeValue");
        }
        return mStreamReader.getAttributeValue(index);
    }

    /**
     * Convenience accessor method to access an attribute that is
     * not in a namespace (has no prefix). Equivalent to
     * calling {@link #getAttrValue(String,String)} with
     * 'null' for 'namespace URI' argument
     */
    public String getAttrValue(String localName)
        throws XMLStreamException
    {
        if (!readerAccessible()) {
            throw notAccessible("getAttributeValue");
        }
        /* If we are to believe StAX specs, null would mean "do not
         * check namespace" -- that's pretty much never what anyone
         * really wants (or, at least should use), so let's pass
         * "" to indicate "no namespace"
         */
        /* 16-Jun-2008, tatu: Alas, Sun sjsxp doesn't seem to work
         *   well if we do pass "" instead of null! Since Woodstox
         *   works ok with both, let's use null -- specs are irrelevant
         *   if no implementation follows this particular quirk.
         */
        //return mStreamReader.getAttributeValue("", localName);
        return mStreamReader.getAttributeValue(null, localName);
    }

    /**
     * Method that can be called when this cursor points to START_ELEMENT,
     * and returns unmodified textual value
     * of the specified attribute (if element has it), or null if
     * element has no value for such attribute.
     * 
     * @param namespaceURI Namespace URI for the attribute, if any;
     *   empty String or null if none.
     * @param localName Local name of the attribute to access (in
     *   namespace-aware mode: in non-namespace-aware mode, needs to
     *   be the full name)
     *
     * @throws XMLStreamException if either the underlying parser has
     *   problems (cursor not valid or not pointing to START_ELEMENT),
     *   or if invalid attribute 
     */
    public String getAttrValue(String namespaceURI, String localName)
        throws XMLStreamException
    {
        if (!readerAccessible()) {
            throw notAccessible("getAttrValue");
        }
        return mStreamReader.getAttributeValue(namespaceURI, localName);
    }

    /*
    ////////////////////////////////////////////////////
    // Public API, Typed Access API for attributes
    ////////////////////////////////////////////////////
     */

    /**
     * Method for accessing value of specified attribute as boolean.
     * Method will only succeed if the attribute value is a valid
     * boolean, as specified by XML Schema specification (and hence
     * is accessible via Stax2 Typed Access API).
     *
     * @param index Index of attribute to access
     *
     * @throws XMLStreamException If specified attribute can not be
     *   accessed (due to cursor state), or if attribute value
     *   is not a valid textual representation of boolean
     * @throws IllegalArgumentException If given attribute index is invalid
     */
    public boolean getAttrBooleanValue(int index)
        throws NumberFormatException, XMLStreamException
    {
        if (!readerAccessible()) {
            throw notAccessible("getAttrBooleanValue");
        }
        /* For now, let's just get it as String and convert: in future,
         * may be able to use more efficient access method(s)
         */
        String value = mStreamReader.getAttributeValue(index);
        try {
            return DataUtil.parseBoolean(value);
        } catch (IllegalArgumentException iae) {
            throw constructStreamException("Attribute #"+index+" value not numeric: "+iae.getMessage());
        }
    }

    /**
     * Method for accessing value of specified attribute as boolean.
     * If attribute value is not a valid boolean
     * (as specified by XML Schema specification), will instead
     * return specified "default value".
     *
     * @param index Index of attribute to access
     * @param defValue Value to return if attribute value exists but
     *   is not a valid boolean value
     *
     * @throws XMLStreamException If specified attribute can not be
     *   accessed (due to cursor state), or if attribute value
     *   is not a valid textual representation of boolean.
     * @throws IllegalArgumentException If given attribute index
     *   is invalid
     */
    public boolean getAttrBooleanValue(int index, boolean defValue)
        throws NumberFormatException, XMLStreamException
    {
        if (!readerAccessible()) {
            throw notAccessible("getAttrBooleanValue");
        }
        /* For now, let's just get it as String and convert: in future,
         * may be able to use more efficient access method(s)
         */
        return DataUtil.parseBoolean(mStreamReader.getAttributeValue(index), defValue);
    }

    /**
     * Method for accessing value of specified attribute as integer.
     * Method will only succeed if the attribute value is a valid
     * integer, as specified by XML Schema specification (and hence
     * is accessible via Stax2 Typed Access API).
     *
     * @param index Index of attribute to access
     *
     * @throws XMLStreamException If specified attribute can not be
     *   accessed (due to cursor state), or if attribute value
     *   is not a valid textual representation of integer.
     * @throws IllegalArgumentException If given attribute index
     *   is invalid
     */
    public int getAttrIntValue(int index)
        throws NumberFormatException, XMLStreamException
    {
        if (!readerAccessible()) {
            throw notAccessible("getAttrIntValue");
        }
        /* For now, let's just get it as String and convert: in future,
         * may be able to use more efficient access method(s)
         */
        String value = mStreamReader.getAttributeValue(index);
        try {
            return DataUtil.parseInt(value);
        } catch (IllegalArgumentException iae) {
            throw constructStreamException("Attribute #"+index+" value not numeric: "+iae.getMessage());
        }
    }

    /**
     * Method for accessing value of specified attribute as integer.
     * If attribute value is not a valid integer
     * (as specified by XML Schema specification), will instead
     * return specified "default value".
     *
     * @param index Index of attribute to access
     * @param defValue Value to return if attribute value exists but
     *   is not a valid integer value
     *
     * @throws XMLStreamException If specified attribute can not be
     *   accessed (due to cursor state), or if attribute value
     *   is not a valid textual representation of integer.
     * @throws IllegalArgumentException If given attribute index
     *   is invalid
     */
    public int getAttrIntValue(int index, int defValue)
        throws NumberFormatException, XMLStreamException
    {
        if (!readerAccessible()) {
            throw notAccessible("getAttrIntValue");
        }
        /* For now, let's just get it as String and convert: in future,
         * may be able to use more efficient access method(s)
         */
        return DataUtil.parseInt(mStreamReader.getAttributeValue(index), defValue);
    }

    /**
     * Method for accessing value of specified attribute as long.
     * Method will only succeed if the attribute value is a valid
     * long, as specified by XML Schema specification (and hence
     * is accessible via Stax2 Typed Access API).
     *
     * @param index Index of attribute to access
     *
     * @throws XMLStreamException If specified attribute can not be
     *   accessed (due to cursor state), or if attribute value
     *   is not a valid textual representation of long.
     * @throws IllegalArgumentException If given attribute index
     *   is invalid
     */
    public long getAttrLongValue(int index)
        throws NumberFormatException, XMLStreamException
    {
        if (!readerAccessible()) {
            throw notAccessible("getAttrLongValue");
        }
        /* For now, let's just get it as String and convert: in future,
         * may be able to use more efficient access method(s)
         */
        String value = mStreamReader.getAttributeValue(index);
        try {
            return DataUtil.parseLong(value);
        } catch (IllegalArgumentException iae) {
            throw constructStreamException("Attribute #"+index+" value not numeric: "+iae.getMessage());
        }
    }

    /**
     * Method for accessing value of specified attribute as long.
     * If attribute value is not a valid long
     * (as specified by XML Schema specification), will instead
     * return specified "default value".
     *
     * @param index Index of attribute to access
     * @param defValue Value to return if attribute value exists but
     *   is not a valid long value
     *
     * @throws XMLStreamException If specified attribute can not be
     *   accessed (due to cursor state), or if attribute value
     *   is not a valid textual representation of long.
     * @throws IllegalArgumentException If given attribute index
     *   is invalid
     */
    public long getAttrLongValue(int index, long defValue)
        throws NumberFormatException, XMLStreamException
    {
        if (!readerAccessible()) {
            throw notAccessible("getAttrLongValue");
        }
        /* For now, let's just get it as String and convert: in future,
         * may be able to use more efficient access method(s)
         */
        return DataUtil.parseLong(mStreamReader.getAttributeValue(index), defValue);
    }

    /*
    ////////////////////////////////////////////////////
    // Deprecated data access
    ////////////////////////////////////////////////////
     */

    /**
     * @deprecated Use combination of {@link #findAttrIndex} and
     *   {@link #getAttrIntValue(int)} instead.
     */
    @Deprecated
    public int getAttrIntValue(String uri, String localName)
        throws XMLStreamException
    {
        if (!readerAccessible()) {
            throw notAccessible("getAttrIntValue");
        }
        /* For now, let's just get it as String and convert: in future,
         * may be able to use more efficient access method(s)
         */
        String value = mStreamReader.getAttributeValue(uri, localName);
        return DataUtil.parseInt(value);
    }

    /**
     * @deprecated Use combination of {@link #findAttrIndex} and
     *   {@link #getAttrIntValue(int,int)} instead.
     */
    @Deprecated
    public int getAttrIntValue(String uri, String localName, int defValue)
        throws XMLStreamException
    {
        if (!readerAccessible()) {
            throw notAccessible("getAttrIntValue");
        }
        /* For now, let's just get it as String and convert: in future,
         * may be able to use more efficient access method(s)
         */
        String valueStr = mStreamReader.getAttributeValue(uri, localName);
        return DataUtil.parseInt(valueStr, defValue);
    }

    /*
    ////////////////////////////////////////////////////
    // Public API, Typed Access API for element values
    ////////////////////////////////////////////////////
     */

    /*
    ////////////////////////////////////////////////
    // Public API, accessing extra application data
    ////////////////////////////////////////////////
     */

    /**
     * Method for accessing application-provided data set previously
     * by a {@link #setData} call.
     */
    public Object getData() {
        return mData;
    }

    /**
     * Method for assigning per-cursor application-managed data,
     * readable using {@link #getData}.
     */
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
     * currently points to that are passed by the specified filter.
     * If cursor does not point to a start element,
     * it will throw {@link IllegalStateException}; if it does not support
     * concept of child cursors, it will throw
     * {@link UnsupportedOperationException}
     *
     * @param f Filter child cursor is to use for filtering out
     *    'unwanted' nodes; may be null if no filtering is to be done
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

    /**
     * Method that will create a new nested cursor for iterating
     * over all (immediate) child nodes of the start element this cursor
     * currently points to.
     * If cursor does not point to a start element,
     * it will throw {@link IllegalStateException}; if it does not support
     * concept of child cursors, it will throw
     * {@link UnsupportedOperationException}
     *
     * @throws IllegalStateException If cursor can not be created due
     *   to the state cursor is in.
     * @throws UnsupportedOperationException If cursor does not allow
     *   creation of child cursors.
     */
    public final SMInputCursor childCursor()
        throws XMLStreamException
    {
        return childCursor(null);
    }

    /**
     * Method that will create a new nested cursor for iterating
     * over all the descendant (children and grandchildren) nodes of
     * the start element this cursor currently points to
     * that are accepted by the specified filter.
     * If cursor does not point to a start element,
     * it will throw {@link IllegalStateException}; if it does not support
     * concept of descendant cursors, it will throw
     * {@link UnsupportedOperationException}
     *
     *
     * @param f Filter child cursor is to use for filtering out
     *    'unwanted' nodes; may be null if no filtering is to be done
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
    /////////////////////////////////////////////////////////////
    // Public API, convenience methods for exception construction
    /////////////////////////////////////////////////////////////
     */

    /**
     * Method for constructing stream exception with given message,
     * and location that matches that of the underlying stream
     *<b>regardless of whether this cursor is valid</b> (i.e.
     * will indicate location of the stream which may differ from
     * where this cursor was last valid)
     */
    public XMLStreamException constructStreamException(String msg)
    {
        // !!! TODO: use StaxMate-specific sub-classes of XMLStreamException?
        return new XMLStreamException(msg, getStreamLocation());
    }

    /**
     * Method for constructing and throwing stream exception with given
     * message. Equivalent to throwing exception that
     * {@link #constructStreamException} constructs and returns.
     */
    public void throwStreamException(String msg)
        throws XMLStreamException
    {
        throw constructStreamException(msg);
    }

    /*
    /////////////////////////////////////////////////////////////
    // Public API, dev-readable descs
    /////////////////////////////////////////////////////////////
     */

    /**
     * Method that generates developer-readable description of
     * the logical path of the event this cursor points to,
     * assuming that <b>element tracking</b> is enabled.
     * If it is, a path description will be constructed; if not,
     * result will be "." ("unspecified current location").
     *<p>
     * Note: while results look similar to XPath expressions,
     * they are not accurate (or even valid) XPath. 
     * This is partly because of filtering, and partly because
     * of differences between element/node index calculation.
     * The idea is to make it easier to get reasonable idea
     * of logical location, in addition to physical input location.
     */
    public String getPathDesc()
    {
        /* Need to start with parent, since current element may
         * or may not exist (depeneding on traversal)?
         */
        SMElementInfo parent = getParentTrackedElement();
        // Not tracking, or not just yet advanced?
        if (parent == null && getElementTracking() == Tracking.NONE) {
            return ".";
        }
        StringBuilder sb = new StringBuilder(100);

        appendPathDesc(sb, parent, true);

        /* Let's indicate index of the current node; whether to indicate
         * via element or node index depend on whether it's a start/end
         * element (and one for which we have info) or not
         */
        SMElementInfo curr = getTrackedElement();
        if (curr != null && getCurrEvent() == SMEvent.START_ELEMENT) {
            appendPathDesc(sb, mTrackedElement, false);
        } else {
            sb.append("/*[n").append(getNodeCount()).append(']');
        }
        return sb.toString();
    }

    private static void appendPathDesc(StringBuilder sb, SMElementInfo info,
                                       boolean recursive)
    {
        if (info == null) {
            return;
        }
        if (recursive) {
            appendPathDesc(sb, info.getParent(), true);
        }
        sb.append('/');
        String prefix = info.getPrefix();
        if (prefix != null && prefix.length() > 0) {
            sb.append(prefix);
            sb.append(':');
        }
        sb.append(info.getLocalName());
        // and let's indicate relative element-index of the element
        sb.append("[e").append(info.getElementIndex()).append(']');
    }

    protected String getCurrEventDesc() {
        return mCurrEvent.toString();
    }

    @Override
        public String toString() {
        return "[Cursor that point(s/ed) to: "+getCurrEventDesc()+"]";
    }

    /*
    ////////////////////////////////////////////
    // Methods sub-classes need or can override
    // to customize behaviour:
    ////////////////////////////////////////////
     */

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
}
