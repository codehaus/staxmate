package org.codehaus.staxmate.out;

import javax.xml.stream.XMLStreamException;

/**
 * Output class that models an outputtable XML element.
 */
public class SMOutputElement
    extends SMOutputContainer
{
    // No output done, due to blocking:
    protected final static int OUTPUT_NONE = 0;
    // Element name and prefix output, possibly some attributes
    protected final static int OUTPUT_ATTRS = 1;
    // Start element completely output:
    protected final static int OUTPUT_CHILDREN = 2;
    // End element output, ie. fully closed
    protected final static int OUTPUT_CLOSED = 3;

    /*
    /////////////////////////////////////////////
    // Element properties
    /////////////////////////////////////////////
    */

    /**
     * Local name of the element, name without preceding prefix or colon
     * (in namespace mode). In non-namespace mode fully-qualified name.
     */
    protected final String mLocalName;

    /**
     * Namespace of this element.
     *<p>
     * Note: can never be null -- event the default (empty) namespace
     * is presented by a global shared namespace instance.
     */
    protected final SMNamespace mNs;

    /*
    /////////////////////////////////////////////
    // Output state information
    /////////////////////////////////////////////
    */

    protected int mOutputState = OUTPUT_NONE;

    /**
     * Namespace that was bound as the default namespace in the context
     * where this element gets output. This is generally just stored here
     * to be able to write the end element matching start element,
     * since it's {@link SMOutputContext} that handles actual namespace
     * binding for output.
     * This is either the default declared
     * namespace of an ancestor element, or if none exists, the default
     * namespace of the root (either the empty namespace, or one found
     * via {@link javax.xml.namespace.NamespaceContext}.
     *<p>
     * Note: can never be null -- event the default (empty) namespace
     * is presented by a global shared namespace instance.
     */
    protected SMNamespace mParentDefaultNs;

    /**
     * Number of explicitly bound namespaces parent element has (or
     * for root elements 0). Stored for {@link SMOutputContext} during
     * time element is open; needed for closing namespace scopes
     * appropriately.
     */
    protected int mParentNsCount;

    protected SMOutputElement(SMOutputContext ctxt,
                              String localName, SMNamespace ns)
    {
        super(ctxt);
        mParent = null;
        mLocalName = localName;
        mNs = ns;
    }
    
    public String getLocalName() {
        return mLocalName;
    }
    
    public SMNamespace getNamespace() {
        return mNs;
    }

    public void linkParent(SMOutputContainer parent, boolean blocked)
        throws XMLStreamException
    {
        if (mParent != null) {
            throwRelinking();
        }
        mParent = parent;
        if (!blocked) { // can output start element right away?
            doWriteStartElement();
        }
    }

    /*
    ///////////////////////////////////////////////////////////
    // Additional output methods
    ///////////////////////////////////////////////////////////
     */

    public void addAttribute(SMNamespace ns, String localName, String value)
        throws XMLStreamException
    {
        final SMOutputContext ctxt = mContext;
        
        // Let's make sure NS declaration is good, first:
        if (ns == null) {
            ns = SMOutputContext.getEmptyNamespace();
        } else if (!ns.isValidIn(ctxt)) { // shouldn't happen, but...
            /* Let's find instance from our current context, instead of the
             * one from some other context
             */
            ns = getNamespace(ns.getURI());
        }
        
        // Ok, what can we do, then?
        switch (mOutputState) {
        case OUTPUT_NONE: // blocked
            // !!! TBI: buffer attribute to this element
            break;
        case OUTPUT_ATTRS: // perfect
            ctxt.writeAttribute(localName, ns, value);
            break;
        default:
            throwClosedForAttrs();
        } 
    }

    /**
     * Convenience method for attributes that do not belong to a
     * namespace (no prefix)
     */
    public void addAttribute(String localName, String value)
        throws XMLStreamException
    {
        addAttribute(null, localName, value);
    }

    /**
     * Convenience method to use for adding attributes with integer
     * values
     */
    public void addAttribute(SMNamespace ns, String localName, int value)
        throws XMLStreamException
    {
        // !!! Should optimize (use local or shared char array buffer etc)
        addAttribute(ns, localName, String.valueOf(value));
    }

    /*
    ///////////////////////////////////////////////////////////
    // Abstract method implementations
    ///////////////////////////////////////////////////////////
     */

    protected void childReleased(SMOutputtable child)
        throws XMLStreamException
    {
        // Ok; first of all, only first child matters:
        if (child == mFirstChild) {
            switch (mOutputState) {
            case OUTPUT_NONE:
                /* output blocked by parent (or lack of parent), can't output,
                 * nothing for parent to do either
                 */
                return;
            case OUTPUT_CLOSED: // error
                throwClosed();
            case OUTPUT_ATTRS: // should never happen!
                throw new IllegalStateException("Internal error: illegal state (OUTPUT_ATTRS) on receiving 'childReleased' notification");
            }

            /* Ok, parent should know how to deal with it. In state
             * OUTPUT_START we will always have the parent defined.
             */
            /* It may seem wasteful to throw this all the way up the chain,
             * but it is necessary to do since children are not to handle
             * how preceding buffered siblings should be dealt with.
             */
            mParent.childReleased(this);
        }
    }
    
    protected boolean doOutput(SMOutputContext ctxt, boolean canClose)
        throws XMLStreamException
    {
        switch (mOutputState) {
        case OUTPUT_NONE: // was blocked, need to output element
            doWriteStartElement();
            break;
        case OUTPUT_CLOSED:
            // If we are closed, let's report a problem
            throwClosed();
        case OUTPUT_ATTRS: // can just "close" attribute writing scope
            mOutputState = OUTPUT_CHILDREN;
        }

        // Any children? Need to try to close them too
        if (mFirstChild != null) {
            if (canClose) {
                closeAndOutputChildren();
            } else {
                closeAllButLastChild();
            }
        }

        // Can we fully close this element?
        if (!canClose || mFirstChild != null) {
            return false;
        }

        // Ok, can and should close for good:
        doWriteEndElement();
        return true;
    }
    
    protected void forceOutput(SMOutputContext ctxt)
        throws XMLStreamException
    {
        // Let's first ask nicely:
        if (doOutput(mContext, true)) {
            ; // all done (including outputting end element)
        } else {
            // ... but if that doesn't work, let's negotiate bit more:
            forceChildOutput();
            doWriteEndElement();
        }
    }
    
    public boolean canOutputNewChild()
        throws XMLStreamException
    {
        /* This is fairly simple; if we are blocked, can not output it right
         * away. Otherwise, if we have no children, can always output a new
         * one; if more than one, can't (first one is blocking, or
         * parent is blocking); if just one, need to try to close it first.
         */
        switch (mOutputState) {
        case OUTPUT_NONE: // output blocked, no go:
            return false;
        case OUTPUT_CLOSED: // error
            throwClosed();
        case OUTPUT_ATTRS: // can just "close" attribute writing scope
            mOutputState = OUTPUT_CHILDREN;
            break;
        }

        if (mFirstChild == null) { // no children -> ok
            return true;
        }
        return closeAndOutputChildren();
    }

    public void getPath(StringBuilder sb)
    {
        if (mParent != null) {
            mParent.getPath(sb);
        }
        sb.append('/');
        /* Figuring out namespace prefix is bit trickier, since it may
         * or may not have been bound yet. But we do know that the empty
         * Namespace can only bind to empty prefix; so we can only have
         * a prefix for non-empty namespace URI (but that doesn't yet
         * guarantee a prefix)
         */
        String uri = mNs.getURI();
        if (uri != null && uri.length() > 0) {
            // Default ns?
            if (!mContext.isDefaultNs(mNs)) { // not the current one, no
                String prefix = mNs.getBoundPrefix();
                if (prefix == null) { // not yet bound? (or masked default ns?)
                    prefix = "{unknown-prefix}";
                } else if (prefix.length() == 0) { // def. NS, no prefix
                    prefix = null;
                }
                if (prefix != null) {
                    sb.append(prefix);
                    sb.append(':');
                }
            }
        }
        sb.append(mLocalName);
    }

    /*
    ///////////////////////////////////////////////////////////
    // Internal methods
    ///////////////////////////////////////////////////////////
     */

    protected void doWriteStartElement()
        throws XMLStreamException
    {
        mOutputState = OUTPUT_ATTRS;
        SMOutputContext ctxt = mContext;
        mParentNsCount = ctxt.getNamespaceCount();
        mParentDefaultNs = ctxt.writeStartElement(mNs, mLocalName);
    }

    protected void doWriteEndElement()
        throws XMLStreamException
    {
        mOutputState = OUTPUT_CLOSED;
        mContext.writeEndElement(mParentNsCount, mParentDefaultNs);
    }

    protected void throwClosedForAttrs()
    {
        String desc = (mOutputState == OUTPUT_CLOSED) ?
            "ELEMENT-CLOSED" : "CHILDREN-ADDED";
        throw new IllegalStateException
            ("Can't add attributes for an element (path = '"
             +getPath()+"'), element state '"+desc+"'");
    }
}
