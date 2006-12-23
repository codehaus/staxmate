package org.codehaus.staxmate;

import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.codehaus.staxmate.out.SMOutputContext;
import org.codehaus.staxmate.out.SMOutputDocument;
import org.codehaus.staxmate.out.SMRootFragment;
import org.codehaus.staxmate.util.Stax2WriterAdapter;

/**
 * Factory class used to create various outputter (like
 * {@link SMOutputDocument} and {@link SMRootFragment}) instances.
 *<p>
 * Factory also has convenience method(s) for accessing a shared
 * global instance of a default {@link XMLOutputFactory}.
 */
public final class SMOutputFactory
{
    private SMOutputFactory() { }

    /*
    ////////////////////////////////////////////////////
    // Document output construction
    //
    // note: no buffered alternatives -- they are easy
    // to create, just add a buffered fragment inside
    // the document fragment
    ////////////////////////////////////////////////////
     */

    public static SMOutputDocument createOutputDocument(XMLStreamWriter sw)
        throws XMLStreamException
    {
        SMOutputContext ctxt = SMOutputContext.createInstance
            (Stax2WriterAdapter.wrapIfNecessary(sw));
        return ctxt.createDocument();
    }

    public static SMOutputDocument createOutputDocument(XMLStreamWriter sw,
                                                        String version,
                                                        String encoding,
                                                        boolean standAlone)
        throws XMLStreamException
    {
        SMOutputContext ctxt = SMOutputContext.createInstance
            (Stax2WriterAdapter.wrapIfNecessary(sw));
        return ctxt.createDocument(version, encoding, standAlone);
    }

    /*
    ///////////////////////////////////////////////////////
    // Fragment output construction
    // 
    // May be useful when only sub-tree(s) of a document
    // is done using StaxMate
    ///////////////////////////////////////////////////////
     */

    public static SMRootFragment createOutputFragment(XMLStreamWriter sw)
        throws XMLStreamException
    {
        SMOutputContext ctxt = SMOutputContext.createInstance
            (Stax2WriterAdapter.wrapIfNecessary(sw));
        return ctxt.createRootFragment();
    }

    /*
    ///////////////////////////////////////////////////////
    // Convenience methods
    ///////////////////////////////////////////////////////
    */

    /**
     * Convenience method that will get a lazily constructed shared
     * {@link XMLOutputFactory} instance. Note that this instance
     * should only be used iff:
     *<ul>
     * <li>Default settings (non-repairing) for the factory are acceptable
     *  </li>
     * <li>Settings of the factory are not modified: thread-safety
     *   of the factory instance is only guaranteed for factory methods,
     *   not for configuration change methods
     *  </li>
     * </ul>
     */
    public static XMLOutputFactory getGlobalXMLOutputFactory()
        throws XMLStreamException
    {
        try {
            return XmlFactoryAccessor.getInstance().getFactory();
        } catch (FactoryConfigurationError err) {
            throw new XMLStreamException(err);
        }
    }

    /*
    ///////////////////////////////////////////////////////
    // Helper classes
    ///////////////////////////////////////////////////////
    */

    /**
     * Separate helper class is used so that the shared factory instance
     * is only created if needed: this happens if the accessor class
     * needs to be instantiate, which in turn happens if the method
     * for accessing the global output factory is called.
     */
    private final static class XmlFactoryAccessor
    {
        final static XmlFactoryAccessor sInstance = new XmlFactoryAccessor();

        XMLOutputFactory mFactory = null;

        private XmlFactoryAccessor() { }
        public static XmlFactoryAccessor getInstance() { return sInstance; }

        public synchronized XMLOutputFactory getFactory()
            throws FactoryConfigurationError
        {
            if (mFactory == null) {
                mFactory = XMLOutputFactory.newInstance();
            }
            return mFactory;
        }
    }
}
