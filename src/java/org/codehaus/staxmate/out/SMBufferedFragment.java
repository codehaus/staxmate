package org.codehaus.staxmate.out;

import javax.xml.stream.XMLStreamException;

/**
 * Buffered fragment; starts its life buffered,
 * so that its content are not automatically written to the underlying
 * stream, but only when buffered instance is released. Once released,
 * can not be buffered again.
 */
public final class SMBufferedFragment
    extends SMOutputContainer
    implements SMBufferable
{
    /* These are the distinct states:
     *
     * BUFFERED_AND_BLOCKED: initial state, where output is blocked both
     *   by this fragment being buffered, AND parent being blocked by
     *   some other buffering.
     * BUFFERED: initial state, where output is only blocked due to the
     *   fragment itself being buffered.
     * BLOCKED: optional state; will be moved here if fragment is unbuffered
     *   but parent has not yet indicate it is ready to be unblocked
     * OPEN: state in which we can freely output children
     * CLOSED: state during which no children can be added any more
     */
    protected final static int STATE_BUFFERED_AND_BLOCKED = 1;
    protected final static int STATE_BUFFERED = 2;
    protected final static int STATE_BLOCKED = 3;
    protected final static int STATE_OPEN = 4;
    protected final static int STATE_CLOSED = 5;

    protected final static int LAST_BUFFERED = STATE_BUFFERED;
    protected final static int LAST_BLOCKED = STATE_BLOCKED;

    /**
     * All instances are initially buffered; state will be changed when
     * instance is released (and further on with other changes)
     */
    protected int mState = STATE_BUFFERED;

    protected SMBufferedFragment(SMOutputContext ctxt)
    {
        super(ctxt);
        
    }

    /*
    ///////////////////////////////////////////////////////////
    // SMBufferable implementation
    ///////////////////////////////////////////////////////////
    */

    public boolean isBuffered() {
        return (mState <= LAST_BUFFERED);
    }

    public void linkParent(SMOutputContainer parent, boolean blocked)
        throws XMLStreamException
    {
        if (mParent != null) {
            throwRelinking();
        }
        mParent = parent;

        // Ok, which state should we move to?
        if (isBuffered()) { // still buffered
            mState = blocked ? STATE_BUFFERED_AND_BLOCKED : STATE_BUFFERED;
        } else {
            if (blocked) {
                mState = STATE_BLOCKED;
            } else {
                mState = STATE_OPEN;
                /* Ok; now, we also need to try to output as much as we can,
                 * since we are neither buffered nor blocked by parent (may
                 * still be blocked by a child). However, we are not to be
                 * closed as of yet.
                 */
                doOutput(mContext, false);
            }
        }
    }

    public void release()
        throws XMLStreamException
    {
        // Should we complain about duplicate calls?
        if (!isBuffered()) {
            return;
        }

        if (mParent != null) {
            /* May need to update the state first, as parent is likely
             * to call doOutput() when being notified
             */
            mState = (mState == STATE_BUFFERED_AND_BLOCKED) ?
                STATE_BLOCKED : STATE_OPEN;
            mParent.childReleased(this);
        } else {
            // Will be blocked by the fact we haven't yet been linked...
            mState = STATE_BLOCKED;
        }
    }

    /*
    ///////////////////////////////////////////////////////////
    // Abstract method implementations
    ///////////////////////////////////////////////////////////
    */

    protected void childReleased(SMOutputtable child)
        throws XMLStreamException
    {
        // First, if we are buffered, no need to do anything more...
        if (mState <= LAST_BLOCKED) {
            return;
        }
        /* Otherwise, the only significant child is the first one, as it's
         * the only one that may have blocked output:
         */
        if (child == mFirstChild) {
            // If so, parent can (and should) deal with it... if we have one
            if (mParent != null) {
                mParent.childReleased(this);
            }
        }
    }

    protected boolean doOutput(SMOutputContext ctxt, boolean canClose)
        throws XMLStreamException
    {
        // No outputting if still buffered...
        if (mState <= LAST_BLOCKED) {
            return false;
        }
        // And it's an error to get it called after being closed
        if (mState == STATE_CLOSED) {
            throwClosed();
        }
        // Should we try to fully close?
        if (canClose) {
            boolean success = closeAndOutputChildren();
            if (success) { // yup, can indeed fully close
                mState = STATE_CLOSED;
            }
            return success;
        }
        return closeAllButLastChild();
    }

    protected void forceOutput(SMOutputContext ctxt)
        throws XMLStreamException
    {
        mState = STATE_OPEN; // just in case we get a callback from children
        forceChildOutput();
        mState = STATE_CLOSED;
    }

    public boolean canOutputNewChild()
        throws XMLStreamException
    {
        // Can not just output if we are buffered...
        if (mState <= LAST_BLOCKED) {
            return false;
        }
        /* Plus, if we are fully closed, we are not to allow even trying to
         * add anything:
         */
        if (mState == STATE_CLOSED) {
            throwClosed();
        }
        return closeAndOutputChildren();
    }

    public void getPath(StringBuilder sb)
    {
        if (mParent != null) {
            mParent.getPath(sb);
        }
        /* Although fragments are "invisible", let's add an indicator
         * of some sort, since this path is used for trouble-shooting
         */
        sb.append("/{buffered-fragment}");
    }
}
