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
    // Life cycle, configuration
    ////////////////////////////////////////////
     */

    public SMFlatteningCursor(SMInputCursor parent, XMLStreamReader2 sr, SMFilter f)
    {
        super(parent, sr, f);
    }

    /*
    ///////////////////////////////////////////////////
    // Public API, accessing cursor state information
    ///////////////////////////////////////////////////
     */

    public int getParentCount()
    {
        /* First things first: if we have a child cursor, we can not
         * ask stream, since its depth depends on how far child
         * cursor has travelled. But it does know its base parent
         * count, which has to be one bigger than our parent count
         * at time when child cursor was created, which is the latest
         * node this cursor traversed over.
         */
        if (mChildCursor != null) {
            return mChildCursor.getBaseParentCount();
        }
        // No event yet, or we are closed? base depth is ok then
        if (mCurrEvent == null) {
            return mBaseDepth;
        }

        /* Otherwise, stream's count can be used. However, it'll be
         * off by one for both START_ELEMENT and END_ELEMENT.
         */
        int depth = mStreamReader.getDepth();
        if (mCurrEvent == SMEvent.START_ELEMENT
            || mCurrEvent == SMEvent.END_ELEMENT) {
            --depth;
        }
        return depth;
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
            mChildCursor.skipAll();
            mChildCursor = null;
            mState = State.ACTIVE;
        } else if (mState == State.INITIAL) {
            mState = State.ACTIVE;
        } // nothing to do if we are active

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
                /* Base depth was depth at START_ELEMENT, Stax2.getDepth()
                 * will return identical value for END_ELEMENT (and
                 * <= used instead of < just for sanity checking)
                 */
                if (mStreamReader.getDepth() < mBaseDepth) {
                    break;
                }
            } else if (type == XMLStreamConstants.START_ELEMENT) {
                ++mElemCount;

                /* !!! 24-Oct-2007, tatus: This sanity check really
                 *   shouldn't be needed any more... but let's leave
                 *   it for time being
                 */
            } else if (type == XMLStreamConstants.END_DOCUMENT) {
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

    /*
    ////////////////////////////////////////////
    // Internal/package methods
    ////////////////////////////////////////////
     */

}
