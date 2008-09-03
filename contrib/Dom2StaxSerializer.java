/*

 Copyright (C) 2005 Tatu Saloranta
 All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions
 are met:

 1. Redistributions of source code must retain the above copyright
    notice, this list of conditions, and the following disclaimer.

 2. Redistributions in binary form must reproduce the above copyright
    notice, this list of conditions, and the disclaimer that follows
    these conditions in the documentation and/or other materials
    PROVIDED with the distribution.

 THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 DISCLAIMED.  IN NO EVENT SHALL THE JDOM AUTHORS OR THE PROJECT
 CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 SUCH DAMAGE.

 */

//package ...;

import java.io.*;
import java.util.*;

import org.w3c.dom.*;

import javax.xml.XMLConstants;
import javax.xml.stream.*;

/**
 * Outputs a DOM document using a {@link javax.xml.stream.XMLStreamWriter}
 * provided.
 * @version $Revision: 1.00 $, $Date: 2005/16/10 23:00:00 $
 * @author  Tatu Saloranta
 */
public class Dom2StaxSerializer
{
    protected final XMLStreamWriter mWriter;

    protected final boolean mRepairing;

    public Dom2StaxSerializer(XMLStreamWriter sw)
    {
        mWriter = sw;
        Object o = sw.getProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES);
        mRepairing = (o instanceof Boolean) && ((Boolean) o).booleanValue();
    }

    // // // Public output methods

    public void output(Document doc)
        throws XMLStreamException
    {
        mWriter.writeStartDocument();

        NsStack nsStack = NsStack.defaultInstance();
        for (Node child = doc.getFirstChild(); child != null; child = child.getNextSibling()) {
            doOutputNode(child, nsStack);
        }

        mWriter.writeEndDocument();
        doClose();
    }

    public void outputFragment(NodeList nodes)
        throws XMLStreamException
    {
        NsStack nss = NsStack.defaultInstance();
        for (int i = 0, len = nodes.getLength(); i < len; ++i) {
            doOutputNode((Node) nodes.item(i), nss);
        }
    }

    public void outputFragment(Node node)
        throws XMLStreamException
    {
        doOutputNode(node, NsStack.defaultInstance());
    }

    // // // Internal output methods

    /**
     * @param elem Element to output
     */
    protected void doOutputElement(Element elem, NsStack nsStack)
        throws XMLStreamException
    {
        boolean sharedNsStack = true; // flag to indicate if we need a copy
        String elemPrefix = elem.getPrefix();
        if (elemPrefix == null) {
            elemPrefix = "";
        }
        String elemUri = elem.getNamespaceURI();
        if (elemUri == null) {
            elemUri = "";
        }

        mWriter.writeStartElement(elemPrefix, elem.getLocalName(), elemUri);
        // Hmmh. In non-repairing mode, we need to output namespaces...
        if (!mRepairing) {
            // First, is the namespace element itself uses bound?
            if (!nsStack.hasBinding(elemPrefix, elemUri)) {
                nsStack = nsStack.childInstance();
                sharedNsStack = false;
                nsStack.addBinding(elemPrefix, elemUri);
                if (elemPrefix.length() == 0) { //def ns
                    mWriter.setDefaultNamespace(elemUri);
                    mWriter.writeDefaultNamespace(elemUri);
                } else {
                    mWriter.setPrefix(elemPrefix, elemUri);
                    mWriter.writeNamespace(elemPrefix, elemUri);
                }
            }
        }

        // And in any case, may have attributes:
        NamedNodeMap attrs = elem.getAttributes();
        for (int i = 0, len = attrs.getLength(); i < len; ++i) {
            Attr attr = (Attr) attrs.item(i);
            String aPrefix = attr.getPrefix();
            String ln = attr.getLocalName();
            String value = attr.getValue();

            /* With attributes things are bit simpler: they will never use
             * the default namespace, so if prefix is empty, they will bound
             * to the empty namespace.
             */
            if (aPrefix == null || aPrefix.length() == 0) { // no NS
                mWriter.writeAttribute(ln, value);
            } else {
                String aNsUri = attr.getNamespaceURI();
                // Attribute NS declared?
                if (!mRepairing && !nsStack.hasBinding(aPrefix, aNsUri)) {
                    if (sharedNsStack) {
                        nsStack = nsStack.childInstance();
                        sharedNsStack = false;
                    }
                    nsStack.addBinding(aPrefix, aNsUri);
                    // Binding prefix is optional, but let's do it nonetheless
                    mWriter.setPrefix(aPrefix, aNsUri);
                    mWriter.writeNamespace(aPrefix, aNsUri);
                }
            }

            mWriter.writeAttribute(aPrefix, aNsUri, attr.getLocalName(),
                                   attr.getValue());
        }

        // And then children, recursively:
        for (Node child = elem.getFirstChild(); child != null; child = child.getNextSibling()) {
            doOutputNode(child, nsStack);
        }

        mWriter.writeEndElement();
    }

    protected void doOutputNode(Node node, NsStack nsStack)
        throws XMLStreamException
    {
        switch (node.getNodeType()) {
        case Node.ELEMENT_NODE:
            doOutputElement((Element) node, nsStack);
            break;
        case Node.TEXT_NODE:
            // Do we care about whether it's actually CDATA?
            mWriter.writeCharacters(node.getNodeValue());
            break;
        case Node.CDATA_SECTION_NODE:
            mWriter.writeCData(node.getNodeValue());
            break;
        case Node.COMMENT_NODE:
            mWriter.writeComment(node.getNodeValue());
            break;
        case Node.ENTITY_REFERENCE_NODE:
            mWriter.writeEntityRef(node.getNodeName());
            break;
        case Node.PROCESSING_INSTRUCTION_NODE:
            String target = node.getNodeName();
            String data = node.getNodeValue();
            if (data == null || data.length() == 0) {
                mWriter.writeProcessingInstruction(target);
            } else {
                mWriter.writeProcessingInstruction(target, data);
            }
            break;
        case Node.DOCUMENT_TYPE_NODE:
            mWriter.writeDTD(buildDTD((DocumentType) node));
            break;
        default:
            throw new XMLStreamException("Unrecognized or unexpected node class: "+node.getClass().getName());
        }
    }

    protected void doClose()
        throws XMLStreamException
    {
        mWriter.close();
    }

    protected String buildDTD(DocumentType doctype)
    {
        /* For StAX 1.0, need to construct it: for StAX2 we could
         * pass these as they are...
         */
        StringBuffer sb = new StringBuffer();
        sb.append("<!DOCTYPE ");
        // root elem should never be null
        sb.append(doctype.getName());
        String pubId = doctype.getPublicId();
        String sysId = doctype.getSystemId();
        if (pubId == null || pubId.length() == 0) { // no public id?
            if (sysId != null && sysId.length() > 0) { // but have sys id
                sb.append("SYSTEM \"");
                sb.append(sysId);
                sb.append('"');
            }
        } else {
            sb.append("PUBLIC \"");
            sb.append(pubId);
            sb.append("\" \"");
            // System id can not be null, if so
            sb.append(sysId);
            sb.append('"');
        }
        String intSubset = doctype.getInternalSubset();
        if (intSubset != null && intSubset.length() > 0) {
            sb.append(" [");
            sb.append(intSubset);
            sb.append(']');
        }
        sb.append('>');
        return sb.toString();
    }

    /**
     * Internal helper class, used for keeping track of bound namespaces.
     * It is only needed since JDom has nasty habit of not keeping good track
     * of changes to the namespace binding of the element itself -- all other
     * declarations are properly stored as "additional" namespaces, and can
     * be easily bound on output... but not this primary namespace. Yuck.
     */
    private final static class NsStack
    {
        final static NsStack sEmptyStack;
        static {
            String[] predefd = new String[] {
                "xml", XMLConstants.XML_NS_URI,
                "xmlns", XMLConstants.XMLNS_ATTRIBUTE_NS_URI,
                "", "",
            };
            sEmptyStack = new NsStack(predefd, predefd.length);
        }

        String[] mNsData;

        int mEnd = 0;

        private NsStack(String[] data, int end)
        {
            mNsData = data;
            mEnd = end;
        }

        public static NsStack defaultInstance() {
            return sEmptyStack;
        }

        public NsStack childInstance() {
            // Can not share array of the root instance
            if (this == sEmptyStack) {
                String[] data = new String[16];
                System.arraycopy(mNsData, 0, data, 0, mEnd);
                return new NsStack(data, mEnd);
            }
            return new NsStack(mNsData, mEnd);
        }

        public boolean hasBinding(String prefix, String uri)
        {
            int i = mEnd - 2;
            for (; i >= 0; i -= 2) {
                if (mNsData[i].equals(prefix)) {
                    // This is the most recent binding...
                    return mNsData[i+1].equals(uri);
                }
            }
            return false;
        }

        public void addBinding(String prefix, String uri)
        {
            if (prefix == null) {
                prefix = "";
            }
            if (mEnd >= mNsData.length) {
                String[] old = mNsData;
                mNsData = new String[old.length * 2];
                System.arraycopy(old, 0, mNsData, 0, old.length);
            }
            mNsData[mEnd] = prefix;
            mNsData[mEnd+1] = uri;
            mEnd += 2;
        }
    }

    /* Simple test driver to see how round-trip parsing and outputting works
     * for StAXOutputter.
     */
    public static void main(String[] args)
        throws Exception
    {
        if (args.length != 1) {
            System.err.println("Usage: java ... [file]");
            System.exit(1);
        }
        String filename = args[0];
        java.io.Reader r = new java.io.FileReader(filename);
        javax.xml.stream.XMLInputFactory f = javax.xml.stream.XMLInputFactory.newInstance();

        XMLStreamReader sr = f.createXMLStreamReader(r);
        Stax2DomBuilder builder = new Stax2DomBuilder();
        Document domDoc = builder.build(sr);
        java.io.PrintWriter pw = new java.io.PrintWriter(System.out);

        javax.xml.stream.XMLOutputFactory of = javax.xml.stream.XMLOutputFactory.newInstance();

        // Repairing?
        //of.setProperty(XMLOutputFactory.OUTPUT_REPAIRING_NAMESPACES, Boolean.TRUE);

        XMLStreamWriter sw = of.createXMLStreamWriter(pw);
        Dom2StaxSerializer outputter = new Dom2StaxSerializer(sw);

        outputter.output(domDoc);
        sw.flush();
        sw.close();
    }
}
