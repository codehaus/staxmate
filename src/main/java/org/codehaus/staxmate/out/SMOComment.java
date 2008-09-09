package org.codehaus.staxmate.out;

import javax.xml.stream.XMLStreamException;

public class SMOComment
    extends SMSimpleOutput
{
    final String mText;

    public SMOComment(String text) {
        super();
        mText = text;
    }

    protected boolean doOutput(SMOutputContext ctxt, boolean canClose)
        throws XMLStreamException
    {
        ctxt.writeComment(mText);
        return true;
    }
}
