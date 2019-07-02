package uk.ac.starlink.ttools.join;

import java.io.IOException;
import java.util.logging.Logger;
import uk.ac.starlink.table.StarTable;
import uk.ac.starlink.table.Tables;
import uk.ac.starlink.table.ValueInfo;
import uk.ac.starlink.table.join.LinkSet;
import uk.ac.starlink.table.join.Match1Type;
import uk.ac.starlink.table.join.MatchEngine;
import uk.ac.starlink.table.join.ProgressIndicator;
import uk.ac.starlink.table.join.RowMatcher;
import uk.ac.starlink.task.ExecutionException;
import uk.ac.starlink.task.TaskException;
import uk.ac.starlink.ttools.jel.JELTable;
import uk.ac.starlink.ttools.task.SingleTableMapping;

/**
 * SingleTableMapping whose result is generated by performing an
 * internal crossmatch on the input table.
 *
 * @author   Mark Taylor
 * @since    16 Nov 2007
 */
public class Match1Mapping implements SingleTableMapping {

    private final MatchEngine matchEngine_;
    private final Match1Type type1_;
    private final String[] tupleExprs_;
    private final ProgressIndicator progger_;
    private final static Logger logger =
        Logger.getLogger( "uk.ac.starlink.ttools.join" );

    /**
     * Constructs a Match1Mapping by giving instructions about how the
     * internal match result table will be produced from the input table.
     *
     * @param   matchEngine   match engine
     * @param   type1    type of internal match result table
     * @param   tupleExprs  array of JEL expressions to execute in the context
     *                      of the input table, one for each element of
     *                      the matchEngine's tuple
     * @param   progger   progress indicator
     */
    public Match1Mapping( MatchEngine matchEngine, Match1Type type1,
                          String[] tupleExprs, ProgressIndicator progger ) {
        matchEngine_ = matchEngine;
        type1_ = type1;
        tupleExprs_ = tupleExprs.clone();
        progger_ = progger;
    }

    /**
     * Converts the input table to a table containing internal match results.
     *
     * @param  inTable  input table
     * @return   match result table
     */
    public StarTable map( StarTable inTable )
            throws TaskException, IOException {

        /* Attempt to create the table with a column for each tuple element.
         * This is a dry run, intended to catch any exceptions before the
         * possibly expensive work of randomising the input table 
         * is performed. */
        ValueInfo[] tupleInfos = matchEngine_.getTupleInfos();
        JELTable.createJELTable( inTable, tupleInfos, tupleExprs_ );

        /* Now randomise the table, currently required for the rest of the
         * matching, and create the tuple-columned table for real. */
        inTable = Tables.randomTable( inTable );
        StarTable subTable =
            JELTable.createJELTable( inTable, tupleInfos, tupleExprs_ );

        /* Do the matching. */
        RowMatcher matcher =
            new RowMatcher( matchEngine_, new StarTable[] { subTable } );
        matcher.setIndicator( progger_ );
        LinkSet matches; 
        try {
            matches = matcher.findInternalMatches( false );
        }
        catch ( InterruptedException e ) {
            throw new ExecutionException( "Match was interrupted", e );
        }
        if ( ! matches.sort() ) {
            logger.warning( "Can't sort matches - matched table rows may be "
                          + "in an unhelpful order" );
        }

        /* Check the result is not empty - it's not really worth returning
         * table if it is, since it's probably equivalent to the input. */
        int matchCount = matches.size();
        logger.info( matchCount + " matches found" );
        if ( matchCount == 0 ) {
            throw new ExecutionException( "No matches were found" );
        }

        /* Return a table representing the results. */
        return type1_.createMatchTable( inTable, matches );
    }
}
