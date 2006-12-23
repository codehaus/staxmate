package org.codehaus.staxmate.out;

import java.util.*;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.stream.*;

import org.codehaus.stax2.XMLStreamWriter2;

/**
 * Class that encapsulates details about context in which StaxMate output
 * is done. The most important of the details is the stream writer to use
 * (since that is eventually invoked to do the real output), and its
 * properties.
 *<p>
 * Usually the process of outputting XML content with StaxMate starts by
 * instantiating an {@link SMOutputContext}. It can then be used to create
 * output fragments; all of which bind to that context. Context is thus
 * what "connects" various fragments when they are buffered (when there
 * may or may not be child/parent relationships yet defined).
 *<p>
 * Context is also used (in addition to storing output relevant settings and
 * acting as a fragment factory)
 * as the owner of various other objects, most notable namespaces. All
 * local namespaces are owned by one and only one context.
 */
public final class SMOutputContext
{
    /*
    //////////////////////////////////////////////////////
    // Constants
    //////////////////////////////////////////////////////
    */

    /* Any documents really use more than 16 explicit namespaces?
     */
    final static int DEF_NS_STACK_SIZE = 16;

    protected final static SMNamespace sNsEmpty =
        new SMGlobalNamespace("", XMLConstants.DEFAULT_NS_PREFIX);
    protected final static SMNamespace sNsXml =
        new SMGlobalNamespace(XMLConstants.XML_NS_PREFIX,
                              XMLConstants.XML_NS_URI);
    protected final static SMNamespace sNsXmlns =
        new SMGlobalNamespace(XMLConstants.XMLNS_ATTRIBUTE,
                              XMLConstants.XMLNS_ATTRIBUTE_NS_URI);
    
    final static HashMap<String,SMNamespace> sGlobalNsMap = new HashMap<String, SMNamespace>();
    static {
        sGlobalNsMap.put(sNsEmpty.getURI(), sNsEmpty);
        sGlobalNsMap.put(sNsXml.getURI(), sNsXml);
        sGlobalNsMap.put(sNsXmlns.getURI(), sNsXmlns);
    }

    /*
    //////////////////////////////////////////////////////
    // Configuration settings
    //////////////////////////////////////////////////////
    */

    final XMLStreamWriter2 mStreamWriter;
    final NamespaceContext mRootNsContext;
    final boolean mRepairing;

    /**
     * Prefix to use for creating automatic namespace prefixes. For example,
     * setting this to "ns" would result in automatic prefixes of form
     * "ns1", "ns2" and so on.
     */
    String mNsPrefixPrefix = "ns";

    int mNsPrefixSeqNr = 1;

    /**
     * Configuration flag that specifies whether by default namespaces
     * should bind as the default namespaces for elements or not. If true,
     * all unbound namespaces are always bound as the default namespace,
     * when elements are output: if false, more complicated logics is used
     * (which considers preferred prefixes, past bindings etc).
     */
    boolean mPreferDefaultNs = false;

    /*
    //////////////////////////////////////////////////////
    // State
    //////////////////////////////////////////////////////
    */

    /**
     * Map that contains all local namespaces, that is, namespaces
     * that have been created for use with documents output using
     * this context.
     */
    HashMap<String, SMNamespace> mLocalNsMap = null;
    
    /**
     * Currently active default namespace; one that is in effect within
     * current scope (inside currently open element, if any; if none,
     * within root level).
     */
    SMNamespace mDefaultNs = sNsEmpty;

    /**
     * Stack of bound non-default namespaces.
     */
    SMNamespace[] mNsStack = null;

    /**
     * Number of bound namespaces in {@link mNsStack}
     */
    int mBoundNsCount = 0;
    
    /*
    //////////////////////////////////////////////////////
    // Indentation settings, state
    //////////////////////////////////////////////////////
    */

    /**
     * This String is null when not doing (heuristic) indentation. Otherwise
     * it defines the longest possible indentation String to use; subset
     * by the offset indexes as necessary.
     */
    String mIndentString = null;

    /**
     * Current offset within indentation String, if indenting. Basically
     * offset of the first character after end of indentation String.
     */
    int mIndentOffset = 0;

    /**
     * Number of characters to add to <code>mIndentOffset</code> when
     * adding a new indentation level (and conversely, subtract when
     * closing such level).
     */
    int mIndentStep = 0;

    /**
     * Counter used to suppress indentation, for levels where text
     * has been output (indicating either pure-text or mixed content).
     * Set to -1 when indentation is disabled.
     * This remains 0 when no explicit text output has been done,
     * and is set to 1 from such a state. After becoming non-zero,
     * it will be incremented by one for each new level (start
     * element output), and subtracted by one for close elements.
     *<p>
     * Since this needs to be 0 for any indentation to be output,
     * it is also used as a 'flag' to see if indentation is enabled.
     */
    int mIndentSuppress = -1;

    /**
     * This flag is used to prevent indentation from being added
     * for empty leaf elements, which should either be output
     * as empty elements, or start/end tag pair, with no intervening
     * spaces.
     */
    boolean mIndentLevelEmpty = true;

    /*
    //////////////////////////////////////////////////////
    // Life-cycle; construction, configuration
    //////////////////////////////////////////////////////
    */

    protected SMOutputContext(XMLStreamWriter2 sw, NamespaceContext rootNsCtxt)
    {
        mStreamWriter = sw;
        mRootNsContext = rootNsCtxt;
        Object o = sw.getProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES);
        mRepairing = (o instanceof Boolean) && ((Boolean) o).booleanValue();
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
        mIndentString = indentStr;
        mIndentOffset = startOffset;
        mIndentStep = step;

        // Important: need to set counter to 0, starts with -1
        mIndentSuppress = 0;
    }
    
    /*
    //////////////////////////////////////////////////////
    // Factory methods, context creation
    //////////////////////////////////////////////////////
    */

    public static SMOutputContext createInstance(XMLStreamWriter2 sw, NamespaceContext rootNsCtxt)
        throws XMLStreamException
    {
        return new SMOutputContext(sw, rootNsCtxt);
    }

    public static SMOutputContext createInstance(XMLStreamWriter2 sw)
        throws XMLStreamException
    {
        return createInstance(sw, sw.getNamespaceContext());
    }

    /*
    //////////////////////////////////////////////////////
    // Factory methods, full document writer creation
    //
    // These methods are used when creating full stand-alone
    // documents using StaxMate
    //////////////////////////////////////////////////////
    */

    /**
     * Method used to create a StaxMate output fragment that corresponds
     * to a single well-formed XML document. Assumption, then, is that
     * the underlying stream writer has only been created, but no writes
     * have yet been done.
     *<p>
     * This version of the method calls the matching no-arguments method
     * in the stream writer.
     */
    public SMOutputDocument createDocument()
        throws XMLStreamException
    {
        return new SMOutputDocument(this);
    }

    /**
     * Method used to create a StaxMate output fragment that corresponds
     * to a single well-formed XML document. Assumption, then, is that
     * the underlying stream writer has only been created, but no writes
     * have yet been done.
     *<p>
     * This version of the method calls the matching stream writer method
     * which takes full xml declaration information.
     */
    public SMOutputDocument createDocument(String version, String encoding)
        throws XMLStreamException
    {
        return new SMOutputDocument(this, version, encoding);
    }

    public SMOutputDocument createDocument(String version, String encoding,
                                           boolean standalone)
        throws XMLStreamException
    {
        return new SMOutputDocument(this, version, encoding, standalone);
    }
    
    /*
    //////////////////////////////////////////////////////
    // Factory methods, fragment creation
    //
    // These methods are used when only sub-trees are created
    // using StaxMate (although they can also be used to 
    // create buffered fragments; but usually that is simpler
    // to do via fragment object's factory methods)
    //////////////////////////////////////////////////////
    */

    /**
     * Method to use when outputting an XML sub-tree, in which case the
     * underlying stream writer may be (or has been) used for outputting
     * XML content in addition to content that is output using StaxMate.
     * Resulting fragment is not buffered, and will thus be fully
     * streamed (except for buffering caused by adding buffered children)
     */
    public SMRootFragment createRootFragment()
        throws XMLStreamException
    {
        return new SMRootFragment(this);
    }

    public SMBufferedFragment createBufferedFragment()
        throws XMLStreamException
    {
        return new SMBufferedFragment(this);
    }

    /*
    //////////////////////////////////////////////////////
    // Factory methods, simple node creation
    //////////////////////////////////////////////////////
    */

    public SMOutputtable createCharacters(String text) {
        return SMOCharacters.create(text);
    }

    public SMOutputtable createCharacters(char[] buf, int offset, int len) {
        return SMOCharacters.createShared(buf, offset, len);
    }

    /**
     * Specialized alternative to {link #createCharacters(char[],int,int)}
     * that can count on the passed char array NOT being shared. This means
     * that no intermediate copy needs to be done -- instance can just use
     * the passed in reference knowing it will not be messed by other threads.
     */
    public SMOutputtable createNonSharedCharacters(char[] buf, int offset, int len) {
        return SMOCharacters.createNonShared(buf, offset, len);
    }

    public SMOutputtable createCData(String text) {
        return SMOCData.create(text);
    }

    public SMOutputtable createCData(char[] buf, int offset, int len) {
        return SMOCData.createShared(buf, offset, len);
    }

    /**
     * Specialized alternative to {link #createCData(char[],int,int)}
     * that can count on the passed char array NOT being shared. This means
     * that no intermediate copy needs to be done -- instance can just use
     * the passed in reference knowing it will not be messed by other threads.
     */
    public SMOutputtable createNonSharedCData(char[] buf, int offset, int len) {
        return SMOCData.createNonShared(buf, offset, len);
    }

    public SMOutputtable createComment(String text) {
        return new SMOComment(text);
    }

    public SMOutputtable createEntityRef(String name) {
        return new SMOEntityRef(name);
    }

    public SMOutputtable createProcessingInstruction(String target, String data) {
        return new SMOProcInstr(target, data);
    }

    /*
    //////////////////////////////////////////////////////
    // Namespace handling
    //////////////////////////////////////////////////////
    */

    public final SMNamespace getNamespace(String uri)
    {
        if (uri == null || uri.length() == 0) {
            return sNsEmpty;
        }
        if (mLocalNsMap != null) {
            SMNamespace ns = (SMNamespace) mLocalNsMap.get(uri);
            if (ns != null) {
                return ns;
            }
        }
        SMNamespace ns = (SMNamespace) sGlobalNsMap.get(uri);
        if (ns == null) {
            ns = new SMLocalNamespace(this, uri, mPreferDefaultNs, null);
            if (mLocalNsMap == null) {
                mLocalNsMap = new HashMap<String,SMNamespace>();
            }
            mLocalNsMap.put(uri, ns);
        }
        return ns;
    }

    public final SMNamespace getNamespace(String uri, String prefPrefix)
    {
        if (uri == null || uri.length() == 0) {
            return sNsEmpty;
        }
        if (mLocalNsMap != null) {
            SMNamespace ns = mLocalNsMap.get(uri);
            if (ns != null) {
                return ns;
            }
        }
        SMNamespace ns = sGlobalNsMap.get(uri);
        if (ns == null) {
            ns = new SMLocalNamespace(this, uri, mPreferDefaultNs, prefPrefix);
            if (mLocalNsMap == null) {
                mLocalNsMap = new HashMap<String,SMNamespace>();
            }
            mLocalNsMap.put(uri, ns);
        }
        return ns;
    }

    public final static SMNamespace getEmptyNamespace()
    {
        return sNsEmpty;
    }

    /*
    //////////////////////////////////////////////////////
    // Accessors
    //////////////////////////////////////////////////////
    */

    public final XMLStreamWriter2 getWriter() {
        return mStreamWriter;
    }
    
    public final boolean isWriterRepairing() {
        return mRepairing;
    }

    /*
    //////////////////////////////////////////////////////
    // Outputting of the actual content; done via context
    // so that overriding is possible
    //////////////////////////////////////////////////////
    */

    public void flushWriter()
        throws XMLStreamException
    {
        mStreamWriter.flush();
    }

    public void writeCharacters(String text)
        throws XMLStreamException
    {
        if (mIndentSuppress == 0) {
            mIndentSuppress = 1;
        }
        mStreamWriter.writeCharacters(text);
    }
    
    public void writeCharacters(char[] buf, int offset, int len)
        throws XMLStreamException
    {
        if (mIndentSuppress == 0) {
            mIndentSuppress = 1;
        }
        mStreamWriter.writeCharacters(buf, offset, len);
    }
    
    public void writeCData(String text)
        throws XMLStreamException
    {
        if (mIndentSuppress == 0) {
            mIndentSuppress = 1;
        }
        mStreamWriter.writeCData(text);
    }
    
    public void writeCData(char[] buf, int offset, int len)
        throws XMLStreamException
    {
        if (mIndentSuppress == 0) {
            mIndentSuppress = 1;
        }
        mStreamWriter.writeCData(buf, offset, len);
    }
    
    public void writeComment(String text)
        throws XMLStreamException
    {
        if (mIndentSuppress == 0) {
            outputIndentation();
            mIndentLevelEmpty = false;
        }
        mStreamWriter.writeComment(text);
    }
    
    public void writeEntityRef(String name)
        throws XMLStreamException
    {
        // Entity references are like text output, so:
        if (mIndentSuppress == 0) {
            mIndentSuppress = 1;
        }
        mStreamWriter.writeEntityRef(name);
    }
    
    public void writeProcessingInstruction(String target, String data)
        throws XMLStreamException
    {
        if (mIndentSuppress == 0) {
            outputIndentation();
            mIndentLevelEmpty = false;
        }
        if (data == null) {
            mStreamWriter.writeProcessingInstruction(target);
        } else {
            mStreamWriter.writeProcessingInstruction(target, data);
        }
    }

    public void writeAttribute(String localName, SMNamespace ns,
                               String value)
        throws XMLStreamException
    {
        /* First things first: in repairing mode this is specifically
         * easy...
         */
        if (mRepairing) {
            // If no prefix preference, let's not pass one:
            String prefix = ns.getPreferredPrefix();
            if (prefix == null) {
                mStreamWriter.writeAttribute(ns.getURI(), localName, value);
            } else {
                mStreamWriter.writeAttribute(prefix,
                                             ns.getURI(), localName, value);
            }
            return;
        }

        // If not repairing, we need to handle bindings:

        /* No/empty namespace is simple for attributes, though; the
         * default namespace is never used...
         */
        if (ns == sNsEmpty) {
            mStreamWriter.writeAttribute(localName, value);
            return;
        }

        String prefix = ns.getBoundPrefix();
        if (prefix == null || prefix.length() == 0) {
            // First check: maybe it is still bound in the root context?
            prefix = findRootPrefix(ns);
            if (prefix != null) {
                // Yup. Need to mark it as permanently bound, then
                ns.bindPermanentlyAs(prefix);
            } else {
                // Ok. So which prefix should we bind (can't use def ns)?
                prefix = ns.getLastBoundPrefix();
                if (prefix == null) {
                    prefix = ns.getPreferredPrefix();
                }
                if (prefix == null || isPrefixBound(prefix)) {
                    prefix = generateUnboundPrefix();
                }
                // Ok, can bind now...
                ns.bindAs(prefix);
                mStreamWriter.writeNamespace(prefix, ns.getURI());
            }
        }

        mStreamWriter.writeAttribute(prefix, ns.getURI(), localName, value);
    }

    /**
     * Method called by the element object when it is about to get written
     * out. In this case, element will keep track of part of namespace
     * context information for this context object (to save allocation
     * of separate namespace context object).
     *
     * @return Namespace that was the active namespace in parent scope
     *   of this element. Will be different from the default namespace
     *   if a new default namespace was declared to be used by this
     *   element.
     */
    public SMNamespace writeStartElement(SMNamespace ns, String localName)
        throws XMLStreamException
    {
        // Indentation?
        if (mIndentSuppress >= 0) {
            if (mIndentSuppress == 0) {
                outputIndentation();
                mIndentOffset += mIndentStep;
            } else {
                ++mIndentSuppress;
            }
            mIndentLevelEmpty = true;
        }

        /* In repairing mode we won't do binding,
         * nor keep track of them
         */
        if (mRepairing) {
            String prefix = ns.getPreferredPrefix();
            // If no prefix preference, let's not pass one:
            if (prefix == null) {
                mStreamWriter.writeStartElement(ns.getURI(), localName);
            } else {
                mStreamWriter.writeStartElement(prefix, localName, ns.getURI());
            }
            return mDefaultNs;
        }

        SMNamespace oldDefaultNs = mDefaultNs;
        String prefix;
        boolean needToBind = false;

        // Namespace we need is either already the default namespace?
        if (ns == oldDefaultNs) { // ok, simple; already the default NS:
            prefix = "";
        } else {
            // Perhaps it's already bound to a specific prefix though?
            prefix = ns.getBoundPrefix();
            if (prefix != null) { // yes, should be ok then
                /* ... except for one possible caveat: the "empty" namespace
                 * may have been masked (StaxMate never masks any explicitly
                 * bound namespace declarations)
                 */
                if (ns == sNsEmpty) {
                    /* Only ends up here if the default ns is not the empty
                     * one any more... If so, need to re-bind it.
                     */
                    needToBind = true;
                }
            } else { // no such luck... need to bind
                /* Ok, how about the root namespace context? We may have
                 * "inherited" bindings; if so, they are accessible via
                 * namespace context.
                 */
                prefix = findRootPrefix(ns);
                if (prefix != null) {
                    // Yup. Need to mark it as permanently bound, then
                    ns.bindPermanentlyAs(prefix);
                } else {
                    needToBind = true; // yes, need to bind it
                    // Bind as the default namespace?
                    if (ns.prefersDefaultNs()) { // yes, please
                        prefix = "";
                    } else { // well, let's see if we have used a prefix earlier
                        prefix = ns.getLastBoundPrefix();
                        if (prefix != null && !isPrefixBound(prefix)) {
                            ; // can and should use last bound one, if possible
                        } else { // nope... but perhaps we have a preference?
                            prefix = ns.getPreferredPrefix();
                            if (prefix != null && !isPrefixBound(prefix)) {
                                // Ok, cool let's just bind it then:
                            } else {
                                // Nah, let's just bind as the default, then
                                prefix = "";
                            }
                        }
                    }
                }
            }
        }
        
        mStreamWriter.writeStartElement(prefix, localName, ns.getURI());
        if (needToBind) {
            if (prefix.length() == 0) {
                mDefaultNs = ns;
                mStreamWriter.writeDefaultNamespace(ns.getURI());
            } else {
                ns.bindAs(prefix);
                mStreamWriter.writeNamespace(prefix, ns.getURI());
            }
        }
        return oldDefaultNs;
    }
    
    public void writeEndElement(int parentNsCount, SMNamespace parentDefNs)
        throws XMLStreamException
    {
        // Indentation?
        if (mIndentSuppress >= 0) {
	    mIndentOffset -= mIndentStep;
            if (mIndentSuppress == 0) {
                if (!mIndentLevelEmpty) {
                    outputIndentation();
                }
            } else {
                --mIndentSuppress;
            }
            mIndentLevelEmpty = false;
        }

        mStreamWriter.writeEndElement();

        /* Ok, if we are not in repairing mode, may need to unbind namespace
         * bindings for namespaces bound with matching start element
         */
        if (mRepairing) {
            if (mBoundNsCount > parentNsCount) {
                int i = mBoundNsCount;
                mBoundNsCount = parentNsCount;
                while (i-- > parentNsCount) {
                    SMNamespace ns = mNsStack[i];
                    mNsStack[i] = null;
                    ns.unbind();
                }
            }
        }

        mDefaultNs = parentDefNs;
    }

    public void writeStartDocument()
        throws XMLStreamException
    {
        mStreamWriter.writeStartDocument();
    }

    public void writeStartDocument(String version, String encoding)
        throws XMLStreamException
    {
        // note: Stax 1.0 has weird ordering for the args...
        mStreamWriter.writeStartDocument(encoding, version);
    }

    public void writeStartDocument(String version, String encoding,
                                   boolean standalone)
        throws XMLStreamException
    {
        mStreamWriter.writeStartDocument(version, encoding, standalone);
    }

    public void writeEndDocument()
        throws XMLStreamException
    {
        mStreamWriter.writeEndDocument();
        // And finally, let's indicate stream writer about closure too...
        mStreamWriter.close();
    }

    public void writeDoctypeDecl(String rootName,
                                 String systemId, String publicId,
                                 String intSubset)
        throws XMLStreamException
    {
        if (mIndentSuppress == 0) {
            outputIndentation();
        }
        mStreamWriter.writeDTD(rootName, systemId, publicId, intSubset);
    }

    /*
    //////////////////////////////////////////////////////
    // Other public utility methods
    //////////////////////////////////////////////////////
    */

    public String generateUnboundPrefix() {
        while (true) {
            String prefix = mNsPrefixPrefix + (mNsPrefixSeqNr++);
            if (!isPrefixBound(prefix)) {
                return prefix;
            }
        }
    }

    public boolean isPrefixBound(String prefix)
    {
        for (int i = mBoundNsCount; --i >= 0; ) {
            SMNamespace ns = mNsStack[i];
            if (prefix.equals(ns.getBoundPrefix())) {
                /* Note: StaxMate never creates masking bindings, so we
                 * know it's still active
                 */
                return true;
            }
        }
        /* So far so good. But perhaps it's bound in the root NamespaceContext?
         */
        if (mRootNsContext != null) {
            String uri = mRootNsContext.getNamespaceURI(prefix);
            if (uri != null && uri.length() > 0) {
                return true;
            }
        }
        return false;
    }

    public String findRootPrefix(SMNamespace ns)
    {
        if (mRootNsContext != null) {
            String uri = ns.getURI();
            String prefix = mRootNsContext.getPrefix(uri);
            /* Should seldom if ever get a match for the default NS; but
             * if we do, let's not take it.
             */
            if (prefix != null && prefix.length() > 0) {
                return prefix;
            }
        }
        return null;
    }

    /*
    //////////////////////////////////////////////////////
    // Package methods
    //////////////////////////////////////////////////////
    */

    /**
     * @return Number of bound non-default namespaces (ones with explicit
     *   prefix) currently
     */
    int getNamespaceCount() {
        return mBoundNsCount;
    }

    boolean isDefaultNs(SMNamespace ns) {
        return (mDefaultNs == ns);
    }

    /*
    //////////////////////////////////////////////////////
    // Internal methods
    //////////////////////////////////////////////////////
    */

    /*
    private void bindNs(SMNamespace ns)
    {
        SMNamespace[] stack = mNsStack;
        if (stack == null) {
            mNsStack = stack = new SMNamespace[DEF_NS_STACK_SIZE];
        } else if (mBoundNsCount >= stack.length) {
            mNsStack = new SMNamespace[stack.length * 2];
            System.arraycopy(stack, 0, mNsStack, 0, stack.length);
            stack = mNsStack;
        }
        stack[mBoundNsCount++] = ns;
    }
    */

    private void outputIndentation()
        throws XMLStreamException
    {
        int offset = mIndentOffset;
        if (offset > 0) {
            int len = mIndentString.length();
            if (offset > len) {
                offset = len;
            }
            // !!! TBI: Should have String-with-indexes method too in XMLStreamWriter2
            String ind = mIndentString.substring(0, offset);
            mStreamWriter.writeRaw(ind);
        }
    }
}
