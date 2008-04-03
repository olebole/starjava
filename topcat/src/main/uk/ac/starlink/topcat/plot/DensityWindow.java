package uk.ac.starlink.topcat.plot;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.swing.Action;
import javax.swing.Box;
import javax.swing.ComboBoxModel;
import javax.swing.Icon;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JPanel;
import javax.swing.OverlayLayout;
import javax.swing.filechooser.FileFilter;
import nom.tam.fits.BasicHDU;
import nom.tam.fits.FitsException;
import nom.tam.fits.Header;
import org.votech.plastic.PlasticHubListener;
import uk.ac.starlink.fits.FitsConstants;
import uk.ac.starlink.plastic.ApplicationItem;
import uk.ac.starlink.plastic.MessageId;
import uk.ac.starlink.plastic.PlasticTransmitter;
import uk.ac.starlink.table.ValueInfo;
import uk.ac.starlink.table.Tables;
import uk.ac.starlink.topcat.BasicAction;
import uk.ac.starlink.topcat.ControlWindow;
import uk.ac.starlink.topcat.ResourceIcon;
import uk.ac.starlink.topcat.SuffixFileFilter;
import uk.ac.starlink.topcat.ToggleButtonModel;
import uk.ac.starlink.topcat.TopcatPlasticListener;
import uk.ac.starlink.topcat.TopcatTransmitter;
import uk.ac.starlink.topcat.TopcatUtils;
import uk.ac.starlink.ttools.func.Maths;
import uk.ac.starlink.ttools.func.Times;
import uk.ac.starlink.util.URLUtils;
import uk.ac.starlink.util.gui.ChangingComboBoxModel;
import uk.ac.starlink.util.gui.ErrorDialog;
import uk.ac.starlink.util.gui.ShrinkWrapper;

import uk.ac.starlink.tplot.*;

/**
 * Graphics window which displays a density plot, that is a 2-dimensional
 * histogram.  Each screen pixel corresponds to a bin of the 2-d histogram,
 * and is coloured according to how many items fall into it.
 *
 * @author   Mark Taylor
 * @since    1 Dec 2005
 */
public class DensityWindow extends GraphicsWindow {

    private final DensityPlot plot_;
    private final JComponent plotPanel_;
    private final BlobPanel blobPanel_;
    private final Action blobAction_;
    private final ToggleButtonModel rgbModel_;
    private final ToggleButtonModel zLogModel_;
    private final ToggleButtonModel weightModel_;
    private final CutChooser cutter_;
    private final JLabel cutLabel_;
    private final PixelSizeAction pixIncAction_;
    private final PixelSizeAction pixDecAction_;
    private final Action fitsAction_;
    private final DensityStyle[] styles_;
    private final List rgbRepaintList_;
    private final ComboBoxModel shaderModel_;
    private int pixelSize_ = 1;

    private static FileFilter fitsFilter_ =
        new SuffixFileFilter( new String[] { ".fits", ".fit", ".fts", } );
    private static final String[] AXIS_NAMES = new String[] { "X", "Y" };
    static final Shader[] INDEXED_SHADERS = new Shader[] {
        Shaders.LUT_HEAT,
        Shaders.LUT_LIGHT,
        Shaders.LUT_PASTEL,
        Shaders.BLACK_WHITE,
        Shaders.LUT_RAINBOW,
        Shaders.RED_BLUE,
        Shaders.LUT_STAIRCASE,
        Shaders.LUT_MANYCOL,
    };

    /**
     * Constructs a new DensityWindow.
     *
     * @param   parent   parent component (may be used for positioning)
     */
    public DensityWindow( Component parent ) {
        super( "Density Map", AXIS_NAMES, 0, false,
               new ErrorModeSelectionModel[ 0 ], parent );

        /* There's only one style set it makes sense to use for this window.
         * Construct it here. */
        styles_ = new DensityStyle[] {
            new DStyle( DensityStyle.RED ),
            new DStyle( DensityStyle.GREEN ),
            new DStyle( DensityStyle.BLUE ),
        };

        /* Construct a plotting surface to receive the graphics. */
        setPadRatio( 0 );
        final PlotSurface surface = new PtPlotSurface();
        ((PtPlotSurface) surface)._tickLength = 0;

        /* Grid looks a bit messy, so turn it off. */
        getGridModel().setSelected( false );

        /* Construct and populate the plot panel with the 2d histogram
         * itself and a transparent layer for doodling blobs on. */
        plot_ = new DensityPlot( surface );

        /* Zooming. */
        final SurfaceZoomRegionList zoomRegions =
                new SurfaceZoomRegionList( plot_ ) {
            protected void requestZoom( double[][] bounds ) {
                for ( int idim = 0; idim < 2; idim++ ) {
                    if ( bounds[ idim ] != null ) {
                        getAxisWindow().getEditors()[ idim ].clearBounds();
                        getViewRanges()[ idim ].setBounds( bounds[ idim ] );
                    }
                }
                replot();
            }
        };
        Zoomer zoomer = new Zoomer();
        zoomer.setRegions( zoomRegions );
        zoomer.setCursorComponent( plot_ );
        Component scomp = plot_.getSurface().getComponent();
        scomp.addMouseListener( zoomer );
        scomp.addMouseMotionListener( zoomer );

        /* Respond to plot changes. */
        plot_.addPlotListener( new PlotListener() {
            public void plotChanged( PlotEvent evt ) {
                zoomRegions.reconfigure();
                DensityPlotEvent devt = (DensityPlotEvent) evt;
                String cutter = 
                    getCutLabelText( devt.getLoCuts(), devt.getHiCuts(),
                                     ((DensityPlotState) devt.getPlotState())
                                    .getWeighted() );
                cutLabel_.setText( cutter );
            }
        } );

        plotPanel_ = new JPanel();
        plotPanel_.setOpaque( false );
        blobPanel_ = new BlobPanel() {
            protected void blobCompleted( Shape blob ) {
                addNewSubsets( plot_.getContainedMask( blob ) );
            }
        };
        blobPanel_.setColors( new Color( 0x80a0a0a0, true ),
                              new Color( 0xc0a0a0a0, true ) );
        blobAction_ = blobPanel_.getBlobAction();
        plotPanel_.setLayout( new OverlayLayout( plotPanel_ ) );
        plotPanel_.add( blobPanel_ );
        plotPanel_.add( plot_ );

        /* Construct and add a status line. */
        PlotStatsLabel plotStatus = new PlotStatsLabel();
        plot_.addPlotListener( plotStatus );
        PositionLabel posStatus = new PositionLabel( surface );
        posStatus.setMaximumSize( new Dimension( Integer.MAX_VALUE,
                                                 posStatus.getMaximumSize()
                                                          .height ) );
        getStatusBox().add( plotStatus );
        getStatusBox().add( Box.createHorizontalStrut( 5 ) );
        getStatusBox().add( posStatus );

        /* Listener which repaints legends when their icons might have
         * changed. */
        ActionListener legendPainter = new ActionListener() {
            public void actionPerformed( ActionEvent evt ) {
                for ( Iterator it = rgbRepaintList_.iterator();
                      it.hasNext(); ) {
                    ((JComponent) it.next()).repaint();
                }
            }
        };

        /* Action for rgb/greyscale toggle. */
        rgbModel_ = new ToggleButtonModel( "RGB", ResourceIcon.COLOR,
                                           "Select red/green/blue or " +
                                           "indexed rendering" );
        rgbModel_.setSelected( true );
        rgbModel_.addActionListener( getReplotListener() );
        rgbRepaintList_ = new ArrayList();
        rgbModel_.addActionListener( legendPainter );

        /* Model for the shader which controls the indexed (non-RGB) 
         * colour map. */
        shaderModel_ = new ChangingComboBoxModel( INDEXED_SHADERS );
        ((ChangingComboBoxModel) shaderModel_)
                                .addChangeListener( getReplotListener() );
        ((ChangingComboBoxModel) shaderModel_)
                                .addActionListener( legendPainter );

        /* Action for linear/log scale for colour map. */
        zLogModel_ = new ToggleButtonModel( "Log Intensity",
                                            ResourceIcon.COLOR_LOG,
                                            "Pixel colours represent log of " +
                                            "counts" );
        zLogModel_.setSelected( false );
        zLogModel_.addActionListener( getReplotListener() );

        /* Action for weighting of histogram values. */
        weightModel_ =
            new ToggleButtonModel( "Weight Counts", ResourceIcon.WEIGHT,
                                   "Allow weighting of histogram counts" );
        weightModel_.addActionListener( getReplotListener() );

        /* Actions for altering pixel size. */
        pixIncAction_ =
            new PixelSizeAction( "Bigger Pixels", ResourceIcon.ROUGH,
                                 "Increase number of screen pixels per bin",
                                 +1 );
        pixDecAction_ =
            new PixelSizeAction( "Smaller Pixels", ResourceIcon.FINE,
                                 "Decrease number of screen pixels per bin",
                                 -1 );

        /* Action for exporting FITS file. */
        fitsAction_ = new ExportAction( "FITS", ResourceIcon.FITS, 
                                        "Save image as FITS array",
                                        fitsFilter_ ) {
            public void exportTo( OutputStream out ) throws IOException {
                try {
                    exportFits( out );
                }
                catch ( FitsException e ) {
                    throw (IOException) new IOException( e.getMessage() )
                                       .initCause( e );
                }
            }
        };

        /* PLASTIC transmitter for transmitting image as FITS. */
        PlasticTransmitter imageTransmitter =
                new TopcatTransmitter( ControlWindow.getInstance()
                                                    .getPlasticServer(),
                                       MessageId.FITS_LOADIMAGE,
                                       "FITS image" ) {
            protected void transmit( PlasticHubListener hub, URI clientId,
                                     ApplicationItem app )
                    throws IOException {
                transmitFits( hub, clientId,
                              app == null ? null : new URI[] { app.getId() } );
            }
        };

        /* Update export menu. */
        getExportMenu().add( fitsAction_ );
        getExportMenu().addSeparator();
        getExportMenu().add( imageTransmitter.getBroadcastAction() );
        getExportMenu().add( imageTransmitter.createSendMenu() );

        /* Cut level adjuster widgets. */
        cutter_ = new CutChooser(); 
        cutter_.setLowValue( 0.1 );
        cutter_.setHighValue( 0.9 );
        cutter_.addChangeListener( getReplotListener() );
        cutLabel_ = new JLabel( "0 \u2014 0" );
        JComponent cutBox = Box.createVerticalBox();
        cutBox.setBorder( makeTitledBorder( "Cut Percentile Levels" ) );
        JComponent clbox = Box.createHorizontalBox();
        clbox.add( Box.createHorizontalGlue() );
        clbox.add( cutLabel_ );
        clbox.add( Box.createHorizontalGlue() );
        cutBox.add( cutter_ );
        cutBox.add( clbox ); 

        /* Indexed colourmap selector. */
        JComponent shBox = Box.createHorizontalBox();
        final JComboBox shaderSelector = new JComboBox( shaderModel_ );
        shaderSelector.setRenderer( Shaders.createRenderer( shaderSelector ) );
        shBox.add( Box.createHorizontalStrut( 5 ) );
        shBox.add( new ShrinkWrapper( shaderSelector ) );
        shBox.add( Box.createHorizontalStrut( 5 ) );
        shBox.add( new ComboBoxBumper( shaderSelector ) );
        shBox.add( Box.createHorizontalStrut( 5 ) );
        JComponent shaderBox = Box.createVerticalBox();
        shaderBox.add( shBox );
        shaderBox.add( Box.createVerticalGlue() );
        shaderBox.setBorder( makeTitledBorder( "Indexed Colours" ) );
        rgbModel_.addActionListener( new ActionListener() {
            public void actionPerformed( ActionEvent evt ) {
                shaderSelector.setEnabled( ! rgbModel_.isSelected() );
            }
        } );
        shaderSelector.setEnabled( ! rgbModel_.isSelected() );

        /* Place controls. */
        JComponent controlBox = Box.createHorizontalBox();
        controlBox.add( cutBox );
        controlBox.add( shaderBox );
        getExtrasPanel().add( controlBox );

        /* General plot operation menu. */
        JMenu plotMenu = new JMenu( "Plot" );
        plotMenu.setMnemonic( KeyEvent.VK_P );
        plotMenu.add( weightModel_.createMenuItem() );
        plotMenu.add( getRescaleAction() );
        plotMenu.add( getAxisEditAction() );
        plotMenu.add( getLegendModel().createMenuItem() );
        plotMenu.add( getReplotAction() );
        getJMenuBar().add( plotMenu );

        /* Axis operation menu. */
        JMenu axisMenu = new JMenu( "Axes" );
        axisMenu.setMnemonic( KeyEvent.VK_A );
        axisMenu.add( getFlipModels()[ 0 ].createMenuItem() );
        axisMenu.add( getFlipModels()[ 1 ].createMenuItem() );
        axisMenu.addSeparator();
        axisMenu.add( getLogModels()[ 0 ].createMenuItem() );
        axisMenu.add( getLogModels()[ 1 ].createMenuItem() );
        axisMenu.add( zLogModel_.createMenuItem() );
        getJMenuBar().add( axisMenu );

        /* View menu. */
        JMenu viewMenu = new JMenu( "View" );
        viewMenu.setMnemonic( KeyEvent.VK_V );
        axisMenu.add( rgbModel_.createMenuItem() );
        axisMenu.add( pixIncAction_ );
        axisMenu.add( pixDecAction_ );

        /* Subset operation menu. */
        JMenu subsetMenu = new JMenu( "Subsets" );
        subsetMenu.setMnemonic( KeyEvent.VK_S );
        Action fromVisibleAction = new BasicAction( "New subset from visible",
                                                    ResourceIcon.VISIBLE_SUBSET,
                                                    "Define a new row subset " +
                                                    "containing only " +
                                                    "currently visible data" ) {
            public void actionPerformed( ActionEvent evt ) {
                addNewSubsets( plot_.getVisibleMask() );
            }
        };
        subsetMenu.add( blobAction_ );
        subsetMenu.add( fromVisibleAction );
        getJMenuBar().add( subsetMenu );

        /* PLASTIC interoperability menu. */
        JMenu interopMenu = new JMenu( "Interop" );
        interopMenu.setMnemonic( KeyEvent.VK_I );
        interopMenu.add( imageTransmitter.getBroadcastAction() );
        interopMenu.add( imageTransmitter.createSendMenu() );
        getJMenuBar().add( interopMenu );

        /* Add actions to the toolbar. */
        getPointSelectorToolBar().addSeparator();
        getPointSelectorToolBar().add( weightModel_.createToolbarButton() );
        getToolBar().add( fitsAction_ );
        getToolBar().add( getRescaleAction() );
        getToolBar().add( zLogModel_.createToolbarButton() );
        getToolBar().add( rgbModel_.createToolbarButton() );
        getToolBar().add( getLegendModel().createToolbarButton() );
        getToolBar().add( pixIncAction_ );
        getToolBar().add( pixDecAction_ );
        getToolBar().add( blobAction_ );
        getToolBar().add( fromVisibleAction );
        getToolBar().addSeparator();

        /* Add standard help actions. */
        addHelp( "DensityWindow" );

        /* Perform an initial plot. */
        replot();
    }

    protected void init() {
        super.init();
        AxisWindow axwin = getAxisWindow();
        AxisEditor[] axes = axwin.getEditors();
        axwin.setEditors( new AxisEditor[] { axes[ 0 ], axes[ 1 ], } );
    }

    protected JComponent getPlot() {
        return plotPanel_;
    }

    protected StyleEditor createStyleEditor() {
        final StyleEditor editor = new DensityStyleEditor( styles_, rgbModel_ );
        rgbRepaintList_.add( editor );
        return editor;
    }

    protected PointSelector createPointSelector() {

        AxesSelector axsel =
            new CartesianAxesSelector( AXIS_NAMES, getLogModels(),
                                       getFlipModels(),
                                       new ErrorModeSelectionModel[ 0 ] );
        final WeightedAxesSelector waxsel = new WeightedAxesSelector( axsel ) {
            public AxisEditor[] createAxisEditors() {
                AxisEditor[] xyeds = super.createAxisEditors();
                AxisEditor zed = new AxisEditor( "Colour" );
                return new AxisEditor[] { xyeds[ 0 ], xyeds[ 1 ], zed, };
            }
        };
        waxsel.enableWeights( weightModel_.isSelected() );
        weightModel_.addActionListener( new ActionListener() {
            public void actionPerformed( ActionEvent evt ) {
                waxsel.enableWeights( weightModel_.isSelected() );
            }
        } );
        PointSelector psel = new PointSelector( waxsel, getStyles() );
        rgbRepaintList_.add( psel );
        return psel;
    }

    /**
     * Indicates whether any of the active point selectors have non-trivial
     * weighting axes.
     *
     * @return  true  if some weighting may be in force
     */
    private boolean hasWeights() {
        PointSelectorSet psels = getPointSelectors();
        for ( int ip = 0; ip < psels.getSelectorCount(); ip++ ) {
            if ( ((WeightedAxesSelector)
                  psels.getSelector( ip ).getAxesSelector()).hasWeights() ) {
                return true;
            }
        }
        return false;
    }

    public int getMainRangeCount() {
        return 2;
    }

    protected PlotState createPlotState() {
        return new DensityPlotState();
    }

    public PlotState getPlotState() {
        DensityPlotState state = (DensityPlotState) super.getPlotState();
        boolean valid = state != null && state.getValid();
        state.setRgb( rgbModel_.isSelected() );
        state.setLogZ( zLogModel_.isSelected() );
        state.setLoCut( cutter_.getLowValue() );
        state.setHiCut( cutter_.getHighValue() );
        state.setPixelSize( pixelSize_ );
        state.setWeighted( hasWeights() );
        state.setIndexedShader( rgbModel_.isSelected()
                              ? null
                              : (Shader) shaderModel_.getSelectedItem() );
        pixIncAction_.configureEnabledness();
        pixDecAction_.configureEnabledness();
        return state;
    }

    protected void doReplot( PlotState state, Points points ) {

        /* Cancel any current blob drawing. */
        blobPanel_.setActive( false );

        /* Send the plot component the most up to date plotting state. */
        plot_.setPoints( points );
        plot_.setState( state );

        /* Schedule for repainting so changes can take effect. */
        plot_.repaint();
    }

    public Rectangle getPlotBounds() {
        Rectangle bounds =
            new Rectangle( plot_.getSurface().getClip().getBounds() );
        bounds.y--;
        bounds.height += 2;
        return bounds;
    }

    public StyleSet getDefaultStyles( int npoint ) {
        return new StyleSet() {
            public String getName() {
                return "RGB";
            }
            public Style getStyle( int index ) {
                return styles_[ index % styles_.length ];
            }
        };
    }

    protected boolean isLegendInteresting( PlotState state ) {
        return super.isLegendInteresting( state )
            && ((DensityPlotState) state).getRgb();
    }

    /**
     * Returns label text to use which indicates per-channel cut levels.
     *
     * @param  loCuts  per-channel lower absolute cut level array
     * @param  hiCuts  per-channel upper absolute cut level array
     * @param  weighted  true iff weighting is in use
     */
    private static String getCutLabelText( double[] loCuts, double[] hiCuts,
                                           boolean weighted ) {
        StringBuffer sbuf = new StringBuffer();
        for ( int i = 0; loCuts != null && i < loCuts.length; i++ ) {
            if ( i > 0 ) {
                sbuf.append( ";  " );
            }
            if ( weighted ) {
                sbuf.append( (float) loCuts[ i ] )
                    .append( " \u2014 " )
                    .append( (float) hiCuts[ i ] );
            }
            else {
                sbuf.append( (int) loCuts[ i ] )
                    .append( " \u2014 " )
                    .append( (int) hiCuts[ i ] );
            }
        }
        return sbuf.toString();
    }

    /**
     * Transmits the currently plotted image as a FITS file to PLASTIC
     * listeners.
     *
     * @param  hub  hub object
     * @param  plasticId  registration ID for this applicaition
     * @param  recipients  list of targets PLASTIC ids for this message;
     *         if null broadcast to all
     */
    private void transmitFits( final PlasticHubListener hub,
                               final URI plasticId,
                               final URI[] recipients ) throws IOException {

        /* Write the data as a FITS image to a temporary file preparatory
         * to broadcast. */
        final File tmpfile = File.createTempFile( "plastic", ".fits" );
        final String tmpUrl = URLUtils.makeFileURL( tmpfile ).toString();
        tmpfile.deleteOnExit();
        OutputStream ostrm = 
            new BufferedOutputStream( new FileOutputStream( tmpfile ) );
        try {
            exportFits( ostrm );
        }
        catch ( IOException e ) {
            tmpfile.delete();
            throw e;
        }
        catch ( FitsException e ) {
            tmpfile.delete();
            throw (IOException) new IOException( e.getMessage() )
                               .initCause( e );
        }
        finally {
            ostrm.close();
        }

        /* Do the broadcast, synchronously so that we don't delete the
         * temporary file to early, but in another thread so we don't block
         * the GUI. */
        new Thread( "FITS broadcast" ) {
            public void run() {
                List argList = Arrays.asList( new Object[] { tmpUrl, tmpUrl } );
                URI msgId = MessageId.FITS_LOADIMAGE;
                Map responses = recipients == null
                    ? hub.request( plasticId, msgId, argList )
                    : hub.requestToSubset( plasticId, msgId, argList,
                                           Arrays.asList( recipients ) );
                tmpfile.delete();
            }
        }.start();
    }

    /**
     * Exports the grids currently displayed in the plot as a FITS image
     * (primary HDU).
     *
     * @param   ostrm   output stream
     */
    private void exportFits( OutputStream ostrm )
            throws IOException, FitsException {
        final DataOutputStream out = new DataOutputStream( ostrm );

        BinGrid[] grids = plot_.getBinnedData();
        DensityPlotState state = (DensityPlotState) plot_.getState();
        boolean weighted = state.getWeighted();
        int ngrid = grids.length;

        /* Set up an object that can write the data of this grid using
         * an appropriate datatype. */
        double min = + Double.MAX_VALUE;
        double max = - Double.MAX_VALUE;
        for ( int i = 0; i < ngrid; i++ ) {
            max = Math.max( max, grids[ i ].getMaxSum() );
            min = Math.min( min, grids[ i ].getMinSum() );
        }
        int bitpix;
        abstract class NumWriter {
            abstract void writeNum( double value ) throws IOException;
            abstract int size();
        };
        NumWriter numWriter;
        if ( weighted ) {
            bitpix = BasicHDU.BITPIX_DOUBLE;
            numWriter = new NumWriter() {
                void writeNum( double value ) throws IOException {
                    out.writeDouble( value );
                }
                int size() {
                    return 8;
                }
            };
        }
        else if ( max < Math.pow( 2, 7 ) ) {
            bitpix = BasicHDU.BITPIX_BYTE;
            numWriter = new NumWriter() {
                void writeNum( double value ) throws IOException {
                    out.writeByte( (int) value );
                }
                int size() {
                    return 1;
                }
            };
        }
        else if ( max < Math.pow( 2, 15 ) ) {
            bitpix = BasicHDU.BITPIX_SHORT;
            numWriter = new NumWriter() {
                void writeNum( double value ) throws IOException {
                    out.writeShort( (int) value );
                }
                int size() {
                    return 2;
                }
            };
        }
        else {
            bitpix = BasicHDU.BITPIX_INT;
            numWriter = new NumWriter() {
                void writeNum( double value ) throws IOException {
                    out.writeInt( (int) value );
                }
                int size() {
                    return 4;
                }
            };
        }

        /* Assemble FITS header.  The output is a 2d array if there is a
         * single channel displayed, or a 3d array if there is more than
         * one channel. */
        int nx = grids[ 0 ].getSizeX();
        int ny = grids[ 0 ].getSizeY();
        for ( int i = 0; i < ngrid; i++ ) {
            assert grids[ i ].getSizeX() == nx;
            assert grids[ i ].getSizeY() == ny;
        }
        int psize = state.getPixelSize();
        ValueInfo[] axes = state.getAxes();

        /* Label this as Cartesian projected RA/DEC axes if it looks like
         * that's what they are. */
        String name1 = looksLike( axes[ 0 ], Tables.RA_INFO )
                     ? "RA---CAR" : axes[ 0 ].getName();
        String name2 = looksLike( axes[ 1 ], Tables.DEC_INFO )
                     ? "DEC--CAR" : axes[ 1 ].getName();

        boolean log1 = state.getLogFlags()[ 0 ];
        boolean log2 = state.getLogFlags()[ 1 ];
        if ( state.getLogFlags()[ 0 ] ) {
            name1 = "log(" + name1 + ")";
        }
        if ( state.getLogFlags()[ 1 ] ) {
            name2 = "log(" + name2 + ")";
        }
        PlotSurface surface = plot_.getSurface();
        Rectangle bbox = surface.getClip().getBounds();
        int x0 = bbox.x;
        int y0 = bbox.y;
        double[] p0 = surface.graphicsToData( x0, y0, false );
        double[] p1 = surface.graphicsToData( x0 + psize, y0 + psize, false );
        Header hdr = new Header();
        hdr.addValue( "SIMPLE", true, "" );
        hdr.addValue( "BITPIX", bitpix, "Data type" );
        hdr.addValue( "NAXIS", ngrid == 1 ? 2 : 3, "Number of axes" );
        hdr.addValue( "NAXIS1", nx, "X dimension" );
        hdr.addValue( "NAXIS2", ny, "Y dimension" );
        if ( ngrid > 1 ) {
            hdr.addValue( "NAXIS3", ngrid, "Number of channels" );
        }
        hdr.addValue( "DATE", Times.mjdToIso( Times.unixMillisToMjd( 
                                           System.currentTimeMillis() ) ),
                      "HDU creation date" );

        hdr.addValue( "CTYPE1", name1, axes[ 0 ].getDescription() );
        hdr.addValue( "CTYPE2", name2, axes[ 1 ].getDescription() );
        if ( ngrid > 1 ) {
            /* Note "RGB" is a special value recognised by Aladin for CTYPE3. */
            hdr.addValue( "CTYPE3", "RGB",
                          "Separate histograms stored in different planes" );
        }
        if ( ! weighted ) {
            hdr.addValue( "BUNIT", "COUNTS",
                          "Number of points per pixel (bin)" );
        }
        if ( max >= min ) {
            hdr.addValue( "DATAMIN", min, "Minimum value" );
            hdr.addValue( "DATAMAX", max, "Maximum value" );
        }

        /* For -CAR projections, it's essential that the CRVALn values are
         * at zero, and the CRPIXn ones are set to whatever makes the
         * mapping right. */
        hdr.addValue( "CRVAL1", 0.0, "Reference pixel X position" );
        hdr.addValue( "CRVAL2", 0.0, "Reference pixel Y position" );

        Point origin =
            surface.dataToGraphics( log1 ? 1.0 : 0.0, log2 ? 1.0 : 0.0, false );
        if ( origin != null ) {
            hdr.addValue( "CRPIX1",
                          ( origin.x - x0 ) / (double) psize,
                          "Reference pixel X index" );
            hdr.addValue( "CRPIX2",
                          ( y0 + bbox.height - origin.y ) / (double) psize,
                          "Reference pixel Y index" );

            if ( ngrid > 1 ) {
                hdr.addValue( "CRVAL3", 0.0,
                              "Reference pixel plane index position" );
                hdr.addValue( "CRPIX3", 0.0, "Reference pixel plane index" );
            }

            hdr.addValue( "CDELT1", log1 ? Maths.log10( p1[ 0 ] / p0[ 0 ] )
                                         : ( p1[ 0 ] - p0[ 0 ] ),
                                    "X extent of reference pixel" );
            hdr.addValue( "CDELT2", log2 ? Maths.log10( p0[ 1 ] / p1[ 1 ] )
                                         : ( p0[ 1 ] - p1[ 1 ] ),
                                    "Y extent of reference pixel" );
            if ( ngrid > 1 ) {
                hdr.addValue( "CDELT3", 1.0,
                              "Plane index extent of reference pixel" );
            }
        }
        hdr.addValue( "ORIGIN", "TOPCAT " + TopcatUtils.getVersion() + 
                      " (" + getClass().getName() + ")", null );

        /* Write the FITS header. */
        FitsConstants.writeHeader( out, hdr );

        /* Write the data. */
        for ( int i = 0; i < ngrid; i++ ) {
            double[] data = grids[ i ].getSums();
            for ( int iy = 0; iy < ny; iy++ ) {
                int yoff = ( ny - 1 - iy ) * nx;
                for ( int ix = 0; ix < nx; ix++ ) {
                    numWriter.writeNum( data[ yoff + ix ] );
                }
            }
        }

        /* Write padding to an integral number of FITS block sizes. */
        int nbyte = nx * ny * ngrid * numWriter.size();
        int over = nbyte % FitsConstants.FITS_BLOCK;
        if ( over > 0 ) {
            out.write( new byte[ FitsConstants.FITS_BLOCK - over ] );
        }

        /* Flush. */
        out.flush();
    }

    /**
     * Determines whether a given column appears to match a target one.
     * The algorithm is a bit scrappy.
     *
     * @param  test   column to assess
     * @param  target  column info that it's being matched against
     * @return  true  if <code>test</code> appears to be the kind of 
     *          quantity described by <code>target</code>
     */
    private boolean looksLike( ValueInfo test, ValueInfo target ) {
        try {
            String selectedName = getPointSelectors()
                                 .getMainSelector()
                                 .getTable()
                                 .getColumnSelectorModel( target )
                                 .getColumnData()
                                 .getColumnInfo()
                                 .getName();   // sorry.
            if ( selectedName.equals( test.getName() ) ) {
                return true;
            }
        }
        catch ( NullPointerException e ) {
        }
        if ( target.getName().equalsIgnoreCase( test.getName() ) ) {
            return true;
        }
        return false;
    }

    /**
     * Style used by density window.  Most of this class is defined
     * by the abstract DensityStyle class, but we have to fill in one
     * method (isRGB) here since behaviour is dependent on the current
     * state of this window.
     */
    private class DStyle extends DensityStyle {
        DStyle( DensityStyle.Channel channel ) {
            super( channel );
        }
        protected boolean isRGB() {
            return rgbModel_.isSelected();
        }
        public Shader getShader() {
            return (Shader) shaderModel_.getSelectedItem();
        }
    }

    /**
     * Action for incrementing the grid pixel size.
     */
    private class PixelSizeAction extends BasicAction {
        final int MAX_SIZE = 20;
        final int MIN_SIZE = 1;
        final int inc_;

        /**
         * Constructs a new PixelSizeAction.
         *
         * @param  name  action name
         * @param  icon  action icon
         * @param  desc  short description (tool tip)
         * @param  inc   amount to increment the pixsize when the action is
         *               invoked
         */
        PixelSizeAction( String name, Icon icon, String desc, int inc ) {
            super( name, icon, desc );
            inc_ = inc;
        }

        public void actionPerformed( ActionEvent evt ) {
            pixelSize_ = Math.min( Math.max( pixelSize_ + inc_, MIN_SIZE ),
                                             MAX_SIZE );
            configureEnabledness();
            replot();
        }

        /**
         * Configures this action according to whether it would have any
         * effect or not.
         */
        void configureEnabledness() {
            setEnabled( pixelSize_ + inc_ >= MIN_SIZE &&
                        pixelSize_ + inc_ <= MAX_SIZE );
        }
    }
}
