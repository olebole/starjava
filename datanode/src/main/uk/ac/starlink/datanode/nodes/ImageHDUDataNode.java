package uk.ac.starlink.datanode.nodes;

import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.swing.JComponent;
import nom.tam.fits.BasicHDU;
import nom.tam.fits.FitsException;
import nom.tam.fits.Header;
import nom.tam.util.ArrayDataInput;
import uk.ac.starlink.array.AccessMode;
import uk.ac.starlink.array.BridgeNDArray;
import uk.ac.starlink.array.MouldArrayImpl;
import uk.ac.starlink.array.NDArray;
import uk.ac.starlink.array.NDShape;
import uk.ac.starlink.fits.FitsConstants;
import uk.ac.starlink.fits.MappedFile;

/**
 * An implementation of the {@link DataNode} interface for
 * representing Header and Data Units (HDUs) in FITS files.
 *
 * @author   Mark Taylor (Starlink)
 * @version  $Id$
 */
public class ImageHDUDataNode extends HDUDataNode {
    private String name;
    private String description;
    private String hduType;
    private Header header;
    private NDShape shape;
    private final String dataType;
    private String blank;
    private FITSFileDataNode.ArrayDataMaker hdudata;
    private Number badval;

    /**
     * Initialises an <code>ImageHDUDataNode</code> from a <code>Header</code> 
     * object.
     *
     * @param   hdr  a FITS header object
     *               from which the node is to be created.
     * @param   hdudata  an object capable of returning the array data for
     *                   the image
     */
    public ImageHDUDataNode( Header hdr, FITSDataNode.ArrayDataMaker hdudata )
            throws NoSuchDataException {
        super( hdr, hdudata );

        this.header = hdr;
        this.hdudata = hdudata;
        hduType = getHduType();
        if ( hduType != "Image" ) {
            throw new NoSuchDataException( "Not an Image HDU" );
        }

        long[] axes = getDimsFromHeader( hdr );
        int ndim = axes.length;
        boolean ok = axes != null && ndim > 0;
        if ( ok ) {
            for ( int i = 0; i < ndim; i++ ) {
                ok = ok && axes[ i ] > 0;
            }
        }
        if ( ok ) {
            shape = new NDShape( axes );
        }

        boolean hasBlank = hdr.containsKey( "BLANK" );
        badval = null;
        blank = "<none>";
        switch ( hdr.getIntValue( "BITPIX" ) ) {
            case BasicHDU.BITPIX_BYTE:
                dataType = "byte";
                if ( hasBlank ) {
                    int val = hdr.getIntValue( "BLANK" );
                    blank = "" + val;
                    badval = new Byte( (byte) val );
                }
                break;
            case BasicHDU.BITPIX_SHORT:
                dataType = "short";
                if ( hasBlank ) {
                    int val = hdr.getIntValue( "BLANK" );
                    blank = "" + val;
                    badval = new Short( (short) val );
                }
                break;
            case BasicHDU.BITPIX_INT:
                dataType = "int";
                if ( hasBlank ) {
                    int val = hdr.getIntValue( "BLANK" );
                    blank = "" + val;
                    badval = new Integer( val );
                }
                break;
            case BasicHDU.BITPIX_FLOAT:
                dataType = "float";
                blank = null;
                break;
            case BasicHDU.BITPIX_DOUBLE:
                dataType = "double";
                blank = null;
                break;
            case BasicHDU.BITPIX_LONG:
                throw new NoSuchDataException(
                    "64-bit integers not supported by FITS" );
            default:
                dataType = null;
        }

        description = "(" + hduType
                    + ( ( shape != null ) 
                         ? ( " " + NDShape.toString( shape.getDims() ) + " " ) 
                         : "" )
                    + ")";

        /* Set the icon based on the shape of the image. */
        short iconID;
        if ( shape == null ) {
            iconID = IconFactory.HDU;
        }
        else {
            int nd = shape.getNumPixels() == 1 ? 0 : shape.getNumDims();
            iconID = IconFactory.getArrayIconID( nd );
        }
        setIconID( iconID );
    }

    public boolean allowsChildren() {
        // return false;
        return false;
    }

    public Iterator getChildIterator() {
        List children = new ArrayList();
        return children.iterator();
    }

    public void configureDetail( DetailViewer dv ) {
        super.configureDetail( dv );
        dv.addSeparator();
        if ( shape != null ) {
            dv.addKeyedItem( "Shape", NDShape.toString( shape.getDims() ) );
        }
        if ( dataType != null ) {
            dv.addKeyedItem( "Pixel type", dataType ); 
        }
        if ( blank != null ) {
            dv.addKeyedItem( "Blank value", blank );
        }

    }

    public String getDescription() {
        return description;
    }

    public String getNodeTLA() {
        return "IMG";
    }

    public String getNodeType() {
        return "FITS Image HDU";
    }

    static long[] getDimsFromHeader( Header hdr ) {
        try {
            int naxis = hdr.getIntValue( "NAXIS" );
            long[] dimensions = new long[ naxis ];
            for ( int i = 0; i < naxis; i++ ) {
                String key = "NAXIS" + ( i + 1 );
                if ( hdr.containsKey( key ) ) {
                    dimensions[ i ] =  hdr.getLongValue( key );
                }
                else {
                    throw new FitsException( "No header card + " + key );
                }
            }
            return dimensions;
        }
        catch ( Exception e ) {
            return null;
        }
    }

    public boolean hasDataObject( DataType dtype ) {
            return super.hasDataObject( dtype );
    }

    public Object getDataObject( DataType dtype ) throws DataObjectException {
            return super.getDataObject( dtype );
    }

}
