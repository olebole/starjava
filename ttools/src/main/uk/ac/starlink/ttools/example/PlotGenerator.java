package uk.ac.starlink.ttools.example;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import javax.swing.Icon;
import uk.ac.starlink.ttools.plot.GraphicExporter;
import uk.ac.starlink.ttools.plot.Picture;
import uk.ac.starlink.ttools.plot.Range;
import uk.ac.starlink.ttools.plot2.AuxScale;
import uk.ac.starlink.ttools.plot2.Decoration;
import uk.ac.starlink.ttools.plot2.LayerOpt;
import uk.ac.starlink.ttools.plot2.Navigator;
import uk.ac.starlink.ttools.plot2.PlotLayer;
import uk.ac.starlink.ttools.plot2.PlotPlacement;
import uk.ac.starlink.ttools.plot2.PlotUtil;
import uk.ac.starlink.ttools.plot2.ShadeAxis;
import uk.ac.starlink.ttools.plot2.Surface;
import uk.ac.starlink.ttools.plot2.SurfaceFactory;
import uk.ac.starlink.ttools.plot2.data.DataStore;
import uk.ac.starlink.ttools.plot2.paper.Compositor;
import uk.ac.starlink.ttools.plot2.paper.PaperType;
import uk.ac.starlink.ttools.plot2.paper.PaperTypeSelector;
import uk.ac.starlink.ttools.plot2.task.PlotDisplay;

/**
 * Convenience class for generating plots.
 * This allows you to set up the basic parameters of a plot, and use
 * the same object to construct either a JComponent for live display,
 * or a static Icon, or to export the graphics to a file in a graphic
 * file format.
 *
 * <p>Although this class gives quite a lot of configurability, there
 * are some options it does not provide, for instance related to the
 * details of autoranging plot limits based on the data.
 * To take full advantage of these, you can go back to the lower-level
 * API taking the implementations here as a starting point.
 *
 * @author   Mark Taylor
 * @since    27 Jun 2014
 */
public class PlotGenerator<P,A> {

    private final PlotLayer[] layers_;
    private final SurfaceFactory<P,A> surfFact_;
    private final P profile_;
    private final A aspect_;
    private final Icon legend_;
    private final float[] legPos_;
    private final ShadeAxis shadeAxis_;
    private final Range shadeFixRange_;
    private final PaperTypeSelector ptSel_;
    private final Compositor compositor_;
    private final DataStore dataStore_;
    private final int xpix_;
    private final int ypix_;
 
    /**
     * Constructor.
     *
     * @param  layers   layers constituting plot content
     * @param  surfFact   surface factory
     * @param  profile   surface profile
     * @param  aspect   initial surface aspect (may get changed by zooming etc)
     * @param  legend   legend icon, or null if none required
     * @param  legPos   2-element array giving x,y fractional legend placement
     *                  position within plot (elements in range 0..1),
     *                  or null for external legend
     * @param  shadeAxis  shader axis, or null if not required
     * @param  shadeFixRange  fixed shader range,
     *                        or null for auto-range where required
     * @param  ptSel    paper type selector
     * @param  compositor  compositor for pixel composition
     * @param  dataStore   data storage object
     * @param  xpix    initial horizontal size in pixels
     *                 (may get changed by window resizing)
     * @param  ypix    initial vertical size in pixels
     *                 (may get changed by window resizing)
     */
    public PlotGenerator( PlotLayer[] layers,
                          SurfaceFactory<P,A> surfFact, P profile, A aspect,
                          Icon legend, float[] legPos,
                          ShadeAxis shadeAxis, Range shadeFixRange,
                          PaperTypeSelector ptSel, Compositor compositor,
                          DataStore dataStore, int xpix, int ypix ) {
        layers_ = layers;
        surfFact_ = surfFact;
        profile_ = profile;
        aspect_ = aspect;
        legend_ = legend;
        legPos_ = legPos;
        shadeAxis_ = shadeAxis;
        shadeFixRange_ = shadeFixRange;
        ptSel_ = ptSel;
        compositor_ = compositor;
        dataStore_ = dataStore;
        xpix_ = xpix;
        ypix_ = ypix;
    }

    /**
     * Returns a JComponent containing a live plot.
     *
     * @param surfaceAuxRange  determines whether aux ranges are recalculated
     *                         when the surface changes
     * @param  navigator  user gesture navigation controller,
     *                    or null for a non-interactive plot
     * @param  caching   if true, plot image will be cached where applicable,
     *                   if false it will be regenerated from the data
     *                   on every repaint
     * @return  plot display component
     */
    public PlotDisplay createPlotDisplay( Navigator<A> navigator,
                                          boolean surfaceAuxRange,
                                          boolean caching ) {
        PlotDisplay display = 
            new PlotDisplay( layers_, surfFact_, profile_, aspect_, legend_,
                             legPos_, shadeAxis_, shadeFixRange_,
                             ptSel_, compositor_, dataStore_,
                             surfaceAuxRange, navigator, caching );
        display.setPreferredSize( new Dimension( xpix_, ypix_ ) );
        return display;
    }

    /**
     * Exports a plot to an output stream in a supported graphics format.
     *
     * @param   exporter  defines a graphics output format
     * @param   out   destination stream;
     *                this method buffers it, but doesn't close it
     */
    public void exportPlot( GraphicExporter exporter, OutputStream out )
            throws IOException {
        boolean forceBitmap = false;
        Picture pic = PlotUtil.toPicture( createIcon( forceBitmap ) );
        OutputStream bufOut = new BufferedOutputStream( out );
        exporter.exportGraphic( pic, bufOut );
        bufOut.flush();
    }

    /**
     * Returns a static icon that can be used to paint the plot.
     * The assumption is that the plot will only be painted once;
     * the image is not cached for repeated painting.
     *
     * @param  forceBitmap  true iff the plot layers should be forced to
     *         a pixel map grid rather than (perhaps) being drawn using
     *         vector graphics; usually not necessary
     * @return  icon to paint plot; it may be painted in a headless context
     */
    public Icon createIcon( boolean forceBitmap ) {
        Rectangle extBounds = new Rectangle( 0, 0, xpix_, ypix_ );
        Rectangle dataBounds =
            PlotPlacement
           .calculateDataBounds( extBounds, surfFact_, profile_, aspect_,
                                 false, legend_, legPos_, shadeAxis_ );
        dataBounds.x += 2;
        dataBounds.y += 2;
        dataBounds.width -= 4;
        dataBounds.height -= 4;
        Surface surf = surfFact_.createSurface( dataBounds, profile_, aspect_ );
        Decoration[] decs =
            PlotPlacement.createPlotDecorations( dataBounds, legend_, legPos_,
                                                 shadeAxis_ );
        PlotPlacement placer = new PlotPlacement( extBounds, surf, decs );
        Map<AuxScale,Range> auxRanges =
            PlotDisplay.getAuxRanges( layers_, surf, shadeFixRange_, shadeAxis_,
                                      dataStore_ );
        LayerOpt[] opts = PaperTypeSelector.getOpts( layers_ );
        PaperType paperType =
              forceBitmap ? ptSel_.getPixelPaperType( opts, compositor_, null )
                          : ptSel_.getVectorPaperType( opts );
        boolean cached = false;
        return PlotDisplay.createIcon( placer, layers_, auxRanges, dataStore_,
                                       paperType, cached );
    }
}