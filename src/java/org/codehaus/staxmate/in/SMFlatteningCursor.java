package org.codehaus.staxmate.in;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

import org.codehaus.stax2.XMLStreamReader2;

/**
 * Default implementation of generic flat (non-scoped) cursor; cursor
 * that traverse all descendants (children and grandchildren) of a start
 * element.
 *<p>
 * Differences to {@link SMHierarchicCursor} are:
 * <ul>
 *  <li>Flat cursors return {@link XMLStreamConstants#END_ELEMENT} nodes (except
 *    for the one that closes the outermost level), unless
 *    filtered out by the filter, whereas the nested cursor automatically
 *    leaves those out.
 *   </li>
 *  <li>Flat cursors can not have child/descendant cursors
 *   </li>
 * </ul> 
 *
 * @author Tatu Saloranta
 */
public class SMFlatteningCursor
    extends SMInputCursor
{
    /*
    ////////////////////////////////////////////
    // Iteration state
    ////////////////////////////////////////////
     */

    /**
     * Number of nested start elements this element has iterated over,
     * including current one (if pointing to one)
     */
    protected int mNesting;

    /*
    ////////////////////////////////////////////
    // Life cycle, configuration
    ////////////////////////////////////////////
     */

    public SMFlatteningCursor(SMInputCursor parent, XMLStreamReader2 sr, SMFilter f)
    {
        super(parent, sr, f);
        mNesting = 0;
    }

    /*
    ///////////////////////////////////////////////////
    // Public API, accessing cursor state information
    ///////////////////////////////////////////////////
     */

    public int getDepth()
    {
        return mParentCount + mNesting;
    }

    /*
    ////////////////////////////////////////////
    // Public API, iterating
    ////////////////////////////////////////////
     */

    public SMEvent getNext()
        throws XMLStreamException
    {
        if (mState == State.CLOSED) {
            return null;
        }

        /* If there is a child cursor, it has to be traversed
         * through
         */
        if (mState == State.HAS_CHILD) {
            mChildCursor.skipTree();
            mChildCursor = null;
            mState = State.ACTIVE;
        } else if (mState == State.INITIAL) {
            mState = State.ACTIVE;
        } else { // active
            /* Start element parent count can only be updated AFTER the node
             * has been observed; whereas end element has to be updated
             * when (AFTER) observed.
             */
            if (mCurrEvent == SMEvent.START_ELEMENT) {
                ++mNesting;
            }
        }

        while (true) {
            int type;

            /* Root level has no end element; should always get END_DOCUMENT,
             * but let's be extra careful... (maybe there's need for fragment
             * cursors later on)
             */
            if (isRootCursor()) {
                if (!mStreamReader.hasNext()) {
                    break;
                }
                type = mStreamReader.next();
                /* Document end marker at root level is same as end
                 * element at inner levels...
                 */
                if (type == XMLStreamConstants.END_DOCUMENT) {
                    break;
                }
            } else {
                type = mStreamReader.next();
            }

            ++mNodeCount;

            if (type == XMLStreamConstants.END_ELEMENT) {
                if (--mNesting < 0) { // starts at 0
                    break;
                }
            } else if (type == XMLStreamConstants.START_ELEMENT) {
                ++mNesting;
                ++mElemCount;
            }
            // !!! only here temporarily, shouldn't be needed
            else if (type == XMLStreamConstants.END_DOCUMENT) {
                throw new IllegalStateException("Unexpected END_DOCUMENT encountered (root = "+isRootCursor()+")");
            }

            SMEvent evt = sEventsByIds[type];
            mCurrEvent = evt;

            // Ok, are we interested in this event?
            if (mFilter != null && !mFilter.accept(evt, this)) {
                // Nope, let's just skip over

                // May still need to create the tracked element?
                if (type == XMLStreamConstants.START_ELEMENT) { 
                    if (mElemTracking == Tracking.ALL_SIBLINGS) {
                        mTrackedElement = constructElementInfo
                            (mParentTrackedElement, mTrackedElement);
                    }
                }
                continue;
            }

            // Need to update tracked element?
            if (type == XMLStreamConstants.START_ELEMENT
                && mElemTracking != Tracking.NONE) {
                SMElementInfo prev = (mElemTracking == Tracking.PARENTS) ?
                    null : mTrackedElement;
                mTrackedElement = constructElementInfo
                    (mParentTrackedElement, prev);
            }
            return evt;
        }

        // Ok, no more events
        mState = State.CLOSED;
        mCurrEvent = null;
        return null;
    }

    public SMInputCursor constructChildCursor(SMFilter f) {
        return new SMHierarchicCursor(this, mStreamReader, f);
    }

    public SMInputCursor constructDescendantCursor(SMFilter f) {
        return new SMFlatteningCursor(this, mStreamReader, f);
    }

    /**
     * Method called by the parent cursor, to skip over the scope
     * this cursor iterates, and of its sub-scopes if any.
     *<p>
     * Note: implementation differs from that of non-flattening cursor
     * mostly since we may be deeper down in the tree already, and
     * thus may need to encounter multiple end tags.
     */
    @Override
    protected void skipTree()
        throws XMLStreamException
    {
        if (mState == State.CLOSED) { // already finished?
            return;
        }
        State state = mState;
        mState = State.CLOSED;
        mCurrEvent = null;

        // child cursor(s) to delegate skipping to?
        if (state == State.HAS_CHILD) {
            mChildCursor.skipTree();
            mChildCursor = null;
        }

        int depth = mNesting;
        while (true) {
            int type = mStreamReader.next();
            if (type == XMLStreamConstants.START_ELEMENT) {
                ++depth;
            } else if (type == XMLStreamConstants.END_ELEMENT) {
                if (--depth < 0) {
                    break;
                }
            } else if (type == XMLStreamConstants.END_DOCUMENT) { // sanity check
                // An error...
                throw new IllegalStateException("Unexpected END_DOCUMENT encountered when skipping a sub-tree.");
            }
        }
    }

    /*
    ////////////////////////////////////////////
    // Internal/package methods
    ////////////////////////////////////////////
     */

}
