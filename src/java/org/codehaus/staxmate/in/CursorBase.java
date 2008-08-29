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
 * Abstract base class used to 'hide' methods so that while they are
 * available for sub-classes ({@link SMInputCursor} and its sub-classes),
 * they are not prominently visible via javadocs.
 *
 * @author Tatu Saloranta
 */
abstract class CursorBase
{
    /*
    ////////////////////////////////////////////
    // Constants, initial cursor state
    ////////////////////////////////////////////
     */

    // // // Constants for the cursor state

    /**
     * State constants are used for keeping track of state of individual
     * cursors.
     */
    public enum State {
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
    private final static SMEvent[] sEventsByIds =
        SMEvent.constructIdToEventMapping();

    /*
    ////////////////////////////////////////////
    // Iteration state
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
     * Depth the underlying stream reader had when this cursor was
     * created (which is the number of currently open parent elements).
     * 0 only for root cursor.
     */
    protected final int mBaseDepth;

    /**
     * Current state of the cursor.
     */
    protected State mState = State.INITIAL;

    /**
     * Event that this cursor currently points to, if valid, or
     * it last pointed to if not (including null if cursor has not
     * yet been advanced).
     */
    protected SMEvent mCurrEvent = null;

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
     * Element that was last "tracked"; element over which cursor was
     * moved, and of which state has been saved for further use. At this
     * point, it can be null if no elements have yet been iterater over.
     * Alternatively, if it's not null, it may be currently pointed to
     * or not; if it's not, either child cursor is active, or this
     * cursor points to a non-start-element node.
     */
    protected SMElementInfo mTrackedElement = null;

    /**
     * Element that the parent of this cursor tracked (if any),
     * when this cursor was created.
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
    // Life-cycle
    ////////////////////////////////////////////
     */

    /**
     * @param baseDepth Base depth (number of enclosing open start elements)
     *   of the underlying stream at point when this cursor was instantiated
     */
    protected CursorBase(XMLStreamReader2 sr, int baseDepth)
    {
        mStreamReader = sr;
        mBaseDepth = baseDepth;
    }

    /*
    ////////////////////////////////////////////
    // Methods we need from sub-class
    ////////////////////////////////////////////
     */

    /**
     * @return Developer-readable description of the event this cursor
     *    currently points to.
     */
    protected abstract String getCurrEventDesc();

    /**
     * @return True if this cursor iterates over root level of
     *   the underlying stream reader
     */
    public abstract boolean isRootCursor();

    public abstract XMLStreamException constructStreamException(String msg);

    public abstract void throwStreamException(String msg)
        throws XMLStreamException;

    /*
    ////////////////////////////////////////////
    // Methods sub-classes need or can override
    // to customize behaviour:
    ////////////////////////////////////////////
     */

    /**
     * This method is needed by flattening cursors when they
     * have child cursors: if so, they can determine their
     * depth relative to child cursor's base parent count
     * (and can not check stream -- as it may have moved --
     * nor want to have separate field to track this information)
     */
    protected final int getBaseParentCount() {
        return mBaseDepth;
    }

    /**
     * Method called to skim through the content that the child
     * cursor(s) are pointing to, end return once next call to
     * XMLStreamReader2.next() will return the next event
     * this cursor should see.
     */
    protected final void rewindPastChild()
        throws XMLStreamException
    {
        final SMInputCursor child = mChildCursor;
        mChildCursor = null;

        child.invalidate();

        /* Base depth to match is always known by the child in question,
         * so let's ask it (hierarchic cursor parent also knows it)
         */
        final int endDepth = child.getBaseParentCount();
        final XMLStreamReader2 sr = mStreamReader;

        for (int type = sr.getEventType(); true; type = sr.next()) {
            if (type == XMLStreamConstants.END_ELEMENT) {
                int depth = sr.getDepth();
                if (depth > endDepth) {
                    continue;
                }
                if (depth != endDepth) { // sanity check
                    throwWrongEndElem(endDepth, depth);
                }
                break;
            } else if (type == XMLStreamConstants.END_DOCUMENT) {
                throwUnexpectedEndDoc();
            }
        }
    }


    /**
     * Method called by the parent cursor, to indicate it has to
     * traverse over xml content and that child cursor as well
     * as all of its descendant cursors (if any) are to be
     * considered invalid.
     */
    protected void invalidate()
        throws XMLStreamException
    {
        mState = State.CLOSED;
        mCurrEvent = null;

        // child cursor(s) to delegate skipping to?
        if (mChildCursor != null) {
            mChildCursor.invalidate();
            mChildCursor = null;
        }
    }

    /*
    ////////////////////////////////////////////
    // Package methods
    ////////////////////////////////////////////
     */

    /**
     *<p>
     * Note: no checks are done regarding validity of passed-in
     * type.
     *
     * @return {@link SMEvent} matching given type
     */
    protected final static SMEvent eventObjectByEventId(int type)
    {
        return sEventsByIds[type];
    }

    /**
     * Internal method for throwing a stream exception that indicates
     * that given method can not be called because the cursor does
     * not point to event of expected type. This can be either because
     * cursor is invalid (doesn't point to any event), or because
     * it points to "wrong" event type. Distinction is reflected
     * in the exception message.
     */
    protected XMLStreamException notAccessible(String method)
        throws XMLStreamException
    {
        if (mChildCursor != null) {
            return constructStreamException("Can not call '"+method+"(): cursor does not point to a valid node, as it has an active open child cursor.");
        }
        return constructStreamException("Can not call '"+method+"(): cursor does not point to a valid node (curr event "+getCurrEventDesc()+"; cursor state "
                  +getStateDesc());
    }

    protected String getStateDesc() {
        return mState.toString();
    }

    /**
     * Method for constructing human-readable description of the event
     * this cursor points to (if cursor valid) or last pointed to (if
     * not valid; possibly null if cursor has not yet been advanced).
     *
     * @return Human-readable description of the underlying Stax event
     *   this cursor points to.
     */
    protected String currentEventStr()
    {
        return (mCurrEvent == null) ? "null" : mCurrEvent.toString();
    }

    void throwUnexpectedEndDoc()
        throws XMLStreamException
    {
        throw new IllegalStateException("Unexpected END_DOCUMENT encountered (root = "+isRootCursor()+")");
    }

    void throwWrongEndElem(int expDepth, int actDepth)
        throws IllegalStateException
    {
        throw new IllegalStateException("Expected to encounter END_ELEMENT with depth >= "+expDepth+", got "+actDepth);
    }

    @Override
    public String toString() {
        return "{" + getClass().getName()+": "+mState+", curr evt: "
            +mCurrEvent+"}";
    }
}
