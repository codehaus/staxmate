package org.codehaus.staxmate.out;

import javax.xml.stream.XMLStreamException;

/**
 * Intermediate abstract output class for StaxMate, which is used as the base
 * for all output nodes that can contain other nodes.
 * Both buffered and unbuffered classes exists, as well as root-level
 * and branch containers. All output by sub-classes is using by the underlying
 * {@link javax.xml.stream.XMLStreamWriter}, using the context
 * ({@link SMOutputContext}).
 *<p>
 * Whether writes are buffered or not generally depends on buffering states
 * of preceding nodes (elements, fragments), in document order: if an ancestor
 * (parent, grand-parent) or a preceding sibling is buffered, so is this
 * fragment, until all such nodes have been released.
 */
public abstract class SMOutputContainer
    extends SMOutputtable
{
    /**
     * Context of this node; defines things like the underlying stream
     * writer and known namespaces.
     */
    final SMOutputContext mContext;

    /**
     * Parent of this container; null for root-level entities, as well
     * as not-yet-linked buffered containers.
     */
    SMOutputContainer mParent = null;

    /**
     * First child node that has not yet been completely output to the
     * underlying stream. This may be due to 
     a blocking condition (parent blocked, children blocked,
     * or the child itself being buffered). May be null if no children
     * have been added, or if all have been completely output.
     */
    SMOutputtable mFirstChild = null;

    /**
     * Last child node that has not been output to the underlying stream.
     */
    SMOutputtable mLastChild = null;

    /*
    ///////////////////////////////////////////////////////////
    // Life-cycle
    ///////////////////////////////////////////////////////////
    */

    protected SMOutputContainer(SMOutputContext ctxt)
    {
        super();
        mContext = ctxt;
    }

    /**
     * This method can be called to enable or disable heuristic indentation
     * for the output done using this output context.
     *<p>
     * Here are some example calls:
     *<blockquote>
     * context.setIndentation("\n        ", 1, 2); // indent by lf and 2 spaces per level
     * context.setIndentation(null, 0, 0); // disable indentation
     * context.setIndentation("\r\n\t\t\t\t\t\t\t\t", 2, 1); // indent by windows lf and 1 tab per level
     *</blockquote>
     *
     * @param indentStr String to use for indentation; if non-null, will
     *   enable indentation, if null, will disable it. Used in conjunction
     *   with the other arguments
     * @param startOffset Initial character offset for the first level of
     *   indentation (current context; usually root context): basically,
     *   number of leading characters from <code>indentStr</code> to
     *   output.
     * @param step Number of characters to add from the indentation
     *   String for each new level (and to subtract when closing levels).
     */
    public void setIndentation(String indentStr, int startOffset, int step)
    {
        mContext.setIndentation(indentStr, startOffset, step);
    }

    /*
    ///////////////////////////////////////////////////////////
    // Simple accessors/mutators
    ///////////////////////////////////////////////////////////
    */

    public final SMOutputContainer getParent() {
        return mParent;
    }


    public final SMOutputContext getContext() {
        return mContext;
    }

    /*
    /////////////////////////////////////////////////////
    // Properties/state
    /////////////////////////////////////////////////////
     */

    /**
     * Convenience method for getting namespace instance that
     * uniquely represents the specified URI (uniquely meaning
     * that for a given output context there are never more than
     * one instances for a given URI; which means that identity
     * comparison is enough to check for equality of two namespaces).
     * Calls {@link SMOutputContext} to find the actual namespace
     * instance.
     */
    public final SMNamespace getNamespace(String uri) {
        return mContext.getNamespace(uri);
    }

    public final SMNamespace getNamespace(String uri, String prefPrefix) {
        return mContext.getNamespace(uri, prefPrefix);
    }

    /*
    ///////////////////////////////////////////////////////////
    // Output methods for simple nodes (no elements, attributes
    // or buffering)
    ///////////////////////////////////////////////////////////
    */

    public void addCharacters(String text)
        throws XMLStreamException
    {
        if (canOutputNewChild()) {
            mContext.writeCharacters(text);
        } else {
            linkNewChild(mContext.createCharacters(text));
        }
    }

    public void addCharacters(char[] buf, int offset, int len)
        throws XMLStreamException
    {
        if (canOutputNewChild()) {
            mContext.writeCharacters(buf, offset, len);
        } else {
            linkNewChild(mContext.createCharacters(buf, offset, len));
        }
    }

    /**
     * Convenience method for adding value of an int as text
     */
    public void addCharacters(int value)
        throws XMLStreamException
    {
        /* Should/could optimize (use local char array etc), for
         * performance boost...
         */
        String strValue = String.valueOf(value);
        addCharacters(strValue);
    }

    public void addCData(String text)
        throws XMLStreamException
    {
        if (canOutputNewChild()) {
            mContext.writeCData(text);
        } else {
            linkNewChild(mContext.createCData(text));
        }
    }

    public void addCData(char[] buf, int offset, int len)
        throws XMLStreamException
    {
        if (canOutputNewChild()) {
            mContext.writeCData(buf, offset, len);
        } else {
            linkNewChild(mContext.createCData(buf, offset, len));
        }
    }

    public void addComment(String text)
        throws XMLStreamException
    {
        if (canOutputNewChild()) {
            mContext.writeComment(text);
        } else {
            linkNewChild(mContext.createComment(text));
        }
    }

    public void addEntityRef(String name)
        throws XMLStreamException
    {
        if (canOutputNewChild()) {
            mContext.writeEntityRef(name);
        } else {
            linkNewChild(mContext.createEntityRef(name));
        }
    }

    public void addProcessingInstruction(String target, String data)
        throws XMLStreamException
    {
        if (canOutputNewChild()) {
            mContext.writeProcessingInstruction(target, data);
        } else {
            linkNewChild(mContext.createProcessingInstruction(target, data));
        }
    }

    /*
    ////////////////////////////////////////////////////////
    // Output methods for Elements, attributes, buffered
    // fragments
    ////////////////////////////////////////////////////////
    */

    public SMOutputElement addElement(SMNamespace ns, String localName)
        throws XMLStreamException
    {
        final SMOutputContext ctxt = mContext;

        /* First, need to make sure namespace declaration is appropriate
         * for this context
         */
        if (ns == null) {
            ns = SMOutputContext.getEmptyNamespace();
            /* Hmmh. Callers should know better than to share namespace
             * instances... but then again, we can easily fix the problem
             * even if they are shared:
             */
        } else if (!ns.isValidIn(ctxt)) {
            /* Let's find instance from our current context, instead of the
             * one from some other context
             */
            ns = getNamespace(ns.getURI());
        }

        // Ok, let's see if we are blocked already
        boolean blocked = !canOutputNewChild();
        SMOutputElement newElem = new SMOutputElement(ctxt, localName, ns);
        linkNewChild(newElem);
        newElem.linkParent(this, blocked);

        return newElem;
    }

    /**
     * Convenience method (equivalent to
     * <code>addElement(null, localName);</code>) for adding an element
     * that is not in a namespace.
     *<p>
     * Note: this is NOT the same as outputting an element that simply
     * has no prefix (ie. one that would belong to whatever is the
     * current default namespace).
     */
    public SMOutputElement addElement(String localName)
        throws XMLStreamException
    {
        return addElement(null, localName);
    }

    public SMBufferable addBuffered(SMBufferable buffered)
        throws XMLStreamException
    {
        // Ok; first, let's see if we are blocked already
        boolean blocked = !canOutputNewChild();
        linkNewChild((SMOutputtable) buffered);
        buffered.linkParent(this, blocked);
        return buffered;
    }

    public SMBufferable addAndReleaseBuffered(SMBufferable buffered)
        throws XMLStreamException
    {
        addBuffered(buffered);
        buffered.release();
        return buffered;
    }

    /*
    ////////////////////////////////////////////////////////
    // Buffered fragment/element construction
    //
    // note: these methods add tight coupling to sub-classes...
    // while not really good, architecturally, these are
    // strongly dependant classes in any case, so let's not
    // get ulcer over such cyclic dependencies (just duly note
    // they are there)
    ////////////////////////////////////////////////////////
    */

    public SMBufferedFragment createBufferedFragment()
    {
        return new SMBufferedFragment(getContext());
    }

    public SMBufferedElement createBufferedElement(SMNamespace ns, String localName)
    {
        return new SMBufferedElement(getContext(), localName, ns);
    }

    /*
    ////////////////////////////////////////////////////////
    // Abstract methods from base classes
    ////////////////////////////////////////////////////////
    */

    protected abstract boolean doOutput(SMOutputContext ctxt, boolean canClose)
        throws XMLStreamException;

    protected abstract void forceOutput(SMOutputContext ctxt)
        throws XMLStreamException;

    /*
    ////////////////////////////////////////////////////////
    // New abstract methods
    ////////////////////////////////////////////////////////
    */

    /**
     * Method called by a child, when it is released and neither is or
     * contains any buffered entities. This should indicate that it
     * can be output unless one of its parents or preceding siblings
     * is buffered.
     * Container is expected to update its own
     * state, and then inform its own parent (if necesary) about release;
     * this may cascade output from parents up the container stack.
     *
     * @param child Child node that now neither is nor contains any buffered
     *    nodes.
     */
    protected abstract void childReleased(SMOutputtable child)
        throws XMLStreamException;
   
    /**
     * Method called to figure out if we can just output a newly added
     * child, without any buffering. It will request container to close
     * and output all non-buffered children it has, if any; and indicate
     * whether it was fully succesful or not.
     *
     * @return True if all children (if any) were completely output; false
     *   if there was at least one buffered child that couldn't be output.
     */
    public abstract boolean canOutputNewChild()
        throws XMLStreamException;

    /*
    ////////////////////////////////////////////////////////
    // Methods for getting more info (debugging, error msgs)
    ////////////////////////////////////////////////////////
    */

    /**
     * Method that can be called to get an XPath like description
     * of the relative location of this output node, starting from root.
     */
    public final String getPath()
    {
        StringBuilder sb = new StringBuilder(100);
        getPath(sb);
        return sb.toString();
    }

    public abstract void getPath(StringBuilder sb);

    /*
    ////////////////////////////////////////////////////////
    // Internal/package methods
    ////////////////////////////////////////////////////////
    */

    protected void linkNewChild(SMOutputtable n)
    {
        SMOutputtable last = mLastChild;
        if (last == null) {
            mLastChild = n;
            mFirstChild = n;
        } else {
            last.linkNext(n);
            mLastChild = n;
        }
    }

    /**
     * Method that will try to close and output all child nodes that
     * can be (ones that are not buffered), and returns true if that
     * succeeds; or false if there was at least one buffered descendant.
     *
     * @return True if all descendants (children, recursively) were
     *   succesfully output, possibly closing them first if necessary
     */
    protected final boolean closeAndOutputChildren()
        throws XMLStreamException
    {
        while (mFirstChild != null) {
            if (!mFirstChild.doOutput(mContext, true)) {
                // Nope, node was buffered or had buffered child(ren)
                return false;
            }
            mFirstChild = mFirstChild.mNext;
        }
        mLastChild = null;
        return true;
    }

    /**
     * Method that will try to close and output all children except for
     * the last, and if that succeeds, output last child if it need
     * not be closed (true for non-element/simple children).
     */
    protected final boolean closeAllButLastChild()
        throws XMLStreamException
    {
        SMOutputtable child = mFirstChild;
        while (child != null) {
            SMOutputtable next = child.mNext;
            /* Need/can not force closing of the last child, but all
             * previous can and should be closed:
             */
            boolean notLast = (next != null);
            if (!mFirstChild.doOutput(mContext, notLast)) {
                // Nope, node was buffered or had buffered child(ren)
                return false;
            }
            mFirstChild = child = next;
        }
        mLastChild = null;
        return true;
    }

    protected final void forceChildOutput()
        throws XMLStreamException
    {
        SMOutputtable child = mFirstChild;
        mFirstChild = null;
        mLastChild = null;
        for (; child != null; child = child.mNext) {
            child.forceOutput(mContext);
        }
    }

    protected void throwClosed() {
        throw new IllegalStateException("Illegal call when container (of type "
                                        +getClass()+") was closed");
    }

    protected void throwRelinking() {
            throw new IllegalStateException("Can not re-set parent (for instance of "+getClass()+") once it has been set once");
    }

    protected void throwBuffered() {
        throw new IllegalStateException("Illegal call when container (of type "
                                        +getClass()+") is still buffered");
    }
}
