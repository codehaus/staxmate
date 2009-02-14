package org.codehaus.staxmate;

import java.io.*;
import java.net.URL;

import javax.xml.stream.*;

import org.codehaus.stax2.XMLInputFactory2;
import org.codehaus.stax2.XMLStreamReader2;
import org.codehaus.stax2.io.Stax2ByteArraySource;
import org.codehaus.stax2.ri.Stax2ReaderAdapter;

import org.codehaus.staxmate.in.*;

/**
 * Factory class used to create {@link SMInputCursor} instances.
 * Cursor come in two major flavors: "nested" and "flattening" cursors.
 *<p>
 * Nested cursor are used to iterate a single level nested events,
 * so they can only traverse over immediate children of the event
 * (generally, START_ELEEMENT) that the parent cursor points to.
 * Flattening cursors on the other hand traverse over all the
 * descendants (children, children of children etc) of the parent
 * START_ELEMENT. One additional difference is that the flattening
 * cursors do expose END_ELEMENTS so that matching of actual levels
 * is still possible.
 *<p>
 * Beyond nested/flat (aka "child vs descendant") cursors, there
 * are additional varieties, such as:
 *<ul>
 * <li>Filtered cursors: these will only expose events you want to 
 *   see, and silently skip any other events. Most commonly
 *   needed ones (element-only, text-only, element-and-text-only;
 *   all of which skip comments, processing instructions) exist
 *   for your convenience using
 *  {@link org.codehaus.staxmate.in.SMFilterFactory}.
 *   Filters are passed to the factory methods.
 *</ul>
 *
 * @author Tatu Saloranta
 */
public final class SMInputFactory
{
    /**
     * Xml input stream factory used for constructing stream readers.
     */
    final XMLInputFactory mStaxFactory;

    /**
     * If the configured stax input factory implements Stax2 API,
     * will contain upcast factory instance, otherwise null.
     */
    final XMLInputFactory2 mStax2Factory;

    public SMInputFactory(XMLInputFactory staxF)
    {
        mStaxFactory = staxF;
        mStax2Factory = (staxF instanceof XMLInputFactory2) ?
            (XMLInputFactory2) staxF : null;
    }

    /*
    /////////////////////////////////////////////////
    // Access to underlying Stax factory
    /////////////////////////////////////////////////
     */

    public XMLInputFactory getStaxFactory() { return mStaxFactory; }

    /*
    /////////////////////////////////////////////////
    // Cursor construction, underlying methods
    /////////////////////////////////////////////////
     */

    /**
     * Static factory method used to construct root-level hierarchic (child)
     * cursor, when starting to process an xml document or fragment.
     * Additional cursors are usually constructed via methods
     * within this cursor and its child cursors).
     *
     * @param sr Underlying stream reader cursor will use
     * @param f (optional) Filter to use for the cursor, if any; null
     *   means that no filtering will be done.
     */
    public static SMHierarchicCursor hierarchicCursor(XMLStreamReader sr, SMFilter f) {
        return constructHierarchic(Stax2ReaderAdapter.wrapIfNecessary(sr), f);
    }

    /**
     * Static factory method used to construct root-level flattening (descendant)
     * cursor, when starting to process an xml document or fragment.
     * Additional cursors are usually constructed via methods
     * within this cursor and its child cursors).
     *
     * @param sr Underlying stream reader cursor will use
     * @param f (optional) Filter to use for the cursor, if any; null
     *   means that no filtering will be done.
     */
    public static SMFlatteningCursor flatteningCursor(XMLStreamReader sr, SMFilter f) {
        return constructFlattening(Stax2ReaderAdapter.wrapIfNecessary(sr), f);
    }

    /**
     * Convenience method that will construct and return 
     * a nested cursor that will only ever iterate to one node, that
     * is, the root element of the document reader is reading.
     *<p>
     * Method uses standard "element-only" filter from
     *  {@link org.codehaus.staxmate.in.SMFilterFactory}.
     */
    public static SMHierarchicCursor rootElementCursor(XMLStreamReader sr)
    {
        return hierarchicCursor(sr, SMFilterFactory.getElementOnlyFilter());
    }

    /**
     * Convenience method that will construct and return 
     * a nested cursor that will iterate over root-level events
     * (comments, PIs, root element), without filtering any events.
     *<p>
     * Method uses standard "element-only" filter from
     *  {@link org.codehaus.staxmate.in.SMFilterFactory}.
     */
    public static SMHierarchicCursor rootCursor(XMLStreamReader sr)
    {
        return hierarchicCursor(sr, null);
    }

    /*
    /////////////////////////////////////////////////
    // Stream reader construction
    /////////////////////////////////////////////////
     */

    /**
     * Method for constructing Stax stream reader to read contents
     * of specified URL, using Stax input factory
     * this StaxMate factory was constructed with.
     */
    public XMLStreamReader2 createStax2Reader(URL url)
        throws XMLStreamException
    {
        if (mStax2Factory != null) {
            return mStax2Factory.createXMLStreamReader(url);
        }
        try {
            XMLStreamReader sr = mStaxFactory.createXMLStreamReader(url.toExternalForm(), url.openStream());
            return Stax2ReaderAdapter.wrapIfNecessary(sr);
        } catch (IOException ioe) {
            throw new XMLStreamException(ioe);
        }
    }

    /**
     * Method for constructing Stax stream reader to read contents
     * of specified file, using Stax input factory
     * this StaxMate factory was constructed with.
     */
	public XMLStreamReader2 createStax2Reader(File f)
        throws XMLStreamException
    {
        if (mStax2Factory != null) {
            return mStax2Factory.createXMLStreamReader(f);
        }
        try {
            @SuppressWarnings("deprecation")
            String sysId = f.toURL().toExternalForm();
            XMLStreamReader sr = mStaxFactory.createXMLStreamReader(sysId, new FileInputStream(f));
            return Stax2ReaderAdapter.wrapIfNecessary(sr);
        } catch (IOException ioe) {
            throw new XMLStreamException(ioe);
        }
    }

    /**
     * Method for constructing Stax stream reader to read contents
     * of (portion of) specified byte array, using Stax input factory
     * this StaxMate factory was constructed with.
     */
    public XMLStreamReader2 createStax2Reader(byte[] data, int offset, int len)
        throws XMLStreamException
    {
        Stax2ByteArraySource src = new Stax2ByteArraySource(data, offset, len);
        if (mStax2Factory != null) {
            return (XMLStreamReader2) mStax2Factory.createXMLStreamReader(src);
        }
        try {
            XMLStreamReader sr = mStaxFactory.createXMLStreamReader(src.constructInputStream());
            return Stax2ReaderAdapter.wrapIfNecessary(sr);
        } catch (IOException ioe) {
            throw new XMLStreamException(ioe);
        }
    }

    /**
     * Method for constructing Stax stream reader to read contents
     * accessible through InputStream provided.
     * Underlying stream reader is constructed using Stax factory
     * this StaxMate factory was constructed with.
     *<p>
     * NOTE: this method should only be used if no other overloaded
     * methods matches input source. For example, if input comes from
     * a file, then the method that takes File argument should be used
     * instead. This because more specific methods can provide better
     * error reporting and entity resolution support.
     */
    public XMLStreamReader2 createStax2Reader(InputStream in)
        throws XMLStreamException
    {
        return Stax2ReaderAdapter.wrapIfNecessary(mStaxFactory.createXMLStreamReader(in));
    }

    /**
     * Method for constructing Stax stream reader to read contents
     * accessible through Reader provided.
     * Underlying stream reader is constructed using Stax factory
     * this StaxMate factory was constructed with.
     *<p>
     * NOTE: this method should only be used if no other overloaded
     * methods matches input source. For example, if input comes from
     * a file, then the method that takes File argument should be used
     * instead. This because more specific methods can provide better
     * error reporting and entity resolution support.
     */
    public XMLStreamReader2 createStax2Reader(Reader r)
        throws XMLStreamException
    {
        return Stax2ReaderAdapter.wrapIfNecessary(mStaxFactory.createXMLStreamReader(r));
    }

    /*
    /////////////////////////////////////////////////
    // Cursor construction, "full service" (non-static)
    /////////////////////////////////////////////////
     */

    /**
     * Method that will construct and return 
     * a nested cursor that will only ever iterate to one node, that
     * is, the root element of the document reader is reading.
     *<p>
     * Cursor is built based on Stax stream reader constructed to
     * read contents of resource specified by the URL argument.
     *<p>
     * Method uses standard "element-only" filter from
     *  {@link org.codehaus.staxmate.in.SMFilterFactory}.
     */
    public SMHierarchicCursor rootElementCursor(URL url)
        throws XMLStreamException
    {
        return constructHierarchic(createStax2Reader(url), SMFilterFactory.getElementOnlyFilter());
    }

    /**
     * Method that will construct and return 
     * a nested cursor that will only ever iterate to one node, that
     * is, the root element of the document reader is reading.
     *<p>
     * Cursor is built based on Stax stream reader constructed to
     * read contents of specified File.
     *<p>
     * Method uses standard "element-only" filter from
     *  {@link org.codehaus.staxmate.in.SMFilterFactory}.
     */
    public SMHierarchicCursor rootElementCursor(File f)
        throws XMLStreamException
    {
        return constructHierarchic(createStax2Reader(f), SMFilterFactory.getElementOnlyFilter());
    }

    /**
     * Method that will construct and return 
     * a nested cursor that will only ever iterate to one node, that
     * is, the root element of the document reader is reading.
     *<p>
     * Cursor is built based on Stax stream reader constructed to
     * read contents of the specified byte array.
     *<p>
     * Method uses standard "element-only" filter from
     *  {@link org.codehaus.staxmate.in.SMFilterFactory}.
     */
    public SMHierarchicCursor rootElementCursor(byte[] data, int offset, int len)
        throws XMLStreamException
    {
        return constructHierarchic(createStax2Reader(data, offset, len), SMFilterFactory.getElementOnlyFilter());
    }

    /**
     * Method that will construct and return 
     * a nested cursor that will only ever iterate to one node, that
     * is, the root element of the document reader is reading.
     *<p>
     * Cursor is built based on Stax stream reader constructed to
     * read contents via specified InputStream.
     *<p>
     * Method uses standard "element-only" filter from
     *  {@link org.codehaus.staxmate.in.SMFilterFactory}.
     *<p>
     * NOTE: this method should only be used if no other overloaded
     * methods matches input source. For example, if input comes from
     * a file, then the method that takes File argument should be used
     * instead. This because more specific methods can provide better
     * error reporting and entity resolution support.
     */
    public SMHierarchicCursor rootElementCursor(InputStream in)
        throws XMLStreamException
    {
        return constructHierarchic(createStax2Reader(in), SMFilterFactory.getElementOnlyFilter());
    }

    /**
     * Method that will construct and return 
     * a nested cursor that will only ever iterate to one node, that
     * is, the root element of the document reader is reading.
     *<p>
     * Cursor is built based on Stax stream reader constructed to
     * read contents via specified Reader.
     *<p>
     * Method uses standard "element-only" filter from
     *  {@link org.codehaus.staxmate.in.SMFilterFactory}.
     *<p>
     * NOTE: this method should only be used if no other overloaded
     * methods matches input source. For example, if input comes from
     * a file, then the method that takes File argument should be used
     * instead. This because more specific methods can provide better
     * error reporting and entity resolution support.
     */
    public SMHierarchicCursor rootElementCursor(Reader r)
        throws XMLStreamException
    {
        return constructHierarchic(createStax2Reader(r), SMFilterFactory.getElementOnlyFilter());
    }

    public SMFlatteningCursor flatteningCursor(File input, SMFilter f)
        throws XMLStreamException
    {
        return constructFlattening(createStax2Reader(input), f);
    }

    public SMFlatteningCursor flatteningCursor(URL input, SMFilter f)
        throws XMLStreamException
    {
        return constructFlattening(createStax2Reader(input), f);
    }

    public SMFlatteningCursor flatteningCursor(InputStream input, SMFilter f)
        throws XMLStreamException
    {
        return constructFlattening(createStax2Reader(input), f);
    }

    public SMFlatteningCursor flatteningCursor(Reader r, SMFilter f)
        throws XMLStreamException
    {
        return constructFlattening(createStax2Reader(r), f);
    }

    /*
    ///////////////////////////////////////////////////////
    // Convenience methods
    ///////////////////////////////////////////////////////
    */

    /**
     * Convenience method that will get a lazily constructed shared
     * {@link SMInputFactory} instance. Instance is built using
     * similarly shared {@link XMLInputFactory} instance (which
     * is accessed using {@link #getGlobalXMLInputFactory}).
     * See notes on  {@link #getGlobalXMLInputFactory} for limitations
     * on when (if ever) you should use this method.
     *<p>
     * Note that this single(ton) instance is global to the class loader
     * that loaded <code>SMInputFactory</code> (and usually hence
     * global to a single JVM instance).
     *
     * @throws FactoryConfigurationError If there are problems with
     *   configuration of Stax input factory (most likely because
     *   there is no implementation available)
     */
    public static SMInputFactory getGlobalSMInputFactory()
        throws FactoryConfigurationError
    {
        return SMFactoryAccessor.getFactory();
    }

    /**
     * Convenience method that will get a lazily constructed shared
     * {@link XMLInputFactory} instance. Note that this instance
     * should only be used IFF:
     *<ul>
     * <li>Default settings (namespace-aware, dtd-aware but not validating,
     *   non-coalescing)
     *    for the factory are acceptable
     *  </li>
     * <li>Settings of the factory are not modified: thread-safety
     *   of the factory instance is only guaranteed for factory methods,
     *   not for configuration change methods
     *  </li>
     * </ul>
     *<p>
     * Note that this single(ton) instance is global to the class loader
     * that loaded <code>SMInputFactory</code> (and usually hence
     * global to a single JVM instance).
     *
     * @throws FactoryConfigurationError If there are problems with
     *   configuration of Stax input factory (most likely because
     *   there is no implementation available)
     */
    public static XMLInputFactory getGlobalXMLInputFactory()
        throws FactoryConfigurationError
    {
        return XmlFactoryAccessor.getFactory();
    }

    /*
    ///////////////////////////////////////////////////////
    // Internal methods
    ///////////////////////////////////////////////////////
    */

    private final static SMHierarchicCursor constructHierarchic(XMLStreamReader2 sr, SMFilter f)
    {
        SMInputContext ctxt = new SMInputContext(sr);
        return new SMHierarchicCursor(ctxt, null, f);
    }

    private final static SMFlatteningCursor constructFlattening(XMLStreamReader2 sr, SMFilter f)
    {
        SMInputContext ctxt = new SMInputContext(sr);
        return new SMFlatteningCursor(ctxt, null, f);
    }

    /*
    ///////////////////////////////////////////////////////
    // Helper classes
    ///////////////////////////////////////////////////////
    */

    /**
     * Helper class used for implementing efficient lazy instantiation of
     * the global xml stream input factory.
     */
    private final static class XmlFactoryAccessor
    {
        final static XmlFactoryAccessor sInstance = new XmlFactoryAccessor();
        XMLInputFactory mFactory = null;

        public static XMLInputFactory getFactory()
            throws FactoryConfigurationError
        {
            return sInstance.get();
        }

        private synchronized XMLInputFactory get()
            throws FactoryConfigurationError
        {
            if (mFactory == null) {
                mFactory = XMLInputFactory.newInstance();
            }
            return mFactory;
        }
    }

    /**
     * Helper class used for implementing efficient lazy instantiation of
     * the global StaxMate input factory.
     */
    private final static class SMFactoryAccessor
    {
        final static SMFactoryAccessor sInstance = new SMFactoryAccessor();
        SMInputFactory mFactory = null;

        public static SMInputFactory getFactory()
            throws FactoryConfigurationError
        {
            return sInstance.get();
        }

        private synchronized SMInputFactory get()
            throws FactoryConfigurationError
        {
            if (mFactory == null) {
                mFactory = new SMInputFactory(XmlFactoryAccessor.getFactory());
            }
            return mFactory;
        }
    }
}
