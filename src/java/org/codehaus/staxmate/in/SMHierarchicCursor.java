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

    public int getParentCount() {
        return mBaseDepth;
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
            mChildCursor.skipAll();
            mChildCursor = null;
            mState = State.ACTIVE;
        } else if (mState == State.INITIAL) {
            mState = State.ACTIVE;
        } else { // active
            // If we had a start element, need to skip the subtree...
            if (mCurrEvent == SMEvent.START_ELEMENT) {
                skipToEndElement();
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
                    skipToEndElement();
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

    /*
    ////////////////////////////////////////////
    // Internal methods
    ////////////////////////////////////////////
     */

    /**
     * Method called when current event/token is START_ELEMENT, but
     * we are not interested in its contents (children). Hence, needs
     * to skip all intervening events/tokens until matching END_ELEMENT
     * is encountered.
     */
    protected void skipToEndElement()
        throws XMLStreamException
    {
        XMLStreamReader2 sr = mStreamReader;
        // Stax2 guarantees that START_ELEMENT and END_ELEMENT depths match
        int endDepth = sr.getDepth();
        while (true) {
            int type = sr.next();
            if (type == XMLStreamConstants.END_ELEMENT) {
                if (sr.getDepth() <= endDepth) {
                    break;
                }
            } else if (type == XMLStreamConstants.END_DOCUMENT) {
                /* This is just a sanity check, to give more meaningful
                 * error messages in case something weird happens
                 */
                throw new IllegalStateException("Unexpected END_DOCUMENT encountered when hierarchic cursor was skipping element contents");
            }
        }
    }
}
