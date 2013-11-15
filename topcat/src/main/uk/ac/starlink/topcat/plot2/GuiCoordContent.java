package uk.ac.starlink.topcat.plot2;

import uk.ac.starlink.table.ColumnData;
import uk.ac.starlink.table.ValueInfo;
import uk.ac.starlink.ttools.plot2.data.Coord;

/**
 * Aggregates user-supplied information about a coordinate value used
 * as input for a plot.
 * The <code>dataLabels</code> and <code>colDatas<code> arrays both
 * correspond to (and have the same array size as) the 
 * {@link uk.ac.starlink.ttools.plot2.data.Coord#getUserInfos userInfos}
 * arrays for the coord.
 *
 * @see   CoordPanel
 */
public class GuiCoordContent {

    private final Coord coord_;
    private final String[] dataLabels_;
    private final ColumnData[] colDatas_;

    /**
     * Constructor.
     *
     * @param   coord   plot coordinate definition
     * @param  dataLabels   array of strings naming quantities
     *                      for the user variables constituting the coord value
     * @param  colDatas  array of column data arrays supplyig values
     *                   for the user variables constituting the coord value
     */
    public GuiCoordContent( Coord coord, String[] dataLabels,
                            ColumnData[] colDatas ) {
        coord_ = coord;
        dataLabels_ = dataLabels;
        colDatas_ = colDatas;
    }

    /**
     * Returns the coordinate definition.
     *
     * @return   coord definition
     */
    public Coord getCoord() {
        return coord_;
    }

    /**
     * Returns the labels describing user input variables.
     *
     * @return   nUserInfo-element array of user variable labels
     */
    public String[] getDataLabels() {
        return dataLabels_;
    }

    /**
     * Returns the column data objects for user input variables.
     *
     * @return   nUserInfo-element array of column data objects
     */
    public ColumnData[] getColDatas() {
        return colDatas_;
    }

    /**
     * Utility method to interrogate a list of GuiCoordContent objects
     * to get a suitable coordinate label (for instance for use as an
     * axis label) for one of the coordinates in a plot.
     * This is not bulletproof because the user coordinate name is not
     * guaranteed unique, but it will probably work as required.
     *
     * @param  userCoordName  user info coordinate name
     * @param  contents  list of GuiCoordContent values associated
     *                   with a plot; null is permitted, and will give
     *                   a null result
     * @return  string that the user will recognise as applying to
     *          <code>userCoordName</code> for plots generated by this control,
     *          or null if no result is found
     * @see uk.ac.starlink.ttools.plot2.data.Coord#getUserInfos
     */
    public static String getCoordLabel( String userCoordName,
                                        GuiCoordContent[] contents ) {

        /* For each coordinate data item, see if one of the user info
         * names associated with it matches what we're looking for.
         * If so, return the user-entered value (column name or expression),
         * perhaps with a unit string appended. */
        if ( contents != null ) {
            for ( int ic = 0; ic < contents.length; ic++ ) {
                GuiCoordContent content = contents[ ic ];
                ValueInfo[] infos = content.getCoord().getUserInfos();
                ColumnData[] coldatas = content.getColDatas();
                for ( int iu = 0; iu < infos.length; iu++ ) {
                    if ( infos[ iu ].getName().equals( userCoordName ) &&
                         coldatas[ iu ] != null ) {
                        ValueInfo dinfo = coldatas[ iu ].getColumnInfo();
                        String name = dinfo.getName();
                        String unit = dinfo.getUnitString();
                        return unit != null && unit.trim().length() > 0
                             ? name + " / " + unit
                             : name;
                    }
                }
            }
        }

        /* Not found; return null. */
        return null;
    }
}
