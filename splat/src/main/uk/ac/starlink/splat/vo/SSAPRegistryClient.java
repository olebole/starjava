package uk.ac.starlink.splat.vo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import uk.ac.starlink.registry.AbstractRegistryClient;
import uk.ac.starlink.registry.ResourceSink;
import uk.ac.starlink.registry.SoapClient;

/**
 * RegistryClient concrete subclass which turns registry queries into
 * SSAPRegResource elements.
 *
 * <p>This class is implemented using an ad-hoc XPath-like mechanism for
 * accumulating items of interest from the SAX stream, while ignoring
 * anything else.  It works well, and is fast.  However there are certainly 
 * other, probably more flexible and better, ways of doing similar things.
 * Probably there are libraries that can help with this sort of thing.
 *
 * @author   Mark Taylor
 */
public class SSAPRegistryClient extends AbstractRegistryClient<SSAPRegResource> {

    /**
     * Constructor.
     *
     * @param   soapClient  object which performs SOAP communications
     */
    public SSAPRegistryClient( SoapClient soapClient ) {
        super( soapClient );
    }

    @Override
    protected ContentHandler
            createResourceHandler( ResourceSink<SSAPRegResource> sink ) {
        return new SSAPRegResourceHandler( sink );
    }

    /**
     * SAX ContentHandler implementation which processes a SearchResponse
     * registry response to generate a list of SSAPRegResources.
     *
     * @author   Mark Taylor
     */
    private static class SSAPRegResourceHandler extends DefaultHandler {
    
        /* Define XPath-like strings representing the location in the XML
         * content model of resource and capability items that we are
         * interested in. */
        private static final String RESOURCE_PATH =      "/SearchResponse/VOResources/Resource";
            
        private static final String TITLE_PATH =           RESOURCE_PATH + "/title";
        private static final String IDENTIFIER_PATH =      RESOURCE_PATH + "/identifier";
        private static final String SHORTNAME_PATH =       RESOURCE_PATH + "/shortName";
        private static final String STATUS_PATH =          RESOURCE_PATH + "/@status";
        private static final String PUBLISHER_PATH =       RESOURCE_PATH + "/curation/publisher";
        private static final String CONTACTNAME_PATH =     RESOURCE_PATH + "/curation/contact/name";
        private static final String CONTACTEMAIL_PATH =    RESOURCE_PATH + "/curation/contact/email";
        private static final String SUBJECT_PATH =         RESOURCE_PATH + "/content/subject";
        private static final String REFURL_PATH =          RESOURCE_PATH + "/content/referenceURL";
        private static final String RESVERSION_PATH =      RESOURCE_PATH + "/version";
        
        private static final String CAPABILITY_PATH =      RESOURCE_PATH + "/capability";

        private static final String STDID_PATH =         CAPABILITY_PATH + "/@standardID";
        private static final String XSITYPE_PATH =       CAPABILITY_PATH + "/@xsi:type";
        private static final String DATASOURCE_PATH =    CAPABILITY_PATH + "/dataSource";
        private static final String CREATIONTYPE_PATH =  CAPABILITY_PATH + "/creationType";

        private static final String CAPINTERFACE_PATH =  CAPABILITY_PATH + "/interface"; 
        
        private static final String ACCESSURL_PATH =   CAPINTERFACE_PATH + "/accessURL";
        private static final String VERSION_PATH =     CAPINTERFACE_PATH + "/@version";

        private static final String CONTENT_PATH =         RESOURCE_PATH + "/content";
               
        private static final String DESCRIPTION_PATH =      CONTENT_PATH + "/description";
        private static final String DATATYPE_PATH =         CONTENT_PATH + "/type";

        private static final String COVERAGE_PATH =         RESOURCE_PATH + "/coverage";
        private static final String WAVEBAND_PATH =         COVERAGE_PATH + "/waveband";
    
        private Store resourceStore_;
        private Store capabilityStore_;
        private StringBuffer txtBuf_;
        private List<SSAPRegCapability> capabilityList_;
        private ArrayList<String> wavebandList_;
        private final Set<String> resourcePathSet_;
        private final Set<String> capabilityPathSet_;
        private final StringBuffer path_;
        private final ResourceSink<SSAPRegResource> sink_;
    
        /**
         * Constructor.
         *
         * @param  sink  receiver for resource elements produced by this parser
         */
        public SSAPRegResourceHandler( ResourceSink<SSAPRegResource> sink ) {
            sink_ = sink;
            path_ = new StringBuffer();

            /* Identify the infoset items that will be associated with one
             * generated SSAPRegResource object. */
            resourcePathSet_ = new HashSet<String>( Arrays
                                                   .asList( new String[] {
                TITLE_PATH,
                IDENTIFIER_PATH,       
                SHORTNAME_PATH,
                PUBLISHER_PATH,
                CONTACTNAME_PATH,
                CONTACTEMAIL_PATH,
                SUBJECT_PATH,
                REFURL_PATH,
                STATUS_PATH,
                WAVEBAND_PATH,
            } ) );

            /* Identify the infoset items that will be associated with one
             * generated SSAPRegCapability object. */
            capabilityPathSet_ = new HashSet<String>( Arrays
                                                     .asList( new String[] {
                DATATYPE_PATH,
                STDID_PATH,
                XSITYPE_PATH,          
                DESCRIPTION_PATH,           
                ACCESSURL_PATH,
                VERSION_PATH,
                DATASOURCE_PATH,
                CREATIONTYPE_PATH, 
               
            } ) );
        }
    
        @Override
        public void startElement( String namespaceURI, String localName,
                                  String qName, Attributes atts )
                throws SAXException {
            try {
                path_.append( '/' )
                     .append( localName );
                String path = path_.toString();

                /* If this is the start of a Resource element, prepare to
                 * accumulate items which will form a SSAPRegResource object. */
                if ( RESOURCE_PATH.equals( path ) ) {
                    resourceStore_ = new Store();
                    capabilityList_ = new ArrayList<SSAPRegCapability>();
                    wavebandList_ = new ArrayList<String>();
                }

                /* If this is the start of a capability element, prepare to
                 * accumulate items which will form a SSAPRegCapability object. */
                else if ( CAPABILITY_PATH.equals( path ) || CONTENT_PATH.equals(path) ) {
                    if ( capabilityStore_ == null )
                        capabilityStore_ = new Store();
                }
              
                /* If this is a string-containing element we're interested in,
                 * prepare to collect its text. */
                else if ( resourcePathSet_.contains( path ) ||
                          capabilityPathSet_.contains( path ) ) {
                    txtBuf_ = new StringBuffer();
                }

                /* Collect attribute values of interest and associated them
                 * with the current resource or capability as required. */
                int natt = atts.getLength();
                for ( int ia = 0; ia < natt; ia++ ) {
                    String apath = path + "/@" + atts.getQName( ia );
                    if ( resourcePathSet_.contains( apath ) ) {
                        resourceStore_.put( apath, atts.getValue( ia ) );
                    }
                    else if ( capabilityPathSet_.contains( apath ) ) {
                        capabilityStore_.put( apath, atts.getValue( ia ) );
                    }
                }
            }
            catch ( RuntimeException e ) {
                throw new SAXException( e );
            }
        }
    
        @Override
        public void endElement( String namepaceURI, String localName,
                                String qName )
                throws SAXException {
            try {
                String path = path_.toString();

                /* If this is the end of a Resource element, produce a new
                 * SSAPRegResource and feed it to the sink. */
                if ( RESOURCE_PATH.equals( path ) ) {
                    SSAPRegCapability[] caps = capabilityList_.toArray( new SSAPRegCapability[ 0 ] );
                    capabilityList_ = null;
                    String[] wband = wavebandList_.toArray(new String[wavebandList_.size()]);
                    sink_.addResource( createSSAPRegResource( resourceStore_, caps , wband) );
                    resourceStore_ = null;
                }

                /* If this is the end of a capability/interface element,
                 * produce a new SSAPRegCapability and associate it with
                 * the current resource. */
              //  else if ( CAPINTERFACE_PATH.equals( path ) ) {
              //      capabilityList_.add( createSSAPRegCapability(
             //                                capabilityStore_ ) );
            //        for ( Iterator<String> it =
              //                    capabilityStore_.keySet().iterator();
              //            it.hasNext(); ) {
             //           if ( it.next().startsWith( path ) ) {
             //               it.remove();
             //           }
             //       }
            //    }
                else if ( CAPABILITY_PATH.equals( path ) ) {
                    capabilityList_.add( createSSAPRegCapability( capabilityStore_ ) );
                    for ( Iterator<String> it =  capabilityStore_.keySet().iterator(); it.hasNext(); ) {
                        if ( it.next().startsWith( path ) ) {
                            it.remove();
                        }
                    }
                    
                    capabilityStore_ = null;
                }
                else if (WAVEBAND_PATH.equals(path)) {
                    
                    wavebandList_.add(txtBuf_.toString() );
                    txtBuf_ = null;
                }
                else if ( resourcePathSet_.contains( path ) ) {
                    
                    resourceStore_.put( path, txtBuf_.toString() );
                    txtBuf_ = null;
                }
                else if ( capabilityPathSet_.contains( path ) ) {
                    capabilityStore_.put( path, txtBuf_.toString() );
                    txtBuf_ = null;
                }
                path_.setLength( path_.length() - localName.length() - 1 );
            }
            catch ( RuntimeException e ) {
                throw new SAXException( e );
            }
        }
    
        @Override
        public void characters( char[] ch, int start, int length ) {

            /* Accumulate text within a text-containing element of interest. */
            if ( txtBuf_ != null ) {
                txtBuf_.append( ch, start, length );
            }
        }
    
        /**
         * Constructs and returns a new SSAPRegResource.
         *
         * @param  rMap  map of resource values with XPath-like paths as keys
         * @param  capabilities  capabilities of this resource
         */
        private static SSAPRegResource
                createSSAPRegResource( final Store rStore,
                                     final SSAPRegCapability[] caps, final String[] wband ) {
            SSAPRegResource resource = new SSAPRegResource();
            resource.setCapabilities( caps );
            resource.setTitle( rStore.removeScalar( TITLE_PATH ) );
            resource.setIdentifier( rStore.removeScalar( IDENTIFIER_PATH ) );
          //  resource.setIdentifier( rStore.removeScalar( IDENTIFIER_PATH ) );
            resource.setShortName( rStore.removeScalar( SHORTNAME_PATH ) );
            resource.setPublisher( rStore.removeScalar( PUBLISHER_PATH ) );
            resource.setContact( makeContact(
                                   rStore.removeScalar( CONTACTNAME_PATH ),
                                   rStore.removeScalar( CONTACTEMAIL_PATH ) ) );
            resource.setReferenceUrl( rStore.removeScalar( REFURL_PATH ) );
            resource.setSubjects( rStore.removeArray( SUBJECT_PATH ) );
            String status = rStore.removeScalar( STATUS_PATH );
            resource.setContentType( rStore.removeScalar( DATATYPE_PATH ) );
            resource.setWaveband( wband );
            assert rStore.keySet().isEmpty();
            return resource;
        }
    
        /**
         * Constructs and returns a new SSAPRegCapability.
         *
         * @param  cMap  map of capability values with XPath-like paths as keys
         */
        private static SSAPRegCapability
                createSSAPRegCapability( final Store cStore ) {
            SSAPRegCapability cap = new SSAPRegCapability();
            cap.setAccessUrl( cStore.removeScalar( ACCESSURL_PATH ) );
            cap.setDescription( cStore.removeScalar( DESCRIPTION_PATH ) );
            cap.setStandardId( cStore.removeScalar( STDID_PATH ) );
            cap.setVersion( cStore.removeScalar( VERSION_PATH ) );
            cap.setXsiType( cStore.removeScalar( XSITYPE_PATH ) );
            cap.setDataSource( cStore.removeScalar( DATASOURCE_PATH ) );
            
            cap.setCreationType( cStore.removeScalar( CREATIONTYPE_PATH ) );
            assert cStore.keySet().isEmpty();
            return cap;
        }
    
        /**
         * Amalgamates a name and email address into a single string.
         *
         * @param  name  name
         * @param  email   email address
         * @return  combined contact string
         */
        private static String makeContact( String name, String email ) {
            if ( email != null && name != null ) {
                return name + " <" + email + ">";
            }
            else if ( email != null ) {
                return email;
            }
            else if ( name != null ) {
                return name;
            }
            else {
                return null;
            }
        }

        /**
         * Map-like store for key-value pairs.  The values can store multiple
         * items however.
         */
        private static class Store {
            private final Map<String,Collection<String>> map_;

            /**
             * Constructor.
             */
            Store() {
                map_ = new HashMap<String,Collection<String>>();
            }

            /**
             * Adds an item under a given key.
             * String values are trimmed, and null or empty ones are ignored.
             *
             * @param  key   key string
             * @param  value  value element
             */
            public void put( String key, String value ) {
                String tval = value.trim();
                if ( tval.length() > 0 ) {
                    if ( ! map_.containsKey( key ) ) {
                        map_.put( key, new ArrayList<String>() );
                    }
                    map_.get( key ).add( tval );
                }
            }

            /**
             * Retrieves a single value from the store, and removes its entry.
             * If multiple values have been stored under that key, the
             * first one is returned.
             *
             * @param   key  key
             * @return  single entry, or null
             */
            public String removeScalar( String key ) {
                Collection<String> list = map_.remove( key );
                return list == null ? null : list.iterator().next();
            }

            /**
             * Retrieves an array of values from the store, and removes its
             * entry.
             *
             * @param  key key
             * @return  array entry, possibly with zero elements
             */
            public String[] removeArray( String key ) {
                Collection<String> list = map_.remove( key );
                return list == null ? new String[ 0 ]
                                    : list.toArray( new String[ 0 ] );
            }

            /**
             * Returns a modifiable set of keys currently present in the store.
             *
             * @return  key set
             */
            public Set<String> keySet() {
                return map_.keySet();
            }
        }
    }
}
