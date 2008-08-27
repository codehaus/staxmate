package org.codehaus.staxmate;

import javax.xml.stream.*;

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
    private SMInputFactory() { }

    /*
    /////////////////////////////////////////////////
    // Cursor construction
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
        return new SMHierarchicCursor(null, Stax2ReaderAdapter.wrapIfNecessary(sr), f);
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
        return new SMFlatteningCursor(null, Stax2ReaderAdapter.wrapIfNecessary(sr), f);
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
    ///////////////////////////////////////////////////////
    // Convenience methods
    ///////////////////////////////////////////////////////
    */

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
     */
    public static XMLInputFactory getGlobalXMLInputFactory()
        throws XMLStreamException
    {
        try {
            return XmlFactoryAccessor.getInstance().getFactory();
        } catch (FactoryConfigurationError err) {
            // Can we do anything about this? It's an error, need not really catch?
            //throw new XMLStreamException(err);
            throw err;
        }
    }

    /*
    ///////////////////////////////////////////////////////
    // Helper classes
    ///////////////////////////////////////////////////////
    */

    private final static class XmlFactoryAccessor
    {
        final static XmlFactoryAccessor sInstance = new XmlFactoryAccessor();

        XMLInputFactory mFactory = null;

        private XmlFactoryAccessor() { }

        public static XmlFactoryAccessor getInstance() { return sInstance; }

        public synchronized XMLInputFactory getFactory()
            throws FactoryConfigurationError
        {
            if (mFactory == null) {
                mFactory = XMLInputFactory.newInstance();
            }
            return mFactory;
        }
    }

    /*
    /////////////////////////////////////////////////
    // Simple test driver functionality
    /////////////////////////////////////////////////
     */

    @SuppressWarnings("deprecation")
	public static void main(String[] args)
        throws Exception
    {
        if (args.length != 1) {
            System.err.println("Usage: java "+SMInputFactory.class+" [input file]");
            System.exit(1);
        }
        XMLInputFactory f = XMLInputFactory.newInstance();
        java.io.File file = new java.io.File(args[0]);
        XMLStreamReader r = f.createXMLStreamReader(file.toURL().toExternalForm(),
                                                    new java.io.FileInputStream(file));

        ///*
        SMInputCursor it = hierarchicCursor(r, null);
        it.setElementTracking(SMInputCursor.Tracking.VISIBLE_SIBLINGS);
        traverseNested(it);
        //*/

        /*
        SMInputCursor it = flatCursor(r, null);
        it.setElementTracking(SMInputCursor.Tracking.VISIBLE_SIBLINGS);
        traverseFlat(it);
        */

        r.close();
    }

    static void traverseNested(SMInputCursor it)
        throws Exception
    {
        SMEvent evt;

        while ((evt = it.getNext()) != null) {
            System.out.print("["+it.getParentCount()+"] -> "+evt);
            switch (evt) {
            case START_ELEMENT:
                System.out.print(" <"+it.getPrefixedName()+">");
                System.out.println(" Path: "+getPath(it));
                System.out.println(" Prev: "+getSiblings(it));
                traverseNested(it.childCursor(null));
                break;
            case END_ELEMENT:
                System.out.println(" </"+it.getPrefixedName()+">");
                break;
            default: 
                if (evt.isTextualEvent()) {
                    System.out.println(" Text (trim): '"+it.getText().trim()+"'");
                } else {
                    System.out.println();
                }
            }
        }

        System.out.println("["+it.getParentCount()+"] END");
    }

    static void traverseFlat(SMInputCursor it)
        throws Exception
    {
        SMEvent evt;

        while ((evt = it.getNext()) != null) {
            System.out.print("["+it.getParentCount()+"] -> "+evt);

            switch (evt) {
            case START_ELEMENT:
                System.out.print(" <"+it.getPrefixedName()+">");
                System.out.println(" Path: "+getPath(it));
                System.out.println(" Prev: "+getSiblings(it));
                break;

            case END_ELEMENT:
                System.out.print(" </"+it.getPrefixedName()+">");
                System.out.println(" Path: "+getPath(it));
                System.out.println(" Prev: "+getSiblings(it));
                break;

            default:

                if (evt.isTextualEvent()) {
                    System.out.println(" Text (trim): '"+it.getText().trim()+"'");
                } else {
                    System.out.println();
                }
            }
        }

        System.out.println("["+it.getParentCount()+"] END");
    }

    static String getPath(SMInputCursor it)
    {
        SMElementInfo curr = it.getTrackedElement();
        int nodeIndex = curr.getNodeIndex();
        int elemIndex = curr.getElementIndex();

        StringBuilder sb = new StringBuilder();
        for (; curr != null; curr = curr.getParent()) {
            sb.insert(0, '/');
            sb.insert(0, curr.getLocalName());
        }
        sb.insert(0, "["+nodeIndex+" / "+elemIndex+"] ");
        return sb.toString();
    }

    static String getSiblings(SMInputCursor it)
    {
        SMElementInfo curr = it.getTrackedElement();
        StringBuilder sb = new StringBuilder();
        for (; curr != null; curr = curr.getPreviousSibling()) {
            sb.insert(0, "->");
            sb.insert(0, curr.getLocalName());
        }
        return sb.toString();
    }
}
