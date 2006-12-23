package org.codehaus.staxmate.out;

import javax.xml.stream.XMLStreamException;

/**
 * Buffered version of {@link SMOutputElement}; starts its life buffered,
 * so that it, its attributes and content are not automatically written to the
 * underlying stream, but only when buffered instance is released.
 *<p>
 * Since the base class properly already implements most of functionality,
 * there is not much extra to do in this class.
 */
public final class SMBufferedElement
    extends SMOutputElement
    implements SMBufferable
{
    /**
     * All instances are initially buffered; state will be changed when
     * instance is released.
     */
    protected boolean mIsBuffered = true;

    /**
     *<p>
     * Note: although signature indicates we could throw an exception,
     * this sub-class never does. But since super-class in itself could,
     * we have to declare it as potentially being thrown.
     */
    protected SMBufferedElement(SMOutputContext ctxt,
                                String localName, SMNamespace ns)
    {
        super(ctxt, localName, ns);
    }

    /*
    ///////////////////////////////////////////////////////////
    // SMBufferable implementation
    ///////////////////////////////////////////////////////////
     */

    public boolean isBuffered() {
        return mIsBuffered;
    }
    
    // Base class implementation is ok for this:
    //public void linkParent(SMOutputContainer parent, boolean blocked)

    public void release()
        throws XMLStreamException
    {
        mIsBuffered = false;
        if (mParent != null) {
            mParent.childReleased(this);
        }
    }

    /*
    ///////////////////////////////////////////////////////////
    // Abstract method implementations
    ///////////////////////////////////////////////////////////
     */

    // Base impl is fine:
    //protected void childReleased(SMOutputtable child) throws XMLStreamException

    /* Base implementation is mostly fine, but let's add some sanity
     * checking
     */
    protected boolean doOutput(SMOutputContext ctxt, boolean canClose)
        throws XMLStreamException
    {
        if (mIsBuffered) {
            throwBuffered();
        }
        return super.doOutput(ctxt, canClose);
    }
}
