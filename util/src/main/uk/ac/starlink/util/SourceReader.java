package uk.ac.starlink.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.OutputStream;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URL;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.transform.stream.StreamResult;
import org.xml.sax.InputSource;
import org.w3c.dom.Node;


/**
 * Convenience class to manipulate XML Sources. 
 * Methods are provided to do the useful things you might 
 * want done with from a {@link javax.xml.transform.Source}.
 * Depending on the type of the input source this may involve an
 * XML transformation or it may not; such a transformation is not 
 * performed if it is not required.
 * <p>
 * The transformer object used in the case that transformations are
 * required may be accessed or set to permit some customisation of
 * the way the transformation is done.
 *
 * @author   Mark Taylor (Starlink)
 */
public class SourceReader {

    private Transformer transformer;

    /* Implicit no-arg constructor. */

    /**
     * Returns a reference to the Transformer object used for transformations
     * used by this object.  Its characteristics may be changed if required.
     * Note that in the case a transformation is
     * not required (e.g. in the case of getting a DOM node from a
     * source which is already a <tt>DOMSource</tt>) this transformer
     * will not be used.
     *
     * @return  the transformer object used when transformation is necessary
     */ 
    public Transformer getTransformer() {
        if ( transformer == null ) {

            try {
                /* Create a new transformer. */
                transformer = TransformerFactory.newInstance().newTransformer();
            }
            catch ( TransformerException e ) {
                throw new RuntimeException( "Unexpected configuration problem",
                                            e );
            }

            /* Configure some properties to generally useful values. */
            try {
                transformer.setOutputProperty( OutputKeys.INDENT, "yes" );
                transformer.setOutputProperty( OutputKeys.OMIT_XML_DECLARATION, 
                                               "yes" );
                transformer.setOutputProperty( OutputKeys.METHOD, "xml" );
            }
            catch ( IllegalArgumentException e ) {
                // no action - the above properties ought to be configurable,
                // but if not we can sensibly continue.
            }

            /* Attempt to set the indent; if we don't have an Apache
             * transformer this may have no effect, but at worst it is
             * harmless. */
            transformer.setOutputProperty( 
                "{http://xml.apache.org/xslt}indent-amount", "2" );
        }
        return transformer;
    }

    /**
     * Sets the transformer object used for transformations.
     * Note that in the case a transformation is
     * not required (e.g. in the case of getting a DOM node from a
     * source which is already a <tt>DOMSource</tt>) this transformer
     * will not be used.
     *
     * @param   the transformer object to be used when transformation is
     *          necessary.  If <tt>null</tt> is supplied, a default
     *          transformer will be used.
     */
    public void setTransformer( Transformer trans ) {
        transformer = trans;
    }

    /**
     * Returns a DOM Node representing the given source.
     *
     * @param   src  the Source for which the DOM is required
     * @return  a DOM node (typically an <tt>Element</tt>) representing the
     *          XML data in <tt>src</tt>
     * @throws  TransformerException  if some error occurs in transformation
     *                                or I/O
     */
    public Node getDOM( Source src ) throws TransformerException {
        if ( src instanceof DOMSource ) {
            return ((DOMSource) src).getNode();
        }
        else {
            DOMResult res = new DOMResult();
            transform( src, res );
            return res.getNode();
        }
    }

    /**
     * Writes the contents of a given Source into a given Writer.
     * Additional buffering will be performed on the writer if necessary.
     * The writer will be flushed, but not closed.
     * <p>
     * <i>Hmm, not sure if the encoding is handled correctly here for
     * SAXSources...</i>
     *
     * @param   src  the Source to be written
     * @param   wr   the destination for the content of <tt>src</tt>
     * @throws  TransformerException  if some error occurs in transformation
     *                                or I/O
     */
    public void writeSource( Source src, Writer wr ) 
            throws TransformerException {
        try {

            /* Make sure we've got a buffered writer for efficiency. */
            if ( ! ( wr instanceof BufferedWriter ) ) {
                wr = new BufferedWriter( wr );
            }

            /* If we can get a Reader directly from the source, copy chars
             * directly from that to the writer. */
            Reader rdr = getReader( src );
            if ( rdr != null ) {
                if ( ! ( rdr instanceof BufferedReader ) ) {
                    rdr = new BufferedReader( rdr );
                }
                int c;
                while ( ( c = rdr.read() ) > -1 ) {
                    wr.write( c );
                }
            }

            /* Otherwise, do an XML transformation into a StreamResult based 
             * on our writer. */
            else {
                Result res = new StreamResult( wr );
                transform( src, res );
            }
            wr.flush();
        }
        catch ( IOException e ) {
            throw new TransformerException( e );
        }
    }

    /**
     * Writes the contents of a given Source into a given OutputStream.
     * Additional buffering will be performed on the stream if necessary.
     * The stream will be flushed, but not closed.
     *
     * @param   src   the Source to be written
     * @param   ostrm the destination for the content of <tt>src</tt>
     * @throws  TransformerException  if some error occurs in transformation
     *                                or I/O
     */
    public void writeSource( Source src, OutputStream ostrm )
            throws TransformerException {
        try {

            /* Make sure we've got a buffered output stream for efficiency. */
            if ( ! ( ostrm instanceof BufferedOutputStream ) ) {
                ostrm = new BufferedOutputStream( ostrm );
            }

            /* If we can get an InputStream directly from the source, copy 
             * bytes directly from that to the OutputStream. */
            InputStream istrm = getInputStream( src );
            if ( istrm != null ) {
                if ( ! ( istrm instanceof BufferedInputStream ) ) {
                    istrm = new BufferedInputStream( istrm );
                }
                int b;
                while ( ( b = istrm.read() ) > -1 ) {
                    ostrm.write( b );
                }
            }

            /* Otherwise, do an XML transformation into a StreamResult based
             * on our OutputStream. */
            else {
                Result res = new StreamResult( ostrm );
                transform( src, res );
            }
            ostrm.flush();
        }
        catch ( IOException e ) {
            throw new TransformerException( e );
        }
    }


    /**
     * Performs the transformation, catching TransformerExceptions and
     * rethrowing them as unchecked exceptions.
     */
    private void transform( Source src, Result res ) 
            throws TransformerException {
        Transformer trans = getTransformer();
        trans.transform( src, res );
    }


    /**
     * Attempts to get a Reader object directly from a provided XML Source.
     * If none is available, null is returned.
     */
    private static Reader getReader( Source src ) {

        /* Try to get a Reader directly from a StreamSource. */
        if ( src instanceof StreamSource ) {
            StreamSource strmsrc = (StreamSource) src;

            Reader rdr = strmsrc.getReader();
            if ( rdr != null ) { 
                return rdr;
            }
        }

        /* Try to get a Reader directly from a SAXSource. */
        if ( src instanceof SAXSource ) {
            SAXSource saxsrc = (SAXSource) src;
            InputSource input = saxsrc.getInputSource();
            if ( input != null ) {
                Reader rdr = input.getCharacterStream();
                if ( rdr != null ) {
                    return rdr;
                }
            }
        }

        /* Try to get an InputStream directly and turn that into a Reader. */
        InputStream istrm = getInputStream( src );
        if ( istrm != null ) {
            return new InputStreamReader( istrm );
        }

        /* No luck. */
        return null;
    }

    /**
     * Attempts to get an InputStream directly from a provided XML Source.
     * If none is available, null is returned.
     */
    private static InputStream getInputStream( Source src ) {

        /* Try to get an InputStream directly from a StreamSource. */
        if ( src instanceof StreamSource ) {
            StreamSource strmsrc = (StreamSource) src;

            InputStream istrm = strmsrc.getInputStream();
            if ( istrm != null ) {
                return istrm;
            }

            String sysid = strmsrc.getSystemId();
            if ( sysid != null ) {
                try {
                    URL url = new URL( sysid );
                    return url.openStream();
                }
                catch ( MalformedURLException e ) {
                    // no action
                }
                catch ( IOException e ) {
                    // no action
                }
            }
        }

        /* Try to get an InputStream directly from a SAXSource. */
        if ( src instanceof SAXSource ) {
            SAXSource saxsrc = (SAXSource) src;
            InputSource input = saxsrc.getInputSource();
            if ( input != null ) {

                InputStream istrm = input.getByteStream();
                if ( istrm != null ) {
                    return istrm;
                }

                String sysid = saxsrc.getSystemId();
                if ( sysid != null ) {
                    try {
                        URL url = new URL( sysid );
                        return url.openStream();
                    }
                    catch ( MalformedURLException e ) {
                        // no action
                    }
                    catch ( IOException e ) {
                        // no action
                    }
                }
            }
        }

        /* No luck. */
        return null;
    }

}
