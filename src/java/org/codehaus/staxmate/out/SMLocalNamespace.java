package org.codehaus.staxmate.out;

/**
 * Namespace that is local to a specific output context
 * ({@link SMOutputContext}), think of it as the document or sub-tree
 * StaxMate will output using a stream writer).
 */
public final class SMLocalNamespace
    extends SMNamespace
{
    /**
     * Output context in which this namespace is to be used (scope of
     * which it is bound)
     */
    protected final SMOutputContext mContext;

    /**
     * Prefererred (or suggested) prefix for the namespace;
     * StaxMate will try to use this prefix if possible when binding
     * namespaces and also passes it to the underlying stream writer.
     *<p>
     * This value will be automatically set when namespace is created,
     * and there is also a way to explicitly set it. Finally, it will
     * also be set if a dynamic prefix is created for the namespace
     */
    protected String mPrefPrefix;

    /**
     * Prefix this namespace is currently bound to, if any.
     */
    protected String mCurrPrefix = null;

    /**
     * Last prefix this name was bound to, if any.
     */
    protected String mPrevPrefix = null;

    /**
     * Flag that indicates whether this namespaces prefers to be bound
     * as the default namespace (for elements), or not. Output context
     * will use this preference in some situations to determine how to
     * best bind this namespace to a prefix or as the default namespace.
     */
    protected boolean mPreferDefaultNs;

    protected boolean mIsPermanent;

    /**
     * @param ctxt Output context that "owns" this namespace (within which
     *    namespace will be bound when output)
     * @param uri URI that defines identity of the namespace
     * @param prefPrefix Prefererred (or suggested) prefix for the namespace;
     *   StaxMate will try to use this prefix if possible when binding
     *   namespaces and also passes it to the underlying stream writer.
     * @param preferDefaultNs Whether this namespaces prefers to be bound
     *   as the default namespace when used for elements.
     */
    protected SMLocalNamespace(SMOutputContext ctxt,
                               String uri, boolean preferDefaultNs,
                               String prefPrefix)
    {
        super(uri);
        mContext = ctxt;
        mPrefPrefix = prefPrefix;
        mPreferDefaultNs = preferDefaultNs;
    }

    /*
    ///////////////////////////////////
    // Abstract method implementations
    ///////////////////////////////////
     */

    public String getPreferredPrefix() {
        return mPrefPrefix;
    }
    
    public String getBoundPrefix() {
        return mCurrPrefix;
    }

    public String getLastBoundPrefix() {
        return mPrevPrefix;
    }

    public boolean prefersDefaultNs() {
        return mPreferDefaultNs;
    }

    public void prefersDefaultNs(boolean state) {
        mPreferDefaultNs = state;
    }

    public void setPreferredPrefix(String prefPrefix) {
        mPrefPrefix = prefPrefix;
    }

    protected boolean isValidIn(SMOutputContext ctxt) {
        return ctxt == mContext;
    }

    /**
     * The only trick with regard to binding/unbinding of local namespaces
     * is that "re-binding" is not allowed (by StaxMate design; XML would
     * allow it). So let's allow transitions to and from null, but not
     * between two non-empty prefixes.
     */
    protected void bindAs(String prefix) {
        if (mCurrPrefix != null) {
            /* Let's not bother checking for equality -- any calls to re-bind
             * are errors in implementation, and are never called by the
             * end application
             */
            throw new IllegalStateException("Trying to re-bind URI '"+mURI
                                            +"', from prefix '"+mCurrPrefix
                                            +"' to prefix '"+prefix+"'");
        }
        mCurrPrefix = mPrevPrefix = prefix;
    }

    protected void bindPermanentlyAs(String prefix)
    {
        // First, let's do the binding
        bindAs(prefix);
        // and then let's mark it as a permanent one...
        if (mIsPermanent) {
            throw new IllegalStateException("Trying to call permanentlyBindAs() twice (for URI '"+mURI+"', prefix '"+prefix+"')");
        }
        mIsPermanent = true;
    }

    protected void unbind() {
        // Sanity check:
        if (mCurrPrefix == null) {
            throw new IllegalStateException("Trying to unbind an unbound namespace (URI '"+mURI+"')");
        }
        if (!mIsPermanent) { // permanent ones just won't unbind... 
            mCurrPrefix = null;
        }
    }
}
