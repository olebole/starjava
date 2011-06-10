package uk.ac.starlink.ttools.taplint;

import java.io.IOException;
import uk.ac.starlink.table.StarTable;
import uk.ac.starlink.vo.TapQuery;

/**
 * Executes TAP queries for the validator.
 *
 * @author   Mark Taylor
 * @since    9 Jun 2011
 */
public abstract class TapRunner {

    private final String description_;
    private int nQuery_;
    private int nResult_;

    /**
     * Constructor.
     *
     * @param  description   short description of this object's type
     */
    protected TapRunner( String description ) {
        description_ = description;
    }
  
    /**
     * Returns a short description.
     *
     * @return  descriptive label
     */
    public String getDescription() {
        return description_;
    }

    /**
     * Executes a TAP query and returns the result table, or null if the
     * query failed for some reason.  Errors are reported through the reporter
     * as appropriate.
     *
     * @param  reporter  validation message destination
     * @param  tq  TAP query specification
     * @return  result table, or null if there was an error
     */
    public StarTable getResultTable( Reporter reporter, TapQuery tq ) {
        reporter.report( Reporter.Type.INFO, "QTXT",
                         "Submitting query: " + tq.getAdql() );
        try {
            nQuery_++;
            StarTable table = executeQuery( reporter, tq );
            nResult_++;
            return table;
        }
        catch ( IOException e ) {
            reporter.report( Reporter.Type.ERROR, "QERR",
                             "TAP query failed: " + tq.getAdql(), e );
            return null;
        }
    }

    /**
     * Executes a TAP query, performing reporting as appropriate.
     * The result may be null, but will normally be either a table or
     * an IOException will result.
     *
     * @param  reporter  validation message destination
     * @param  query  query to execute
     * @return  result table
     */
    protected abstract StarTable executeQuery( Reporter reporter,
                                               TapQuery query )
        throws IOException;

    /**
     * Reports a summary of the queries executed by this object.
     *
     * @param  reporter  validation message destination
     */
    public void reportSummary( Reporter reporter ) {
        String msg = new StringBuffer()
           .append( "Successful/submitted TAP queries: " )
           .append( nResult_ )
           .append( "/" )
           .append( nQuery_ )
           .toString();
        reporter.report( Reporter.Type.SUMMARY, "QNUM", msg );
    }
}
