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

package org.jdom.input;

import java.util.HashMap;

import org.jdom.*;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Strategy class (used by {@link StAXBuilder} that allows for modifying
 * text content when building a JDOM tree from an XML document using
 * StAX {@link XMLStreamReader}. It is most commonly used to trim out
 * white space that can not be automatically determined by the parser
 * (due to not having an associated DTD, usually), but can be used
 * to do other manipulations as well.
 *<p>
 * Basic calling sequence is as follows:
 * <ol>
 *  <li>For each START_ELEMENT and END_ELEMENT,
 *    {@link #allowModificationsAfter} is called, to determine if CHARACTERS
 *    elements read after this event may possibly be modified. This
 *    allows builder to ignore calling other methods on this object for
 *    elements that contain text content that should not be modified (like
 *    &lt;pre&gt; element in (X)HTML, for example).
 *   </li>
 *  <li>For each CHARACTERS element that follows a call to 
 *    {@link #allowModificationsAfter} that returned true,
 *    {@link #possiblyModifyText} is called, to determine if contents of
 *    that event should be modified before being added to the JDOM tree
 *   </li>
 *  <li>Finally, for CHARACTERS event for which call to
 *    {@link #possiblyModifyText} returned true,
 *    {@link #textToIncludeBetween} is called to figure out resulting text
 *    to add to JDOM tree. This may be the original text (which is passed
 *    as an argument), or something else, including null or empty String
 *    to essentially remove that text event from the tree.
 *   </li>
 * </ol>
 *<p>
 * The default implementation of this class implements simple logics that
 * will remove all "indentation" white space from the document.
 * This is done by always enabling modifications in the whole tree,
 * and removing such text events that are all whitespace and start
 * with a line feed character (\r or \n).
 * Extending classes can obviously create much more fine-grained
 * heuristics.
 *
 * @version $Revision: 1.00 $, $Date: 2004/12/11 00:00:00 $
 * @author  Tatu Saloranta
 */
public abstract class StAXTextModifier
{
    protected StAXTextModifier() { }
    
    /**
     * Method called to determine whether to possibly remove (indentation)
     * white space after START_ELEMENT or END_ELEMENT that the stream
     * reader currently points to.
     *
     * @param r Stream reader that currently points to the event referred.
     * @param eventType Type of the currently pointed to event (either
     *   START_ELEMENT or END_ELEMENT)
     */
    public abstract boolean allowModificationsAfter(XMLStreamReader r, int eventType)
        throws XMLStreamException;
    
    /**
     * Method called for CHARACTERS and CDATA events when the previous call to
     * {@link #allowModificationsAfter} returned true. Is used to
     * determine if there is possibility that this text segment needs
     * to be modified (up to and including being removed, as is the case
     * for indentation removal).
     *<p>
     * Note: StAX stream readers are allowed report CDATA sections as
     * CHARACTERS too, so some implementations may not allow distinguishing
     * between CDATA and other text. Further, when text is to be coalesced,
     * resulting event type will always be CHARACTERS, when segments
     * are combined, even if they all were adjacent CDATA sections.
     *
     * @param r Stream reader that currently points to the CHARACTERS or
     *   CDATA event for which method is called.
     * @param prevEvent Type of the event that immediately preceded
     *   the current event.
     */
    public abstract boolean possiblyModifyText(XMLStreamReader r, int prevEvent)
        throws XMLStreamException;
    
    /**
     * Method called to determine what to include in place of the preceding
     * text segment (of type CHARACTERS or CDATA), given event types that
     * precede and follow the text segment. This allows for removal of
     * (indentation) white space (return null or empty string); trimming
     * of leading and/or trailing white space (return trimmed text), or
     * just returning passed-in text as is.
     *<p>
     * The method is only called if the immediately preceding call to
     * {@link #possiblyModifyText} returned true; otherwise
     * text is included as is without calling this method.
     *<p>
     * Note that when this method is called, the passed in stream reader
     * already points to the event following the text; not the text
     * itself; because of this the text is passed explicitly, as it
     * can NOT be accessed via the stream reader.
     *
     * @return Text to include in place of a CHARACTERS event; may
     *   be the text passed in (no change), null/empty String (remove
     *   the text event, usually all white space like indentation),
     *   or a modified String.
     */
    public abstract String textToIncludeBetween(XMLStreamReader r,
                                                int prevEvent, int nextEvent,
                                                String text)
        throws XMLStreamException;
}
