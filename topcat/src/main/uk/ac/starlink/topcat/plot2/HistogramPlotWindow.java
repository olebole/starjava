package uk.ac.starlink.topcat.plot2;

import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import uk.ac.starlink.table.ColumnData;
import uk.ac.starlink.table.ColumnInfo;
import uk.ac.starlink.table.ColumnStarTable;
import uk.ac.starlink.table.RowSequence;
import uk.ac.starlink.table.StarTable;
import uk.ac.starlink.table.TableSource;
import uk.ac.starlink.topcat.BasicAction;
import uk.ac.starlink.topcat.ResourceIcon;
import uk.ac.starlink.topcat.RowSubset;
import uk.ac.starlink.topcat.TopcatModel;
import uk.ac.starlink.ttools.plot.Range;
import uk.ac.starlink.ttools.plot2.DataGeom;
import uk.ac.starlink.ttools.plot2.GangerFactory;
import uk.ac.starlink.ttools.plot2.PlotLayer;
import uk.ac.starlink.ttools.plot2.PlotType;
import uk.ac.starlink.ttools.plot2.PlotUtil;
import uk.ac.starlink.ttools.plot2.Plotter;
import uk.ac.starlink.ttools.plot2.ReportMap;
import uk.ac.starlink.ttools.plot2.SingleGanger;
import uk.ac.starlink.ttools.plot2.Surface;
import uk.ac.starlink.ttools.plot2.SurfaceFactory;
import uk.ac.starlink.ttools.plot2.config.ConfigKey;
import uk.ac.starlink.ttools.plot2.config.StyleKeys;
import uk.ac.starlink.ttools.plot2.geom.PlaneAspect;
import uk.ac.starlink.ttools.plot2.geom.PlaneDataGeom;
import uk.ac.starlink.ttools.plot2.geom.PlanePlotType;
import uk.ac.starlink.ttools.plot2.geom.PlaneSurface;
import uk.ac.starlink.ttools.plot2.geom.PlaneSurfaceFactory;
import uk.ac.starlink.ttools.plot2.layer.AbstractKernelDensityPlotter;
import uk.ac.starlink.ttools.plot2.layer.BinBag;
import uk.ac.starlink.ttools.plot2.layer.DensogramPlotter;
import uk.ac.starlink.ttools.plot2.layer.FixedKernelDensityPlotter;
import uk.ac.starlink.ttools.plot2.layer.FunctionPlotter;
import uk.ac.starlink.ttools.plot2.layer.HistogramPlotter;
import uk.ac.starlink.ttools.plot2.layer.KnnKernelDensityPlotter;
import uk.ac.starlink.ttools.plot2.layer.Normalisation;
import uk.ac.starlink.ttools.plot2.paper.PaperTypeSelector;
import uk.ac.starlink.ttools.plot2.layer.Stats1Plotter;

/**
 * Layer plot window for histograms.
 * This is a slight variant of PlanePlotWindow, with a restricted set
 * of plotters and modified axis controls.  It's here for convenience
 * and easy start of a histogram - it is also possible to draw histograms
 * in a normal plane plot.  This window also allows export of histogram
 * bin data, which is not available from the plane plot window.
 *
 * @author   Mark Taylor
 * @since    21 Jan 2014
 */
public class HistogramPlotWindow
             extends StackPlotWindow<PlaneSurfaceFactory.Profile,PlaneAspect> {

    private static final PlotType PLOT_TYPE = new HistogramPlotType();
    private static final PlotTypeGui PLOT_GUI = new HistogramPlotTypeGui();
    private static final int BINS_TABLE_INTRO_NCOL = 2;

    /**
     * Constructor.
     *
     * @param  parent  parent component
     */
    public HistogramPlotWindow( Component parent ) {
        super( "Histogram Plot", parent, PLOT_TYPE, PLOT_GUI );

        /* This window currently works with a single plot zone, with index zero.
         * At least, the histogram-specific behaviour applies only
         * to that zone. */
        final int iz0 = 0;

        /* Actions for saving or exporting the binned data as a table. */
        TableSource binSrc = new TableSource() {
            public StarTable getStarTable() {
                return getBinDataTable( iz0 );
            }
        };
        final Action importAct =
            createImportTableAction( "binned data", binSrc, "histogram" );
        final Action saveAct =
            createSaveTableAction( "binned data", binSrc );
        importAct.putValue( Action.SMALL_ICON, ResourceIcon.HISTO_IMPORT );
        saveAct.putValue( Action.SMALL_ICON, ResourceIcon.HISTO_SAVE );
        getPlotPanel().addChangeListener( new ChangeListener() {
            public void stateChanged( ChangeEvent evt ) {
                boolean hasData = hasHistogramLayers( iz0 );
                importAct.setEnabled( hasData );
                saveAct.setEnabled( hasData );
            }
        } );
        getToolBar().add( importAct );
        JMenu exportMenu = getExportMenu();
        exportMenu.addSeparator();
        exportMenu.add( importAct );
        exportMenu.add( saveAct );

        /* Action for selective re-ranging. */
        Action yRescaleAct =
                new BasicAction( "Rescale Y", ResourceIcon.RESIZE_Y,
                                 "Rescale the vertical axis to fit all the data"
                               + " in the visible horizontal range" ) {
            public void actionPerformed( ActionEvent evt ) {
                rescaleY( iz0 );
            }
        };
        insertRescaleAction( yRescaleAct );

        /* Switch off default sketching, since with histograms the
         * sketched result typically looks unlike the final one
         * (fewer samples mean the bars are shorter). */
        getSketchModel().setSelected( false );

        /* Complete the setup. */
        getToolBar().addSeparator();
        addHelp( "HistogramPlotWindow" );
    }

    /**
     * Indicates whether any of the layers currently plotted are displaying
     * histogram data.
     *
     * @param   iz  zone index
     * @return  true iff there are any layers generated by HistogramPlotters
     *          in the given zone
     */
    private boolean hasHistogramLayers( int iz ) {
        PlotPanel plotPanel = getPlotPanel();
        if ( iz < plotPanel.getZoneCount() ) {
            for ( PlotLayer layer : plotPanel.getPlotLayers( iz ) ) {
                if ( layer.getPlotter() instanceof HistogramPlotter ) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns the binned data in the form of a StarTable suitable for
     * saving or otherwise exporting.
     *
     * @param    iz  zone index
     * @return   table representing the current histogram
     */
    private StarTable getBinDataTable( int iz ) {
        PlotPanel plotPanel = getPlotPanel();
        if ( iz >= plotPanel.getZoneCount() ) {
            return null;
        }

        /* Get the bin data corresponding to each histogram layer. */
        PlotLayer[] layers = plotPanel.getPlotLayers( iz );
        ReportMap[] reports = plotPanel.getReports( iz );
        int nl = layers.length;
        assert nl == reports.length;
        Map<PlotLayer,BinBag> binsMap = new LinkedHashMap<PlotLayer,BinBag>();
        for ( int il = 0; il < nl; il++ ) {
            PlotLayer layer = layers[ il ];
            if ( layer.getPlotter() instanceof HistogramPlotter ) {
                ReportMap report = reports[ il ];
                BinBag binBag = report == null
                            ? null
                            : report.get( HistogramPlotter.BINS_KEY );
                assert binBag != null;
                if ( binBag != null ) {
                    binsMap.put( layer, binBag );
                }
            }
        }
        if ( binsMap.size() == 0 ) {
            return null;
        }

        /* Get a list of all the histogram bins with their low/high bounds. */
        Surface surface = plotPanel.getSurface( iz );
        Rectangle bounds = surface.getPlotBounds();
        Point p0 = new Point( bounds.x, bounds.y );
        Point p1 = new Point( bounds.x + bounds.width, bounds.y );
        double x0 = surface.graphicsToData( p0, null )[ 0 ];
        double x1 = surface.graphicsToData( p1, null )[ 0 ];
        double xlo = x0 < x1 ? x0 : x1;
        double xhi = x0 < x1 ? x1 : x0;
        final List<double[]> barList = new ArrayList<double[]>();
        final Map<Double,Integer> rowMap = new HashMap<Double,Integer>();
        int nrow = 0;
        BinBag binBag0 = binsMap.values().iterator().next();
        for ( Iterator<double[]> barIt = binBag0.barIterator( xlo, xhi );
              barIt.hasNext(); ) {
            double[] bar = barIt.next();
            barList.add( bar );
            rowMap.put( new Double( bar[ 0 ] ), new Integer( nrow++ ) );
        }

        /* We will construct a table with one row for each histogram bin. */
        ColumnStarTable table = ColumnStarTable.makeTableWithRows( nrow );

        /* In this window (though it's not necessarily the case for all
         * possible histogram plots), all the data sets share the same set
         * of bins.  The first two columns give the lower and upper bounds
         * of each bin. */
        table.addColumn( new ColumnData( new ColumnInfo( "LOW", Double.class,
                                                         "Bin lower bound" ) ) {
            public Object readValue( long irow ) {
                return barList.get( (int) irow )[ 0 ];
            }
        } );
        table.addColumn( new ColumnData( new ColumnInfo( "HIGH", Double.class,
                                                         "Bin upper bound" ) ) {
            public Object readValue( long irow ) {
                return barList.get( (int) irow )[ 1 ];
            }
        } );
        assert table.getColumnCount() == BINS_TABLE_INTRO_NCOL;

        /* Work out if the layers contain more than one table, and if they
         * contain non-ALL row subsets.  This information is used when
         * assigning sensible (compact but informative) names to the columns. */
        boolean multiSubset = false;
        Set<TopcatModel> tableSet = new HashSet<TopcatModel>();
        for ( PlotLayer layer : binsMap.keySet() ) {
            GuiDataSpec dataSpec = (GuiDataSpec) layer.getDataSpec();
            multiSubset = multiSubset
                       || dataSpec.getRowSubset() != RowSubset.ALL;
            tableSet.add( dataSpec.getTopcatModel() );
        }
        boolean multiTable = tableSet.size() > 1;

        /* Add a new table column for each histogram layer in the plot. */
        for ( PlotLayer layer : binsMap.keySet() ) {
            HistogramPlotter plotter = (HistogramPlotter) layer.getPlotter();
            HistogramPlotter.HistoStyle style =
                (HistogramPlotter.HistoStyle) layer.getStyle();
            GuiDataSpec dataSpec = (GuiDataSpec) layer.getDataSpec();
            boolean isCumulative = style.isCumulative();
            Normalisation norm = style.getNormalisation();
            int icWeight = plotter.getWeightCoordIndex();
            boolean hasWeight =
                icWeight >= 0 && ! dataSpec.isCoordBlank( icWeight );
            String weightName =
                  hasWeight
                ? dataSpec.getCoordDataLabels( icWeight )[ 0 ]
                : null;
            boolean isInt = ! hasWeight && norm == Normalisation.NONE;
            TopcatModel tcModel = dataSpec.getTopcatModel();
            RowSubset rset = dataSpec.getRowSubset();

            /* Think up a name for the column. */
            StringBuffer nbuf = new StringBuffer();
            if ( multiTable ) {
                nbuf.append( "t" )
                    .append( tcModel.getID() )
                    .append( "_" );
            }
            if ( multiSubset ) {
                nbuf.append( rset.getName() )
                    .append( "_" );
            }
            if ( hasWeight ) {
                nbuf.append( "SUM_" )
                    .append( weightName );
            }
            else {
                nbuf.append( "COUNT" );
            }
            String name = nbuf.toString();

            /* Assemble a description for the column. */
            List<String> descripWords = new ArrayList<String>();
            if ( norm != Normalisation.NONE ) {
                descripWords.add( "normalised" );
            }
            if ( isCumulative ) {
                descripWords.add( "cumulative" );
            }
            descripWords.add( "count" );
            StringBuffer dbuf = new StringBuffer();
            for ( String word : descripWords ) {
                dbuf.append( word )
                    .append( ' ' );
            }
            dbuf.setCharAt( 0, Character.toUpperCase( dbuf.charAt( 0 ) ) );
            if ( hasWeight ) {
                dbuf.append( "weighted by " )
                    .append( weightName )
                    .append( ' ' );
            }
            if ( rset != RowSubset.ALL ) {
                dbuf.append( "for row subset " )
                    .append( rset.getName() )
                    .append( ' ' );
            }
            dbuf.append( "in table " )
                .append( tcModel.getLabel() );
            String descrip = dbuf.toString();

            /* Construct the data array for the column. */
            BinBag binBag = binsMap.get( layer );
            final Number[] data = new Number[ nrow ];
            final Class clazz = isInt ? Integer.class : Double.class;
            for ( Iterator<BinBag.Bin> binIt =
                      binBag.binIterator( isCumulative, norm );
                  binIt.hasNext(); ) {
                BinBag.Bin bin = binIt.next();
                Double xmin = new Double( bin.getXMin() );
                if ( rowMap.containsKey( xmin ) ) {
                    int irow = rowMap.get( xmin ).intValue();
                    double y = bin.getY();
                    data[ irow ] = isInt
                                 ? (Number) new Integer( (int) Math.round( y ) )
                                 : (Number) new Double( y );
                }
            }
            Number zero = isInt ? (Number) new Integer( 0 )
                                : (Number) new Double( 0 );
            Number lastVal = zero;
            for ( int irow = 0; irow < nrow; irow++ ) {
                if ( data[ irow ] == null ) {
                    data[ irow ] = isCumulative ? lastVal : zero;
                }
                else {
                    lastVal = data[ irow ];
                }
            }

            /* Add the column to the table. */
            ColumnInfo info = new ColumnInfo( name, clazz, descrip );
            table.addColumn( new ColumnData( info ) {
                public Object readValue( long irow ) {
                    return data[ (int) irow ];
                }
            } );
        }
        assert table.getColumnCount() ==
               BINS_TABLE_INTRO_NCOL + binsMap.keySet().size();

        /* Return the completed table. */
        return table;
    }

    /**
     * Rescales the Y axis to accommodate currently plotted histogram bars
     * while leaving the X axis unchanged.
     *
     * @param  iz  zone index
     */
    private void rescaleY( int iz ) {
        PlotPanel plotPanel = getPlotPanel();
        if ( iz < plotPanel.getZoneCount() ) {
            Range yrange = readVerticalRange( iz );
            PlaneSurface surface =
                (PlaneSurface) plotPanel.getLatestSurface( iz );
            double[] xbounds = surface.getDataLimits()[ 0 ];
            boolean ylogFlag = surface.getLogFlags()[ 1 ];
            PlotUtil.padRange( yrange, ylogFlag );
            double[] ybounds = yrange.getFiniteBounds( ylogFlag );
            PlaneAspect aspect = new PlaneAspect( xbounds, ybounds );
            getAxisController( iz ).setAspect( aspect );
            plotPanel.replot();
        }
    }

    /**
     * Returns a Range object corresponding to the extent on the Y axis
     * of histogram bin data currently plotted.
     *
     * @param   iz   zone index
     * @return  Y range of plotted histogram data, or null
     */
    private Range readVerticalRange( int iz ) {
        PlotPanel plotPanel = getPlotPanel();

        /* Initialise range object with the lower limit for the bottom of
         * the bars. */
        Range yRange = new Range();
        Surface surface = plotPanel.getSurface( iz );
        boolean isLog = surface instanceof PlaneSurface
                     && ((PlaneSurface) surface ).getLogFlags()[ 1 ];
        yRange.submit( isLog ? 1 : 0 );

        /* Get the heights of the entries in the bin data table.
         * This will cover the HistogramPlotter layers. */
        StarTable binsTable = getBinDataTable( iz );
        if ( binsTable != null ) {
            int ncol = binsTable.getColumnCount();
            try {
                RowSequence rseq = binsTable.getRowSequence();
                while ( rseq.next() ) {
                    Object[] row = rseq.getRow();
                    for ( int icol = BINS_TABLE_INTRO_NCOL; icol < ncol;
                          icol++ ) {
                        Object value = row[ icol ];
                        if ( value instanceof Number ) {
                            yRange.submit( ((Number) value).doubleValue() );
                        }
                    }
                }
                rseq.close();
            }
            catch ( IOException e ) {
                // shouldn't happen
            }
        }

        /* Interrogate the KernelDensityPlotter layers separately. */
        PlotLayer[] layers = plotPanel.getPlotLayers( iz );
        ReportMap[] reports = plotPanel.getReports( iz );
        int nl = layers.length;
        for ( int il = 0; il < nl; il++ ) {
            PlotLayer layer = layers[ il ];
            ReportMap report = reports[ il ];
            if ( report != null ) {
                Plotter plotter = layer.getPlotter();
                if ( plotter instanceof AbstractKernelDensityPlotter ) {
                    double[] bins =
                        report.get( AbstractKernelDensityPlotter.BINS_KEY );
                    for ( double bin : bins ) {
                        yRange.submit( bin );
                    }
                }
            }
        }

        /* Return the populated range object. */
        return yRange;
    }

    /**
     * Variant of PlanePlotType for histograms; has a different set of plotters.
     */
    private static class HistogramPlotType implements PlotType {
        private final PaperTypeSelector ptSelector_ =
            PlanePlotType.getInstance().getPaperTypeSelector();
        private final SurfaceFactory surfFact_ = new PlaneSurfaceFactory();
        public PaperTypeSelector getPaperTypeSelector() {
            return ptSelector_;
        }
        public DataGeom[] getPointDataGeoms() {
            return new DataGeom[] { PlaneDataGeom.INSTANCE };
        }
        public SurfaceFactory getSurfaceFactory() {
            return surfFact_;
        }
        public Plotter[] getPlotters() {
            ConfigKey<Normalisation> normKey = StyleKeys.NORMALISE;
            return new Plotter[] {
                new HistogramPlotter( PlaneDataGeom.X_COORD, true, normKey ),
                new FixedKernelDensityPlotter( PlaneDataGeom.X_COORD, true,
                                               normKey ),
                new KnnKernelDensityPlotter( PlaneDataGeom.X_COORD, true,
                                             normKey ),
                new DensogramPlotter( PlaneDataGeom.X_COORD, true ),
                new Stats1Plotter( PlaneDataGeom.X_COORD, true, normKey ),
                FunctionPlotter.PLANE,
            };
        }
    }

    /**
     * Defines GUI features specific to histogram plot.
     */
    private static class HistogramPlotTypeGui
            implements PlotTypeGui<PlaneSurfaceFactory.Profile,PlaneAspect> {
        public AxisController<PlaneSurfaceFactory.Profile,PlaneAspect>
                createAxisController() {
            return new HistogramAxisController();
        }
        public PositionCoordPanel createPositionCoordPanel( int npos ) {
            return SimplePositionCoordPanel
                  .createPanel( PLOT_TYPE.getPointDataGeoms()[ 0 ], npos );
        }
        public GangerFactory getGangerFactory() {
            return SingleGanger.FACTORY;
        }
        public ZoneFactory createZoneFactory() {
            return ZoneFactories.FIXED;
        }
        public boolean hasPositions() {
            return false;
        }
        public String getNavigatorHelpId() {
            return "histogramNavigation";
        }
    }
}
