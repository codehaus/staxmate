package org.codehaus.staxmate.in;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

import org.codehaus.stax2.XMLStreamReader2;

/**
 * Default implementation of generic nested (scoped) cursor; cursor that only
 * traverses direct children of a single start element.
 * 
 * @author Tatu Saloranta
 */
public class SMHierarchicCursor
    extends SMInputCursor
{
    /*
    ////////////////////////////////////////////
    // Life cycle
    ////////////////////////////////////////////
     */

    public SMHierarchicCursor(SMInputCursor parent, XMLStreamReader2 sr, SMFilter f)
    {
        super(parent, sr, f);
    }

    /*
    ///////////////////////////////////////////////////
    // Public API, accessing cursor state information
    ///////////////////////////////////////////////////
     */

    public int getDepth() {
        return mParentCount;
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
        // If there is a child cursor, it has to be traversed through
        if (mState == State.HAS_CHILD) {
            mChildCursor.skipTree();
            mChildCursor = null;
            mState = State.ACTIVE;
        } else if (mState == State.INITIAL) {
            mState = State.ACTIVE;
        } else { // active
            // If we had a start element, need to skip the subtree...
            if (mCurrEvent == SMEvent.START_ELEMENT) {
                skipSubTree(0);
            }
        }

        while (true) {
            int type;
            
            // Root level has no end element...
            if (isRootCursor()) {
                if (!mStreamReader.hasNext()) {
                    break;
                }
                type = mStreamReader.next();
                /* Document end marker at root level is same as end element
                 * at inner levels...
                 */
                if (type == XMLStreamConstants.END_DOCUMENT) {
                    break;
                }
            } else {
                type = mStreamReader.next();
            }
            ++mNodeCount;
            if (type == XMLStreamConstants.END_ELEMENT) {
                break;
            }
            if (type == XMLStreamConstants.START_ELEMENT) {
                ++mElemCount;
            }
            // !!! only here temporarily, shouldn't be needed
            else if (type == XMLStreamConstants.END_DOCUMENT) {
                throw new IllegalStateException("Unexpected END_DOCUMENT encountered (root = "+isRootCursor()+")"); }
            SMEvent evt = sEventsByIds[type];
            mCurrEvent = evt;
            
            // Ok, are we interested in this event?
            if (mFilter != null && !mFilter.accept(evt, this)) {
                /* Nope, let's just skip over; but we may still need to
                 * create the tracked element?
                 */
                if (type == XMLStreamConstants.START_ELEMENT) {
                    if (mElemTracking == Tracking.ALL_SIBLINGS) {
                        mTrackedElement = constructElementInfo(mParentTrackedElement, mTrackedElement);
                    }
                    skipSubTree(0);
                }
                continue;
            }
            
            // Need to update tracked element?
            if (type == XMLStreamConstants.START_ELEMENT && mElemTracking != Tracking.NONE) {
                SMElementInfo prev = (mElemTracking == Tracking.PARENTS) ? null : mTrackedElement;
                mTrackedElement = constructElementInfo(mParentTrackedElement, prev);
            }
            return evt;
        }

        // Ok, no more events
        mState = State.CLOSED;
        mCurrEvent = null;
        return null;
    }

    public SMInputCursor constructChildCursor(SMFilter f)
    {
        return new SMHierarchicCursor(this, mStreamReader, f);
    }

    public SMInputCursor constructDescendantCursor(SMFilter f)
    {
        return new SMFlatteningCursor(this, mStreamReader, f);
    }

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
            skipSubTree(0);
        } else if (state == State.INITIAL) {
            skipSubTree(0);
        } else {
            skipSubTree(1);
        }
    }
}
