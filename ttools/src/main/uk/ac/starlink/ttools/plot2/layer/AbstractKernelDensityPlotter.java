package uk.ac.starlink.ttools.plot2.layer;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.Icon;
import uk.ac.starlink.ttools.plot.Range;
import uk.ac.starlink.ttools.plot.Style;
import uk.ac.starlink.ttools.plot2.Axis;
import uk.ac.starlink.ttools.plot2.Equality;
import uk.ac.starlink.ttools.plot2.LayerOpt;
import uk.ac.starlink.ttools.plot2.PlotUtil;
import uk.ac.starlink.ttools.plot2.ReportKey;
import uk.ac.starlink.ttools.plot2.ReportMap;
import uk.ac.starlink.ttools.plot2.ReportMeta;
import uk.ac.starlink.ttools.plot2.config.ConfigException;
import uk.ac.starlink.ttools.plot2.config.ConfigKey;
import uk.ac.starlink.ttools.plot2.config.ConfigMap;
import uk.ac.starlink.ttools.plot2.config.StyleKeys;
import uk.ac.starlink.ttools.plot2.data.DataSpec;
import uk.ac.starlink.ttools.plot2.data.DataStore;
import uk.ac.starlink.ttools.plot2.data.FloatingCoord;
import uk.ac.starlink.ttools.plot2.geom.PlanarSurface;

/**
 * Abstract superclass for a plotter that plots something that looks like
 * a kernel density estimate.  In fact, for reasons of efficiency and
 * implementation, it's a histogram with pixel-sized bins, but it
 * looks pretty much the same.
 *
 * <p>Concrete subclasses have to arrange for the details of exactly
 * how kernels are instantiated from a given kernel shape.
 *
 * @author   Mark Taylor
 * @since    17 Feb 2015
 */
public abstract class AbstractKernelDensityPlotter
        extends Pixel1dPlotter<AbstractKernelDensityPlotter.KDenseStyle> {

    /** Report key for plotted bin height in data coordinates. */
    public static final ReportKey<double[]> BINS_KEY =
        ReportKey.createUnprintableKey( new ReportMeta( "bins", "Bins" ),
                                        double[].class );

    /** Config key for line thickness (only effective if fill==false). */
    public static final ConfigKey<Integer> THICK_KEY =
        StyleKeys.createThicknessKey( 2 );

    /** Config key for normalisation. */
    public static final ConfigKey<Normalisation> NORMALISE_KEY =
        StyleKeys.NORMALISE;

    private static final int GUESS_PLOT_WIDTH = 300;

    /**
     * Constructor.
     *
     * @param   xCoord  X axis coordinate
     * @param   hasWeight   true to permit histogram weighting
     * @param   name  plotter name
     * @param   icon  plotter icon
     */
    protected AbstractKernelDensityPlotter( FloatingCoord xCoord,
                                            boolean hasWeight,
                                            String name, Icon icon ) {
        super( xCoord, hasWeight, name, icon );
    }

    /**
     * Returns a list of keys that specify how the smoothing kernel
     * will be configured.
     *
     * @return   list of implementation-specific kernel config keys
     */
    protected abstract ConfigKey[] getKernelConfigKeys();

    /**
     * Constructs an object for plot-time kernel construction based on
     * a particular config map.
     *
     * @param   config   config map with kernel config keys in it
     * @see   #getKernelConfigKeys
     */
    protected abstract KernelFigure createKernelFigure( ConfigMap config )
            throws ConfigException;

    public ConfigKey[] getStyleKeys() {
        List<ConfigKey> list = new ArrayList<ConfigKey>();
        list.add( StyleKeys.COLOR );
        list.add( StyleKeys.TRANSPARENCY );
        list.addAll( Arrays.asList( getKernelConfigKeys() ) );
        list.add( KERNEL_KEY );
        list.add( StyleKeys.CUMULATIVE );
        list.add( NORMALISE_KEY );
        list.add( StyleKeys.FILL );
        list.add( THICK_KEY );
        return list.toArray( new ConfigKey[ 0 ] );
    }

    public KDenseStyle createStyle( ConfigMap config ) throws ConfigException {
        Color color = StyleKeys.getAlphaColor( config, StyleKeys.COLOR,
                                               StyleKeys.TRANSPARENCY );
        Kernel1dShape kernelShape = config.get( KERNEL_KEY );
        boolean isCumulative = config.get( StyleKeys.CUMULATIVE );
        Normalisation norm = config.get( NORMALISE_KEY );
        FillMode fill = config.get( StyleKeys.FILL );
        Combiner combiner = config.get( COMBINER_KEY );
        KernelFigure kernelFigure = createKernelFigure( config );
        Stroke stroke = fill.hasLine()
                      ? new BasicStroke( config.get( THICK_KEY ),
                                         BasicStroke.CAP_ROUND,
                                         BasicStroke.JOIN_ROUND )
                      : null;
        return new KDenseStyle( color, fill, stroke, kernelShape, kernelFigure,
                                combiner, isCumulative, norm );
    }

    protected LayerOpt getLayerOpt( KDenseStyle style ) {
        Color color = style.color_;
        boolean isOpaque = color.getAlpha() == 255 && style.fill_.isOpaque();
        return new LayerOpt( color, isOpaque );
    }

    protected int getPixelPadding( KDenseStyle style, PlanarSurface surf ) {
        Kernel1d kernel =
            style.createKernel( surf.getAxes()[ 0 ], surf.getLogFlags()[ 0 ] );
        return getEffectiveExtent( kernel );
    }

    protected Combiner getCombiner( KDenseStyle style ) {
        return style.combiner_;
    }

    protected void paintBins( PlanarSurface surface, BinArray binArray,
                              KDenseStyle style, Graphics2D g ) {

        /* Store graphics context state. */
        Color color0 = g.getColor();
        float[] rgba = style.color_.getComponents( null );
        float cr = rgba[ 0 ];
        float cg = rgba[ 1 ];
        float cb = rgba[ 2 ];
        float calpha = rgba[ 3 ];

        /* Get the data values for each pixel position. */
        Axis xAxis = surface.getAxes()[ 0 ];
        boolean xLog = surface.getLogFlags()[ 0 ];
        Kernel1d kernel = style.createKernel( xAxis, xLog );
        double[] bins = getDataBins( binArray, xAxis, kernel, style );

        /* Work out the Y axis base of the bars in graphics coordinates. */
        Axis yAxis = surface.getAxes()[ 1 ];
        boolean yLog = surface.getLogFlags()[ 1 ];
        boolean yFlip = surface.getFlipFlags()[ 1 ];
        int gy0;
        if ( yLog ) {
            double[] dyLimits = surface.getDataLimits()[ 1 ];
            double dy0 = yAxis.dataToGraphics( dyLimits[ 0 ] );
            gy0 = (int) ( yFlip ? dy0 - 2 : dy0 + 2 );
        }
        else {
            gy0 = (int) yAxis.dataToGraphics( 0 );
        }
        Rectangle clip = surface.getPlotBounds();
        int yClipMin = clip.y - 64;
        int yClipMax = clip.y + clip.height + 64;
        gy0 = (int) clip( gy0, yClipMin, yClipMax );

        /* Work out the range of bin indices that need to be painted. */
        int ixlo = binArray.getBinIndex( clip.x );
        int ixhi = binArray.getBinIndex( clip.x + clip.width );

        /* Assemble a list of the (x,y) graphics coordinates of the
         * top left hand corner for each bar in the plot region. */
        int np = ixhi - ixlo;
        int[] xs = new int[ np ];
        int[] ys = new int[ np ];
        for ( int ip = 0; ip < np; ip++ ) {
            int ix = ixlo + ip;
            double dy = yAxis.dataToGraphics( bins[ ix ] );
            xs[ ip ] = binArray.getGraphicsCoord( ix );
            ys[ ip ] = PlotUtil.isFinite( dy )
                     ? clip( dy, yClipMin, yClipMax )
                     : gy0;
        }

        /* Determine whether to accentuate or play down the jaggedness
         * of the pixel quantisation of the convolved function. */
        boolean squareLines = kernel.isSquare() || kernel.getExtent() <= 1;

        /* Either plot a rectangle (1-pixel wide bar) for each count. */
        float fillAlpha = style.fill_.getFillAlpha();
        if ( fillAlpha > 0 ) {
            final int nVertex;
            final int[] pxs;
            final int[] pys;
            if ( squareLines ) {
                nVertex = np * 2 + 2;
                pxs = new int[ nVertex ];
                pys = new int[ nVertex ];
                for ( int ip = 0; ip < np; ip++ ) {
                    pxs[ ip * 2 + 1 ] = xs[ ip ];
                    pys[ ip * 2 + 1 ] = ys[ ip ];
                    pxs[ ip * 2 + 2 ] = xs[ ip ] + 1;
                    pys[ ip * 2 + 2 ] = ys[ ip ];
                }
            }
            else {
                nVertex = np + 3;
                pxs = new int[ nVertex ];
                pys = new int[ nVertex ];
                System.arraycopy( xs, 0, pxs, 1, np );
                System.arraycopy( ys, 0, pys, 1, np );
                pxs[ nVertex - 2 ] = pxs[ nVertex - 3 ] + 1;
                pys[ nVertex - 2 ] = pys[ nVertex - 3 ];
            }
            pxs[ 0 ] = xs[ 0 ];
            pys[ 0 ] = gy0;
            pxs[ nVertex - 1 ] = xs[ np - 1 ] + 1;
            pys[ nVertex - 1 ] = gy0;
            g.setColor( new Color( cr, cg, cb, calpha * fillAlpha ) );
            g.fillPolygon( pxs, pys, nVertex );
        }

        /* Or plot a wiggly line along the top of the bars. */
        float lineAlpha = style.fill_.getLineAlpha();
        if ( lineAlpha > 0 ) {
            final int nVertex;
            final int[] pxs;
            final int[] pys;
            if ( squareLines ) {
                nVertex = np * 2;
                pxs = new int[ nVertex ];
                pys = new int[ nVertex ];
                for ( int ip = 0; ip < np; ip++ ) {
                    pxs[ ip * 2 ] = xs[ ip ];
                    pys[ ip * 2 ] = ys[ ip ];
                    pxs[ ip * 2 + 1 ] = xs[ ip ] + 1;
                    pys[ ip * 2 + 1 ] = ys[ ip ];
                }
            }
            else {
                nVertex = np;
                pxs = xs;
                pys = ys;
            }
            g.setColor( new Color( cr, cg, cb, calpha * lineAlpha ) );
            Stroke stroke0 = g.getStroke();
            g.setStroke( style.stroke_ );
            g.drawPolyline( pxs, pys, nVertex );
            g.setStroke( stroke0 );
        }

        /* Restore graphics context. */
        g.setColor( color0 );
    }

    protected void extendPixel1dCoordinateRanges( Range[] ranges,
                                                  boolean[] logFlags,
                                                  KDenseStyle style,
                                                  DataSpec dataSpec,
                                                  DataStore dataStore ) {

        /* Calculate the height of the bars for auto-ranging purposes. */
        Range xRange = ranges[ 0 ];
        Range yRange = ranges[ 1 ];
        boolean xlog = logFlags[ 0 ];
        boolean ylog = logFlags[ 1 ];

        /* Assume y=0 is always of interest for a histogram. */
        yRange.submit( ylog ? 1 : 0 );

        /* To calculate the bin heights, we have to provide an Axis
         * instance.  We know the data limits of this from previous
         * ranging, but unfortunately there is no information
         * available at this stage about the width of the plot
         * in pixels.  The maximum bar height is
         * dependent on this, but to a first approximation it
         * shouldn't be too sensitive, so we guess a sensible
         * pixel extent, and hope for the best.  Use a value on
         * the large side for pixel extent, since this will err
         * on the side of a range that is too high (leading to
         * unused space at the top rather than clipping the plot). */
        double[] dxlimits = xRange.getFiniteBounds( xlog );
        double dxlo = dxlimits[ 0 ];
        double dxhi = dxlimits[ 1 ];
        int gxlo = 0;
        int gxhi = GUESS_PLOT_WIDTH;
        boolean xflip = false;
        Axis xAxis = Axis.createAxis( gxlo, gxhi, dxlo, dxhi, xlog, xflip );
        Kernel1d kernel = style.createKernel( xAxis, xlog );
        int xpad = getEffectiveExtent( kernel );
        BinArray binArray = readBins( xAxis, xpad, style.combiner_,
                                      dataSpec, dataStore );
        double[] bins = getDataBins( binArray, xAxis, kernel, style );
        int ixlo = binArray.getBinIndex( gxlo );
        int ixhi = binArray.getBinIndex( gxhi );
        for ( int ix = ixlo; ix < ixhi; ix++ ) {
            yRange.submit( bins[ ix ] );
        }
    }

    protected ReportMap getPixel1dReport( Pixel1dPlan plan,
                                          KDenseStyle style,
                                          boolean xLog ) {
        BinArray binArray = plan.binArray_;
        Axis xAxis = plan.xAxis_;
        Kernel1d kernel = style.createKernel( xAxis, xLog );
        double[] dataBins = getDataBins( binArray, xAxis, kernel, style );
        double[] dlimits = xAxis.getDataLimits();
        double dlo = dlimits[ 0 ];
        double dhi = dlimits[ 1 ];
        int glo = (int) Math.round( xAxis.dataToGraphics( dlo ) );
        int ghi = (int) Math.round( xAxis.dataToGraphics( dhi ) );
        if ( glo > ghi ) {
            int gt = glo;
            glo = ghi;
            ghi = gt;
        }
        int ixlo = binArray.getBinIndex( glo );
        int nx = ghi - glo;
        double[] clipBins = new double[ nx ];
        System.arraycopy( dataBins, ixlo, clipBins, 0, nx );
        ReportMap report = new ReportMap();
        report.put( BINS_KEY, clipBins );
        ReportMap kReport = style.kernelFigure_.getReportMap( xLog, dlo, dhi );
        if ( kReport != null ) {
            report.putAll( kReport );
        }
        return report;
    }

    /**
     * Clips a value to a given range.
     *
     * @param  p  input value
     * @param  lo  minimum acceptable value
     * @param  hi  maximum acceptable value
     * @return   input value clipped to given limits
     */
    private static int clip( double p, int lo, int hi ) {
        if ( Double.isNaN( p ) ) {
            return lo;
        }
        else if ( p < lo ) {
            return lo;
        }
        else if ( p > hi ) {
            return hi;
        }
        else {
            return (int) Math.round( p );
        }
    }

    /**
     * Reads the data bin values using a given style.
     *
     * @param   binArray  basic results
     * @param   xAxis   axis over which counts are accumulated
     * @param   kernel   smoothing kernel
     * @param   style   style
     * @return  output data bin values
     */
    private static double[] getDataBins( BinArray binArray, Axis xAxis,
                                         Kernel1d kernel, KDenseStyle style ) {
        return getDataBins( binArray, xAxis, kernel, style.norm_,
                            style.combiner_.getType(), style.isCumulative() );
    }

    /**
     * Style subclass for kernel density plots.
     */
    public static class KDenseStyle implements Style {
        private final Color color_;
        private final FillMode fill_;
        private final Stroke stroke_;
        private final Kernel1dShape kernelShape_;
        private final KernelFigure kernelFigure_;
        private final Combiner combiner_;
        private final boolean isCumulative_;
        private final Normalisation norm_;
        private static final int[] ICON_DATA = { 4, 6, 8, 9, 9, 7, 5, 3, };

        /**
         * Constructor.
         *
         * @param  color  plot colour
         * @param  fill   fill mode
         * @param  stroke  line stroke, null for filled area
         * @param  kernelShape  smoothing kernel shape
         * @param  kernelFigure  kernel configuration
         * @param  combiner   bin aggregation mode
         * @param  isCumulative  are bins painted cumulatively
         * @param  norm   normalisation mode
         */
        public KDenseStyle( Color color, FillMode fill, Stroke stroke,
                            Kernel1dShape kernelShape,
                            KernelFigure kernelFigure,
                            Combiner combiner,
                            boolean isCumulative, Normalisation norm ) {
            color_ = color;
            fill_ = fill;
            stroke_ = stroke;
            kernelShape_ = kernelShape;
            kernelFigure_ = kernelFigure;
            combiner_ = combiner;
            isCumulative_ = isCumulative;
            norm_ = norm;
        }

        /**
         * Returns cumulative flag.
         *
         * @return  true iff counts are cumulative
         */
        public boolean isCumulative() {
            return isCumulative_;
        }

        /**
         * Returns normalisation mode.
         *
         * @return  normalisation mode for count axis
         */
        public Normalisation getNormalisation() {
            return norm_;
        }

        public Icon getLegendIcon() {
            return fill_.createIcon( ICON_DATA, color_, stroke_, 2 );
        }

        /**
         * Constructs a smoothing kernel suitable for this style.
         *
         * @param   xAxis  axis on which samples occur
         * @param   xLog   true for logarithmic x axis, false for linear
         * @return  kernel
         */
        public Kernel1d createKernel( Axis xAxis, boolean xLog ) {
            return kernelFigure_.createKernel( kernelShape_, xAxis, xLog );
        }

        @Override
        public int hashCode() {
            int code = 33421;
            code = 23 * code + color_.hashCode();
            code = 23 * code + fill_.hashCode();
            code = 23 * code + PlotUtil.hashCode( stroke_ );
            code = 23 * code + kernelShape_.hashCode();
            code = 23 * code + kernelFigure_.hashCode();
            code = 23 * code + combiner_.hashCode();
            code = 23 * code + ( isCumulative_ ? 11 : 13 );
            code = 23 * code + PlotUtil.hashCode( norm_ );
            return code;
        }

        @Override
        public boolean equals( Object o ) {
            if ( o instanceof KDenseStyle ) {
                KDenseStyle other = (KDenseStyle) o;
                return this.color_.equals( other.color_ )
                    && this.fill_.equals( other.fill_ )
                    && PlotUtil.equals( this.stroke_, other.stroke_ )
                    && this.kernelShape_.equals( other.kernelShape_ )
                    && this.kernelFigure_.equals( other.kernelFigure_ )
                    && this.combiner_.equals( other.combiner_ )
                    && this.isCumulative_ == other.isCumulative_
                    && PlotUtil.equals( this.norm_, other.norm_ );
            }
            else {
                return false;
            }
        }
    }

    /**
     * Encapsulates the details of smoothing kernel construction.
     */
    @Equality
    public interface KernelFigure {

        /**
         * Creates a kernel1d smoothing function for use on a given axis.
         *
         * @param  shape  kernel shape
         * @param   xAxis  axis on which samples occur
         * @param   xLog   true for logarithmic x axis, false for linear
         * @return  kernel
         */
        Kernel1d createKernel( Kernel1dShape shape, Axis xAxis, boolean xLog );

        /**
         * Returns report items specific to the way this kernel has operated.
         *
         * @param   xLog   true for logarithmic x axis, false for linear
         * @param   dlo   lower data bound of axis
         * @param   dhi   upper data bound of axis
         * @return   report map, may be null
         */
        public ReportMap getReportMap( boolean xLog, double dlo, double dhi );
    }
}
