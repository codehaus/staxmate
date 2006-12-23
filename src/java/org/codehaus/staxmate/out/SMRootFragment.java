package org.codehaus.staxmate.out;

import javax.xml.stream.XMLStreamException;

/**
 * Concrete non-buffered fragment (== container) class used as the root
 * level output container. Root-level does not necessarily have to mean
 * XML root level; it may also be a child context of a stream writer
 * in which StaxMate is only used to output specific sub-trees.
 * This class is also used as the base for the outputter that models
 * a complete document.
 */
public class SMRootFragment
    extends SMOutputContainer
{
    /**
     * Simple state flag; children can only be added when root container
     * is still active.
     */
    protected boolean mActive = true;

    public SMRootFragment(SMOutputContext ctxt)
    {
        super(ctxt);
    }

    /*
    ///////////////////////////////////////////////////////////
    // Abstract method implementations
    ///////////////////////////////////////////////////////////
     */

    protected boolean doOutput(SMOutputContext ctxt, boolean canClose)
        throws XMLStreamException
    {
        // Should never get called if not active...
        if (!mActive) {
            throwIfClosed();
        }
        if (canClose) {
            return closeAndOutputChildren();
        }
        return closeAllButLastChild();
    }

    protected void forceOutput(SMOutputContext ctxt)
        throws XMLStreamException
    {
        // Should never get called if not active...
        if (!mActive) {
            throwIfClosed();
        }
        forceChildOutput();
    }
    
    protected void childReleased(SMOutputtable child)
        throws XMLStreamException
    {
        // Should never get called if not active...
        if (!mActive) {
            throwIfClosed();
        }

        /* The only child that can block output is the first one... 
         * If that was released, may be able to output more as well.
         * Note that since there's never parent (this is the root fragment),
         * there's no need to try to inform anyone else.
         */
        if (child == mFirstChild) {
            closeAllButLastChild();
        }

        // Either way, we are now done
    }

    public boolean canOutputNewChild()
        throws XMLStreamException
    {
        // Should never get called if not active...
        if (!mActive) {
            throwIfClosed();
        }
        return (mFirstChild == null) || closeAndOutputChildren();
    }

    public void getPath(StringBuilder sb)
    {
        if (mParent != null) {
            mParent.getPath(sb);
        }
        /* Although fragments are "invisible", let's add an indicator
         * of some sort, since this path is used for trouble-shooting
         */
        sb.append("/{fragment}");
    }

    /**
     * Method that HAS to be called when all additions have been done
     * via StaxMate API. Since it is possible that the underlying stream
     * writer may be buffering some parts, it needs to be informed of
     * the closure.
     */
    public void closeRoot()
        throws XMLStreamException
    {
        // Hmmh. Should we complain about duplicate closes?
        if (!mActive) {
            return;
        }
        // Let's first try to close them nicely:
        if (!doOutput(mContext, true)) {
            // but if that doesn't work, should just unbuffer all children...
            forceOutput(mContext);
        }
        // Either way, we are now closed:
        mActive = false;
        // And this may also be a good idea:
        getContext().flushWriter();
    }

    /*
    ///////////////////////////////////////////////////////////
    // Internal methods
    ///////////////////////////////////////////////////////////
     */

    private final void throwIfClosed() {
        throw new IllegalStateException("Can not modify root-level container once it has been closed");
    }
}
