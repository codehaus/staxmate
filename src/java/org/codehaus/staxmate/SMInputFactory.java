package org.codehaus.staxmate;

import javax.xml.stream.*;

import org.codehaus.staxmate.in.*;
import org.codehaus.staxmate.util.Stax2ReaderAdapter;

/**
 * Factory class used to create {@link SMInputCursor} instances.
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

    public static SMHierarchicCursor hierarchicCursor(XMLStreamReader sr, SMFilter f) {
        return new SMHierarchicCursor(null, Stax2ReaderAdapter.wrapIfNecessary(sr), f);
    }

    public static SMFlatteningCursor flatteningCursor(XMLStreamReader sr, SMFilter f) {
        return new SMFlatteningCursor(null, Stax2ReaderAdapter.wrapIfNecessary(sr), f);
    }

    /**
     * Return a nested cursor that will only ever iterate to one node, that
     * is, the root element of the document reader is reading.
     */
    public static SMHierarchicCursor rootElementCursor(XMLStreamReader sr)
    {
        return hierarchicCursor(sr, SMFilterFactory.getElementOnlyFilter());
    }

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
            System.out.print("["+it.getDepth()+"] -> "+evt);
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

        System.out.println("["+it.getDepth()+"] END");
    }

    static void traverseFlat(SMInputCursor it)
        throws Exception
    {
        SMEvent evt;

        while ((evt = it.getNext()) != null) {
            System.out.print("["+it.getDepth()+"] -> "+evt);

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

        System.out.println("["+it.getDepth()+"] END");
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
