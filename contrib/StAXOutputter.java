/*--

 Copyright (C) 2000-2004 Jason Hunter & Brett McLaughlin.
 All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions
 are met:

 1. Redistributions of source code must retain the above copyright
    notice, this list of conditions, and the following disclaimer.

 2. Redistributions in binary form must reproduce the above copyright
    notice, this list of conditions, and the disclaimer that follows
    these conditions in the documentation and/or other materials
    provided with the distribution.

 3. The name "JDOM" must not be used to endorse or promote products
    derived from this software without prior written permission.  For
    written permission, please contact <request_AT_jdom_DOT_org>.

 4. Products derived from this software may not be called "JDOM", nor
    may "JDOM" appear in their name, without prior written permission
    from the JDOM Project Management <request_AT_jdom_DOT_org>.

 In addition, we request (but do not require) that you include in the
 end-user documentation provided with the redistribution and/or in the
 software itself an acknowledgement equivalent to the following:
     "This product includes software developed by the
      JDOM Project (http://www.jdom.org/)."
 Alternatively, the acknowledgment may be graphical using the logos
 available at http://www.jdom.org/images/logos.

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

 This software consists of voluntary contributions made by many
 individuals on behalf of the JDOM Project and was originally
 created by Jason Hunter <jhunter_AT_jdom_DOT_org> and
 Brett McLaughlin <brett_AT_jdom_DOT_org>.  For more information
 on the JDOM Project, please see <http://www.jdom.org/>.

 */

package org.jdom.output;

import java.io.*;
import java.util.*;

import javax.xml.XMLConstants;
import javax.xml.stream.*;

import org.jdom.*;

/**
 * Outputs a JDOM document using a {@link javax.xml.stream.XMLStreamWriter}
 * provided.
 *<p>
 * This StAX outputter is modelled to follow API of {@link SAXOutputter}
 * to the degree it makes sense. It would probably make sense to have
 * a common API for these outputters?
 *
 * @version $Revision: 1.00 $, $Date: 2005/16/10 23:00:00 $
 * @author  Tatu Saloranta
 */
public class StAXOutputter
{
    protected final XMLStreamWriter mWriter;

    protected final boolean mRepairing;

    public StAXOutputter(XMLStreamWriter sw)
    {
        mWriter = sw;
        Object o = sw.getProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES);
        mRepairing = (o instanceof Boolean) && ((Boolean) o).booleanValue();
    }

    // // // Public output methods

    public void output(Document doc)
        throws JDOMException, XMLStreamException
    {
        /* Doh. Does JDom not expose xml declaration properties,
         * like encoding, version (and maybe stand-alone status)?
         */
        mWriter.writeStartDocument();

        /* This is bit strange, too, since doctype may be intermingled
         * with proc. instructions and comments at root level... need not
         * be before them all.
         */
        DocType doctype = doc.getDocType();
        if (doctype != null) {
            String pubId = doctype.getPublicID();
            String sysId = doctype.getSystemID();
            String intSubset = doctype.getInternalSubset();

            /* For StAX 1.0, need to construct it: for StAX2 we could
             * pass these as they are...
             */
            StringBuffer sb = new StringBuffer();
            sb.append("<!DOCTYPE ");
            // root elem should never be null
            sb.append(doctype.getElementName());
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
            if (intSubset != null && intSubset.length() > 0) {
                sb.append(" [");
                sb.append(intSubset);
                sb.append(']');
            }
            sb.append('>');
            mWriter.writeDTD(sb.toString());
        }

        doOutputContent(doc.getContent(), NsStack.defaultInstance());

        mWriter.writeEndDocument();
        doClose();
    }

    public void output(Element rootElem)
        throws JDOMException, XMLStreamException
    {
        mWriter.writeStartDocument();
        doOutputElement(rootElem, NsStack.defaultInstance());
        mWriter.writeEndDocument();
        doClose();
    }

    public void output(List elems)
        throws JDOMException, XMLStreamException
    {
        mWriter.writeStartDocument();
        doOutputContent(elems, NsStack.defaultInstance());
        mWriter.writeEndDocument();
        doClose();
    }

    public void outputFragment(List elems)
        throws JDOMException, XMLStreamException
    {
        doOutputContent(elems, NsStack.defaultInstance());
    }

    public void outputFragment(Content node)
        throws JDOMException, XMLStreamException
    {
        doOutputContent(node, NsStack.defaultInstance());
    }

    // // // Internal output methods

    /**
     * @param elem Element to output
     */
    protected void doOutputElement(Element elem, NsStack nsStack)
        throws JDOMException, XMLStreamException
    {
        boolean sharedNsStack = true; // flag to indicate if we need a copy
        String elemPrefix = elem.getNamespacePrefix();
        String elemUri = elem.getNamespaceURI();
        if (elemUri == null) {
            elemUri = "";
        }

        mWriter.writeStartElement(elemPrefix, elem.getName(), elemUri);
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

            // Any other namespaces declared at this level?
            List nsDecl = elem.getAdditionalNamespaces();
            if (nsDecl != null) {
                Iterator it = nsDecl.iterator();
                while (it.hasNext()) {
                    Namespace ns = (Namespace) it.next();
                    String nsPrefix = ns.getPrefix();
                    if (nsPrefix == null) {
                        nsPrefix = "";
                    }
                    String nsUri = ns.getURI();
                    if (nsUri == null) {
                        nsUri = "";
                    }
                    /* Should we check if it was bound? Shouldn't need to...
                     * But we do need to update the stack, so that child
                     * elements can use bindings.
                     */
                    if (sharedNsStack) {
                        nsStack = nsStack.childInstance();
                        sharedNsStack = false;
                    }
                    nsStack.addBinding(nsPrefix, nsUri);

                    /* Binding namespace should not be 100% required, but
                     * some StAX impls may require it?
                     */
                    if (nsPrefix.length() == 0) { //def ns
                        mWriter.setDefaultNamespace(nsUri);
                        mWriter.writeDefaultNamespace(nsUri);
                    } else {
                        mWriter.setPrefix(nsPrefix, nsUri);
                        mWriter.writeNamespace(nsPrefix, nsUri);
                    }
                }
            }
        }

        // And in any case, may have attributes:
        List attrs = elem.getAttributes();
        Iterator it = attrs.iterator();
        while (it.hasNext()) {
            Attribute attr = (Attribute) it.next();
            String aPrefix = attr.getNamespacePrefix();
            String aNsURI = attr.getNamespaceURI();
            /* Not sure if we can or should see if namespace needs
             * binding. Let's just assume that all namespaces have been
             * properly declared when outputting element itself, earlier.
             */
            mWriter.writeAttribute(aPrefix, aNsURI, attr.getName(),
                                   attr.getValue());
        }

        doOutputContent(elem.getContent(), nsStack);

        mWriter.writeEndElement();
    }

    protected void doOutputContent(List content, NsStack currDefaultNS)
        throws JDOMException, XMLStreamException
    {
        Iterator it = content.iterator();
        while (it.hasNext()) {
            doOutputContent((Content) it.next(), currDefaultNS);
        }
    }

    protected void doOutputContent(Content content, NsStack currDefaultNS)
        throws JDOMException, XMLStreamException
    {
        if (content instanceof Element) {
            doOutputElement((Element) content, currDefaultNS);
        } else if (content instanceof Text) {
            // Do we care about whether it's actually CDATA?
            String text = ((Text) content).getText();
            if (content instanceof CDATA) {
                mWriter.writeCData(text);
            } else {
                mWriter.writeCharacters(text);
            }
        } else if (content instanceof Comment) {
            mWriter.writeComment(((Comment) content).getText());
        } else if (content instanceof EntityRef) {
            mWriter.writeEntityRef(((EntityRef) content).getName());
        } else if (content instanceof ProcessingInstruction) {
            ProcessingInstruction pi = (ProcessingInstruction) content;
            String target = pi.getTarget();
            String data = pi.getData();
            if (data == null || data.length() == 0) {
                mWriter.writeProcessingInstruction(target);
            } else {
                mWriter.writeProcessingInstruction(target, data);
            }
        } else {
            throw new JDOMException("Unrecognized or unexpected content class: "+content.getClass().getName());
        }
    }

    protected void doClose()
        throws XMLStreamException
    {
        mWriter.close();
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
        org.jdom.input.StAXBuilder builder = new org.jdom.input.StAXBuilder();

        Document domDoc = builder.build(sr);
        java.io.PrintWriter pw = new java.io.PrintWriter(System.out);

        javax.xml.stream.XMLOutputFactory of = javax.xml.stream.XMLOutputFactory.newInstance();

        // Repairing?
        //of.setProperty(XMLOutputFactory.OUTPUT_REPAIRING_NAMESPACES, Boolean.TRUE);

        XMLStreamWriter sw = of.createXMLStreamWriter(pw);
        StAXOutputter outputter = new StAXOutputter(sw);

        outputter.output(domDoc);
        sw.flush();
        sw.close();
    }
}
