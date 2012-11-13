package uk.ac.starlink.ttools.cone;

import uk.ac.starlink.task.Environment;
import uk.ac.starlink.task.Parameter;
import uk.ac.starlink.task.TaskException;

/**
 * Defines a service which can perform cone search-like operations.
 *
 * @author   Mark Taylor
 * @since    10 Aug 2007
 */
public interface Coner {

    /**
     * Returns any configuration parameters associated with this object.
     *
     * @return  parameter array
     */
    Parameter[] getParameters();

    /**
     * Returns the name of the coordinate system used by this Coner.
     * Spatial matching is done using Right Ascension and Declination
     * in degrees but the exact coordinate system is up to this object.
     * This method should return a string such as "ICRS" which specifies
     * the ecliptic system in use.  It may return the empty string if
     * no assumption is made.
     *
     * <p>This string is used only for documentation purposes, for instance
     * in prompt strings issued to the user.
     *
     * @return  ecliptic coordinate system name
     */
    String getSkySystem();

    /**
     * Provides this object with a chance to perform custom configuration
     * on certain general cone search parameters.  If no customisation 
     * is required, no action need be taken.  This will be called during 
     * the parameter acquisition phase, before the relevant parameters
     * have been interrogated.  Since the execution environment is
     * given however, it will not be called during automatic document
     * generation.
     *
     * @param  env  execution environment
     * @param  srParam   search radius parameter
     */
    void configureParams( Environment env, Parameter srParam )
            throws TaskException;

    /**
     * Indicates whether the result table generated by the created ConeSearcher
     * object should be subjected to additional filtering to ensure that
     * only rows in the specified search radius are included in the final
     * output.
     *
     * @param   env  execution environment
     * @return  true iff post-query filtering on distance is to be performed
     */
    boolean useDistanceFilter( Environment env )
            throws TaskException;

    /**
     * Returns a searcher object which can perform the actual cone searches
     * as configured by this object's parameters.
     * If the <code>bestOnly</code> flag is set, then only the best match
     * is required.  The implementation may use this as a hint if it helps
     * efficiency, but is not obliged to return single-row tables, since
     * extraneous rows will be filtered out later.  Similarly any rows
     * which do not actually match the given criteria will be filtered out
     * later, so it is not an error to return too many rows.
     *
     * @param   env  execution environment
     * @param   bestOnly  true iff only the best match will be used
     */
    ConeSearcher createSearcher( Environment env, boolean bestOnly )
        throws TaskException;

    /**
     * Returns a coverage footprint suitable for use with the cone search
     * service configured by this object's parameters.
     *
     * @param   env  execution environment
     * @return   coverage footprint, or null
     */
    Footprint getFootprint( Environment env ) throws TaskException;
}
