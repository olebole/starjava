package uk.ac.starlink.ndx;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URL;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Logger;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import uk.ac.starlink.array.NDArray;
import uk.ac.starlink.array.NDShape;
import uk.ac.starlink.array.Requirements;
import uk.ac.starlink.ast.AstPackage;
import uk.ac.starlink.ast.Frame;
import uk.ac.starlink.ast.FrameSet;
import uk.ac.starlink.ast.Mapping;
import uk.ac.starlink.ast.WinMap;
import uk.ac.starlink.ast.xml.XAstReader;
import uk.ac.starlink.ast.xml.XAstWriter;
import uk.ac.starlink.util.SourceReader;
import uk.ac.starlink.hdx.AbstractDOMFacade;
import uk.ac.starlink.hdx.DOMFacade;
import uk.ac.starlink.hdx.HdxDocument;
import uk.ac.starlink.hdx.HdxException;
import uk.ac.starlink.hdx.HdxResourceType;
import uk.ac.starlink.hdx.HdxResourceFactory;
import uk.ac.starlink.hdx.PluginException;

/**
 * Default <tt>Ndx</tt> implementation.
 * This class builds an <tt>Ndx</tt> from an {@link NdxImpl}.
 *
 * <p>The static initialiser for this class is also responsible
 * for creating and registering the 
 * {@link uk.ac.starlink.hdx.HdxResourceType} which corresponds to
 * Ndx.  For this to happen, this <code>BridgeNdx</code> class must be named in a
 * <code>Hdx.properties</code> file, as described in class 
 * {@link uk.ac.starlink.hdx.HdxResourceType}.
 *
 * @author   Mark Taylor (Starlink)
 * @author   Peter Draper (Starlink)
 * @author   Norman Gray (Starlink)
 */
public class BridgeNdx implements Ndx {

    private static Logger logger = Logger.getLogger( "uk.ac.starlink.ndx" );

    private final NdxImpl impl;
    private FrameSet ast;
    private String title;
    private Boolean hasEtc;
    private Boolean hasTitle;
    private Boolean hasVariance;
    private Boolean hasQuality;
    private Boolean hasWCS;
    private Integer badbits;
    private NDArray image;
    private NDArray variance;
    private NDArray quality;

    protected Document ndxDocumentCache;

    final private static String XMLNAME_IMAGE = "image";
    final private static String XMLNAME_VARIANCE = "variance";
    final private static String XMLNAME_QUALITY = "quality";
    final private static String XMLNAME_TITLE = "title";
    final private static String XMLNAME_BADBITS = "badbits";
    final private static String XMLNAME_WCS = "wcs";
    final private static String XMLNAME_ETC = "etc";
    

    /*
     * Static initialiser creates the HdxResourceType corresponding to Ndx.
     */
    static private HdxResourceType ndxType;
    static {
        ndxType = HdxResourceType.newHdxResourceType("ndx");
        if (ndxType == null)
            throw new PluginException("Ooops: type ndx was already defined");

        try {
            // Register fallback handler
            ndxType.registerHdxResourceFactory
                (new HdxResourceFactory() {
                    public Object getObject (Element el)
                        throws HdxException {
                        String att = el.getAttribute("url");
                        assert att != null;
                        if (att.length() == 0)
                            // That is, instantiate a new BridgeNdx only
                            // if the element has _no_ url attribute, so
                            // that the data and variance content are
                            // present as elements
                            //
                            // XXX we should deal, either here or when the
                            // element was constructed, with the case
                            // <ndx url="xxx"/>
                            return new BridgeNdx(new DomNdxImpl(el));
                        else
                            return null;
                    }
                });
            ndxType.setElementValidator
                (new uk.ac.starlink.hdx.ElementValidator() {
                    public boolean validateElement(Element el) {
                        // Validate by requiring that each child is an
                        // element, and that each _registered_ element
                        // is valid.  If we find any elements that
                        // aren't registered, assume they're OK.  Is
                        // this sensible?
                        for (Node n = el.getFirstChild();
                             n != null;
                             n = n.getNextSibling()) {
                            if (n.getNodeType() != Node.ELEMENT_NODE)
                                return false;
                            HdxResourceType t
                                = HdxResourceType.match((Element)n);
                            if (t != HdxResourceType.NONE
                                && !t.isValid((Element)n))
                                return false;
                        }
                        // no objections, so...
                        return true;
                    }
                });
            ndxType.setHoistAttribute("uri");
            ndxType.setConstructedClass("uk.ac.starlink.ndx.Ndx");

            // Register the array types corresponding to
            // XMLNAME_{IMAGE,VARIANCE,QUALITY}, and the fallback
            // handlers for them.  This might sit more naturally in
            // NDArrayFactory, except that we would then have to
            // duplicate the XMLNAME_... names.
            HdxResourceFactory hrf = new HdxResourceFactory() {
                    public Object getObject(org.w3c.dom.Element el) 
                        throws uk.ac.starlink.hdx.HdxException {
                        /*
                         * We do not have to worry whether this
                         * element is in fact an HdxElement, and so
                         * whether or not the element is actually a
                         * facade for a pre-made object (and so on).
                         * We would not have been called unless we
                         * were to construct the object from scratch,
                         * from the URL.
                         *
                         * Also, we don't need to worry about checking
                         * that the Element is of an appropriate type.
                         * Firstly, we shouldn't be called otherwise,
                         * but secondly, if this is the wrong element,
                         * then makeNDArray will fail and return null.
                         */
                        try {
                            String url = el.getAttribute("url");
                            if (url == null)
                                return null;
                            uk.ac.starlink.array.NDArrayFactory ndaf
                                    = new uk.ac.starlink.array.NDArrayFactory();
                            return ndaf.makeNDArray
                                    (new URL(url),
                                    uk.ac.starlink.array.AccessMode.READ);
                        } catch (java.net.MalformedURLException ex) {
                            throw new uk.ac.starlink.hdx.HdxException
                                ("Can't create URL: "
                                 + el.getAttribute("url")
                                 + " (" + ex + ")");
                        } catch (IOException ex) {
                            throw new uk.ac.starlink.hdx.HdxException
                                ("Unexpectedly failed to read "
                                 + el.getAttribute("url")
                                 + " (" + ex + ")");
                        }
                    }
            };

            String[] subtype = 
                    { XMLNAME_IMAGE, XMLNAME_VARIANCE, XMLNAME_QUALITY };
            for (int i=0; i<subtype.length; i++) {
                HdxResourceType newtype
                    = HdxResourceType.newHdxResourceType(subtype[i]);
                if (newtype == null)
                    throw new uk.ac.starlink.hdx.PluginException
                        ("Ooops: type " + subtype[i] + " already defined");
                newtype.registerHdxResourceFactory(hrf);
                newtype.setHoistAttribute("uri");
                newtype.setElementValidator
                    (new uk.ac.starlink.hdx.ElementValidator() {
                        public boolean validateElement(Element el) {
                            // Require that the element has a uri attribute
                            String uriString = el.getAttribute("uri");
                            if (uriString.length() == 0)
                                return false;

                            try {
                                // Construct a URI object, to validate
                                // the syntax of uriString.  Should we
                                // try converting it to a URL?
                                // Probably not, since the toURL()
                                // method requires that the URI be
                                // absolute, which we don't
                                // necessarily want.
                                java.net.URI uri = new URI(uriString);
                                return true;
                            } catch (java.net.URISyntaxException e) {
                                // Ignore, but return false
                                return false;
                            }
                        }
                    });
                newtype.setConstructedClass("uk.ac.starlink.array.NDArray");
            }
        } catch (HdxException ex) {
            throw new PluginException("Failed to register types!: " + ex);
        }
    }
    

    /**
     * Constructs an {@link Ndx} implementation from an <tt>NdxImpl</tt> 
     * object.
     *
     * @param  impl  object which provides services to this BridgeNdx
     */
    public BridgeNdx( NdxImpl impl ) {
        this.impl = impl;
    }

    public NDArray getImage() {
        if ( image == null ) {
            image = impl.getImage();
        }
        return image;
    }

    public NDArray getVariance() {
        if ( ! hasVariance() ) {
            throw new UnsupportedOperationException( "No variance component" );
        }
        if ( variance == null ) {
            variance = impl.getVariance();
        }
        return variance;
    }

    public NDArray getQuality() {
        if ( ! hasQuality() ) {
            throw new UnsupportedOperationException( "No quality component" );
        }
        if ( quality == null ) {
            quality = impl.getQuality();
        }
        return quality;
    }

    public boolean hasVariance() {
        if ( hasVariance == null ) {
            hasVariance = Boolean.valueOf( impl.hasVariance() );
        }
        return hasVariance.booleanValue();
    }

    public boolean hasQuality() {
        if ( hasQuality == null ) {
            hasQuality = Boolean.valueOf( impl.hasQuality() );
        }
        return hasQuality.booleanValue();
    }

    public boolean hasTitle() {
        if ( hasTitle == null ) {
            hasTitle = Boolean.valueOf( impl.hasTitle() );
        }
        return hasTitle.booleanValue();
    }

    public boolean hasEtc() {
        if ( hasEtc == null ) {
            hasEtc = Boolean.valueOf( impl.hasEtc() );
        }
        return hasEtc.booleanValue();
    }

    public boolean hasWCS() {
        if ( hasWCS == null ) {
            hasWCS = Boolean.valueOf( AstPackage.isAvailable() &&
                                      impl.hasWCS() );
        }
        return hasWCS.booleanValue();
    }

    public String getTitle() {
        if ( ! hasTitle() ) {
            throw new UnsupportedOperationException( "No title component" );
        }
        if ( title == null ) {
            title = impl.getTitle();
        }
        return title;
    }

    public Source getEtc() {
        if ( ! hasEtc() ) {
            throw new UnsupportedOperationException( "No Etc component" );
        }
        return impl.getEtc();
    }

    public int getBadBits() {
        if ( badbits == null ) {
            badbits = new Integer( impl.getBadBits() );
        }
        return badbits.intValue();
    }

    public FrameSet getAst() {
        if ( ! hasWCS() ) {
            throw new UnsupportedOperationException( "No WCS component" );
        }
        if ( ast == null ) {
            try {

                /* Implementation may supply the WCS in a number of formats.
                 * Try to cope with all, or throw an exception. */
                Object fsobj = impl.getWCS();
                if ( fsobj instanceof FrameSet ) {
                    ast = (FrameSet) fsobj;
                }
                else if ( fsobj instanceof Element ) {
                    ast = makeAst( new DOMSource( (Element) fsobj ) );
                }
                else if ( fsobj instanceof Source ) {
                    ast = makeAst( (Source) fsobj );
                }
                else {
                    logger.warning( "Unknown WCS object type " + fsobj );
                }
            }
            catch ( IOException e ) {
                logger.warning( "Error retrieving WCS: " + e ); 
            }
            if ( ast == null ) {
                ast = Ndxs.getDefaultAst( this );
            }
        }
        return ast;
    }

    public boolean isPersistent() {
        return ( getImage().getURL() != null )
            && ( ! hasVariance() || getVariance().getURL() != null )
            && ( ! hasQuality() || getQuality().getURL() != null );
    }


    private static FrameSet makeAst( Source astsrc ) throws IOException {

        //  Note namespace prefix is null as HDX should have
        //  transformed it!
        return (FrameSet) new XAstReader().makeAst( astsrc, null );
    }

    /**
     * Generates an XML view of this Ndx object as a <tt>Source</tt>.
     * The XML is built using only public methods of this Ndx rather than
     * any private values, so that this method can safely be inherited
     * by subclasses.
     *
     * @param  base  URL against which others are to be relativised
     * @return  an XML Source representation of this Ndx
     * @deprecated replaced by <code>getDOMFacade().getSource(base)</code>
     */
    public Source toXML( URL base ) {
        // this method comes from the Ndx interface, but has the same
        // functionality as getSource
        return getDOMFacade().getSource(base);
    }
    
    /**
     * Returns the Hdx type corresponding to Ndx objects.
     */
    public static HdxResourceType getHdxType() {
        return ndxType;
    }

    /**
     * Generalises the Document.importNode method so it works for a wider
     * range of Node types.
     */
    private Node importNode( Document doc, Node inode ) {

        /* Importing a DocumentFragment should work (in fact I think that's
         * pretty much what DocumentFragments were designed for) but when
         * you try to do it using Crimson it throws:
         *
         *   org.apache.crimson.tree.DomEx: 
         *      HIERARCHY_REQUEST_ERR: This node isn't allowed there
         *
         * I'm pretty sure that's a bug.  Work round it by hand here. */
        if ( inode instanceof DocumentFragment ) {
            Node onode = doc.createDocumentFragment();
            for ( Node ichild = inode.getFirstChild(); ichild != null; 
                  ichild = ichild.getNextSibling() ) {
                Node ochild = doc.importNode( ichild, true );
                onode.appendChild( ochild );
            }
            return onode;
        }

        /* It isn't permitted to import a whole document.  Just get its
         * root element. */
        else if ( inode instanceof Document ) {
            Node rootnode = ((Document) inode).getDocumentElement();
            return doc.importNode( rootnode, true );
        }

        /* Otherwise, just let Document.importNode do the work. */
        else {
            return doc.importNode( inode, true );
        }
    }

    /**
     * Turns a URL into a URI catching the exceptions.  I don't think that
     * an exception can actuallly result here, since a URIs are surely a
     * superset of URLs?  So why doesn't this method (or an equivalent 
     * constructor) exist in the URI class??.
     */
    private static URI urlToUri( URL url ) {
        try {
            return new URI( url.toExternalForm() );
        }
        catch ( URISyntaxException e ) {
            throw new AssertionError( "Failed to convert URL <" + url + "> "
                                    + "to URI" );
        }
    }

    protected HdxDocument constructDOM(URL base) {

        /* Set up the document and root element. */
        HdxDocument doc = (HdxDocument)uk.ac.starlink.hdx.HdxDOMImplementation
                .getInstance()
                .createDocument( null, "ndx", null);
        Element ndxEl = doc.createElement( "ndx" );
        doc.appendChild( ndxEl );

        /* Get the base URI in a form suitable for using with URI.relativize. */
        URI baseUri;
        if ( base != null ) {
            try {
                baseUri = new URI( base.toExternalForm() );
                String scheme = baseUri.getScheme();
                String auth = baseUri.getAuthority();
                String path = baseUri.getPath();
                if ( path == null ) {
                    path = "";
                }
                path = path.replaceFirst("[^/]*$", "" );
                baseUri = new URI( scheme, auth, path, "", "" );
            }
            catch ( URISyntaxException e ) {
                baseUri = null;
            }
        }
        else {
            baseUri = null;
        }

        /* Write a title element. */
        if ( hasTitle() ) {
            Element titleEl = doc.createElement( XMLNAME_TITLE );
            ndxEl.appendChild( titleEl );
            Node titleNode = doc.createTextNode( getTitle() );
            titleEl.appendChild( titleNode );
        }

        /* Write an image element. */
        HdxResourceType type = HdxResourceType.match( XMLNAME_IMAGE);
        assert type != HdxResourceType.NONE;
        Element imEl = doc.createElement( type,
                                          getImage().getDOMFacade( type ));
        ndxEl.appendChild( imEl );
        if ( getImage().getURL() != null ) {
            URI iuri = urlToUri( getImage().getURL() );
            if ( baseUri != null ) {
                iuri = baseUri.relativize( iuri );
            }
            imEl.setAttribute("url", iuri.toString());
//             Node imUrl = doc.createTextNode( iuri.toString() );
//             imEl.appendChild( imUrl );
        }
        else {
            Node imComm = doc.createComment( "Image array is virtual" );
            imEl.appendChild( imComm );
        }

        /* Write a variance element. */
        if ( hasVariance() ) {
            type = HdxResourceType.match( XMLNAME_VARIANCE);
            assert type != HdxResourceType.NONE;
            Element varEl = doc.createElement( type,
                                              getImage().getDOMFacade( type));
            ndxEl.appendChild( varEl );
            if ( getVariance().getURL() != null ) {
                URI vuri = urlToUri( getVariance().getURL() );
                if ( baseUri != null ) {
                    vuri = baseUri.relativize( vuri );
                }
                varEl.setAttribute("url", vuri.toString());
//             Node varUrl = doc.createTextNode( vuri.toString() );
//             varEl.appendChild( varUrl );
            }
            else {
                Node varComm = doc.createComment
                        ( "Variance array is virtual" );
                varEl.appendChild( varComm );
            }
        }

        /* Write a quality element. */
        if ( hasQuality() ) {
            type = HdxResourceType.match( XMLNAME_QUALITY );
            assert type != HdxResourceType.NONE;
            Element qualEl = doc.createElement
                    ( type,
                      getImage().getDOMFacade( type ));
            ndxEl.appendChild( qualEl );
            if ( getQuality().getURL() != null ) {
                URI quri = urlToUri( getQuality().getURL() );
                if ( baseUri != null ) {
                    quri = baseUri.relativize( quri );
                }
                qualEl.setAttribute("url", quri.toString());
//                 Node qualUrl = doc.createTextNode( quri.toString() );
//                 qualEl.appendChild( qualUrl );
            }
            else {
                Node qualComm = doc.createComment
                        ( "Quality array is virtual" );
                qualEl.appendChild( qualComm );
            }
        }

        /* Write a badbits element. */
        if ( getBadBits() != 0 ) {
            String bbrep = "0x" + Integer.toHexString( getBadBits() );
            Node bbContent = doc.createTextNode( bbrep );
            Element bbEl = doc.createElement( XMLNAME_BADBITS );
            bbEl.appendChild( bbContent );
            ndxEl.appendChild( bbEl );
        }
        
        /* Write a WCS element. */
        if ( hasWCS() ) {
            FrameSet wfset = getAst();
            Source wcsSource = new XAstWriter().makeSource( wfset, null );
            try {
                Node wcsContent = new SourceReader().getDOM( wcsSource );
                wcsContent = importNode( doc, wcsContent ); 
                Element wcsEl = doc.createElement( XMLNAME_WCS );
                wcsEl.setAttribute( "encoding", "AST-XML" );
                wcsEl.appendChild( wcsContent );
                ndxEl.appendChild( wcsEl );
            }
            catch ( TransformerException e ) {
                logger.warning( "Trouble transforming WCS: " + e.getMessage() );
                ndxEl.appendChild( doc.createComment( "Broken WCS" ) );
            }
        }

        /* Write an Etc element. */
        if ( hasEtc() ) {
            try {
                Source etcSrc = getEtc();
                Node etcEl = new SourceReader().getDOM( etcSrc );
                etcEl = importNode( doc, etcEl );

                /* Check that the returned object has the right form. */
                if ( etcEl instanceof Element && 
                     ((Element) etcEl).getTagName() == XMLNAME_ETC ) {
                    ndxEl.appendChild( etcEl );
                }
                else {
                    logger.warning( "Badly-formed Etc component from impl " 
                                  + impl +  "  - not added" );
                    ndxEl.appendChild( doc.createComment( "Broken ETC" ) );
                }
            }
            catch ( TransformerException e ) {
                logger.warning( 
                    "Error transforming Etc component - not added" );
                ndxEl.appendChild( doc.createComment( "Broken ETC" ) );
            }
        }

        return doc;
    }

//     protected Document constructDOM(URL base) {

//         /* Set up the document and root element. */
//         DocumentBuilderFactory dfact = DocumentBuilderFactory.newInstance();
//         DocumentBuilder dbuild;
//         try {
//             dbuild = dfact.newDocumentBuilder();
//         }
//         catch ( ParserConfigurationException e ) {
//             throw new RuntimeException( "Trouble building vanilla parser", e );
//         }
//         Document doc = dbuild.newDocument();
//         Element ndxEl = doc.createElement( "ndx" );
//         doc.appendChild( ndxEl );

//         /* Get the base URI in a form suitable for using with URI.relativize. */
//         URI baseUri;
//         if ( base != null ) {
//             try {
//                 baseUri = new URI( base.toExternalForm() );
//                 String scheme = baseUri.getScheme();
//                 String auth = baseUri.getAuthority();
//                 String path = baseUri.getPath();
//                 if ( path == null ) {
//                     path = "";
//                 }
//                 path = path.replaceFirst( "[^/]*$", "" );
//                 baseUri = new URI( scheme, auth, path, "", "" );
//             }
//             catch ( URISyntaxException e ) {
//                 baseUri = null;
//             }
//         }
//         else {
//             baseUri = null;
//         }

//         /* Write a title element. */
//         if ( hasTitle() ) {
//             Element titleEl = doc.createElement( XMLNAME_TITLE );
//             ndxEl.appendChild( titleEl );
//             Node titleNode = doc.createTextNode( getTitle() );
//             titleEl.appendChild( titleNode );
//         }

//         /* Write an image element. */
//         Element imEl = doc.createElement( XMLNAME_IMAGE );
//         ndxEl.appendChild( imEl );
//         if ( getImage().getURL() != null ) {
//             URI iuri = urlToUri( getImage().getURL() );
//             if ( baseUri != null ) {
//                 iuri = baseUri.relativize( iuri );
//             }
//             Node imUrl = doc.createTextNode( iuri.toString() );
//             imEl.appendChild( imUrl );
//         }
//         else {
//             Node imComm = doc.createComment( "Image array is virtual" );
//             imEl.appendChild( imComm );
//         }

//         /* Write a variance element. */
//         if ( hasVariance() ) {
//             Element varEl = doc.createElement( XMLNAME_VARIANCE );
//             ndxEl.appendChild( varEl );
//             if ( getVariance().getURL() != null ) {
//                 URI vuri = urlToUri( getVariance().getURL() );
//                 if ( baseUri != null ) {
//                     vuri = baseUri.relativize( vuri );
//                 }
//                 Node varUrl = doc.createTextNode( vuri.toString() );
//                 varEl.appendChild( varUrl );
//             }
//             else {
//                 Node varComm = doc.createComment( "Variance array is virtual" );
//                 varEl.appendChild( varComm );
//             }
//         }

//         /* Write a quality element. */
//         if ( hasQuality() ) {
//             Element qualEl = doc.createElement( XMLNAME_QUALITY );
//             ndxEl.appendChild( qualEl );
//             if ( getQuality().getURL() != null ) {
//                 URI quri = urlToUri( getQuality().getURL() );
//                 if ( baseUri != null ) {
//                     quri = baseUri.relativize( quri );
//                 }
//                 Node qualUrl = doc.createTextNode( quri.toString() );
//                 qualEl.appendChild( qualUrl );
//             }
//             else {
//                 Node qualComm = doc.createComment( "Quality array is virtual" );
//                 qualEl.appendChild( qualComm );
//             }
//         }

//         /* Write a badbits element. */
//         if ( getBadBits() != 0 ) {
//             String bbrep = "0x" + Integer.toHexString( getBadBits() );
//             Node bbContent = doc.createTextNode( bbrep );
//             Element bbEl = doc.createElement( XMLNAME_BADBITS );
//             bbEl.appendChild( bbContent );
//             ndxEl.appendChild( bbEl );
//         }
        
//         /* Write a WCS element. */
//         if ( hasWCS() ) {
//             FrameSet wfset = getAst();
//             Source wcsSource = new XAstWriter().makeSource( wfset, null );
//             try {
//                 Node wcsContent = new SourceReader().getDOM( wcsSource );
//                 wcsContent = importNode( doc, wcsContent ); 
//                 Element wcsEl = doc.createElement( XMLNAME_WCS );
//                 wcsEl.setAttribute( "encoding", "AST-XML" );
//                 wcsEl.appendChild( wcsContent );
//                 ndxEl.appendChild( wcsEl );
//             }
//             catch ( TransformerException e ) {
//                 logger.warning( "Trouble transforming WCS: " + e.getMessage() );
//                 ndxEl.appendChild( doc.createComment( "Broken WCS" ) );
//             }
//         }

//         /* Write an Etc element. */
//         if ( hasEtc() ) {
//             try {
//                 Source etcSrc = getEtc();
//                 Node etcEl = new SourceReader().getDOM( etcSrc );
//                 etcEl = importNode( doc, etcEl );

//                 /* Check that the returned object has the right form. */
//                 if ( etcEl instanceof Element && 
//                      ((Element) etcEl).getTagName() == XMLNAME_ETC ) {
//                     ndxEl.appendChild( etcEl );
//                 }
//                 else {
//                     logger.warning( "Badly-formed Etc component from impl " 
//                                   + impl +  "  - not added" );
//                     ndxEl.appendChild( doc.createComment( "Broken ETC" ) );
//                 }
//             }
//             catch ( TransformerException e ) {
//                 logger.warning( 
//                     "Error transforming Etc component - not added" );
//                 ndxEl.appendChild( doc.createComment( "Broken ETC" ) );
//             }
//         }

//         return doc;
//     }

    public DOMFacade getDOMFacade() {
        return new BridgeNdxDOMFacade();
    }

    protected class BridgeNdxDOMFacade
            extends AbstractDOMFacade {
        public Element getDOM(URL base) {
            // If the cached DOM is (still) present, return it immediately.
            if (ndxDocumentCache == null) 
                ndxDocumentCache = constructDOM(base);
            Element de = ndxDocumentCache.getDocumentElement();
            assert de.getTagName().equals("ndx");
            return de;
        }

        public Object getObject(Element el)
                throws HdxException {
            HdxResourceType t = HdxResourceType.match(el);

            if (t == HdxResourceType.NONE)
                throw new HdxException
                        ("getObject was asked to realise an unregistered Type:"
                         + el);
            String tagname = el.getTagName();
            Object ret = null;
            if (t == ndxType)
                ret = BridgeNdx.this;
            else if (tagname.equals(XMLNAME_IMAGE))
                ret = getImage();
            else if (tagname.equals(XMLNAME_VARIANCE))
                ret = getVariance();
            else if (tagname.equals(XMLNAME_QUALITY))
                ret = getQuality();

            // These three are the only HdxResourceTypes which we
            // register at the top.  If ret is still null, then it's
            // because the code down here is out of date: that is,
            // there's a type been registered which hasn't been added
            // here, or else there's an element of a registered type
            // within the Ndx which we don't think ought to be there
            // (do we need to look at the ElementValidator?).  This is
            // a coding error, so throw an assertion error rather than
            // merely an HdxException
            assert ret != null
                    : "Ooops: surprising registered type " + t + " in Ndx";
            
            assert t.getConstructedClass().isInstance(ret);
            
            return ret;
        }

    }
}
