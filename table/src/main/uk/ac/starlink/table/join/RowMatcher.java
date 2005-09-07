package uk.ac.starlink.table.join;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import uk.ac.starlink.table.Tables;
import uk.ac.starlink.table.RowSequence;
import uk.ac.starlink.table.StarTable;
import uk.ac.starlink.table.ValueInfo;

/**
 * Performs matching on the rows of one or more tables.
 * The specifics of what constitutes a matched row, and some additional
 * intelligence about how to determine this, are supplied by an
 * associated {@link MatchEngine} object, but the generic parts of
 * the matching algorithms are done here. 
 * 
 * @author   Mark Taylor (Starlink)
 */
public class RowMatcher {

    private final MatchEngine engine;
    private final StarTable[] tables;
    private final int nTable;
    private ProgressIndicator indicator = new NullProgressIndicator();
    private long startTime;

    /**
     * Constructs a new matcher with match characteristics defined by
     * a given matching engine.
     *
     * @param  engine  matching engine
     * @param  tables  the array of tables on which matches are to be done
     */
    public RowMatcher( MatchEngine engine, StarTable[] tables ) {
        this.engine = engine;
        this.tables = tables;
        this.nTable = tables.length;
    }

    /**
     * Sets the progress indicator for this matcher.
     * 
     * @param  indicator  new indicator
     */
    public void setIndicator( ProgressIndicator indicator ) {
        this.indicator = indicator;
    }

    /**
     * Returns the current progress indicator for this matcher.
     *
     * @return   indicator
     */
    public ProgressIndicator getIndicator() {
        return indicator;
    }

    /**
     * Constructs a new empty LinkSet for use by this matcher.
     * The current implementation returns one based on a SortedSet,
     * but future implementations may provide the option of LinkSet
     * implementations backed by disk.
     *
     * @return  new LinkSet
     */
    public LinkSet createLinkSet() {
        return new TreeSetLinkSet();
    }

    /**
     * Returns a set of RowLink objects corresponding to a match 
     * between this matcher's two tables performed with its match engine.
     * Each element in the returned list basically corresponds to a matched 
     * pair with one entry from each of the input tables, however using
     * the <tt>req1</tt> and <tt>req2</tt> arguments you can specify whether
     * both input tables need to be represented.
     * Each input table row appears in no more than one RowLink in the
     * returned list.
     *
     * @param  req1  whether an entry from the first table must be present
     *         in each element of the result
     * @param  req2  whether an entry from the second table must be present
     *         in each element of the result
     * @return  link set
     */
    public LinkSet findPairMatches( boolean req1, boolean req2 )
            throws IOException, InterruptedException {
        checkRandom();

        /* Check we have two tables. */
        if ( nTable != 2 ) {
            throw new IllegalStateException( "findPairMatches only makes sense"
                                           + " for 2 tables" );
        }
        startMatch();

        /* Get the possible candidates for inter-table links. */
        LinkSet links = getPossibleInterLinks( 0, 1 );

        /* Get all the possible inter-table pairs. */
        links = findInterPairs( links, 0, 1 );

        /* Make sure that no row is represented more than once
         * (this isn't necessary for the result to make sense, but it's 
         * probably what the caller is expecting). */
        links = eliminateMultipleRowEntries( links );

        /* We now have a set of links corresponding to all the matches
         * with one entry for each of two or more of the input tables.
         * In the case that we want to output some links with unmatched
         * parts, add new singleton row links as necessary. 
         * They are added without scores, since they don't represent 
         * actual matches. */
        LinkSet[] missing = new LinkSet[] {
            ( req1 ? null : missingSingles( links, 0 ) ),
            ( req2 ? null : missingSingles( links, 1 ) ),
        };
        for ( int i = 0; i < 2; i++ ) {
            if ( missing[ i ] != null ) {
                for ( Iterator it = missing[ i ].iterator(); it.hasNext(); ) {
                    links.addLink( (RowLink) it.next() );
                    it.remove();
                }
                missing[ i ] = null;
            }
        }

        /* Return. */
        endMatch();
        return links;
    }

    /**
     * Returns a list of RowLink objects corresponding to a match
     * performed with this matcher's tables using its match engine.
     * Each element in the returned list corresponds to a matched group of 
     * input rows, with no more than one entry from each table.
     * Each input table row appears in no more than one RowLink in
     * the returned list.  Whether each returned RowLink must contain
     * an entry from every input table is determined by the
     * <tt>useAll</tt> argument.
     * Any number of tables can be matched.
     * 
     * @param  useAll  array of booleans indicating for each table whether
     *         all rows are to be used (otherwise just matched)
     * @return list of {@link RowLink}s corresponding to the selected rows
     */
    public LinkSet findGroupMatches( boolean[] useAll )
            throws IOException, InterruptedException {
        checkRandom();

        /* Check we have multiple tables. */
        if ( nTable < 2 ) {
            throw new IllegalStateException( "Find matches only makes sense "
                                           + "for multiple tables" );
        }

        /* Check that there are the right number of options and that they
         * are not null. */
        if ( useAll.length != nTable ) {
            throw new IllegalArgumentException( 
                "Options length " + useAll.length +
                " differs from table count " + nTable );
        }
        startMatch();

        /* Get all the possible pairs. */
        LinkSet pairs = findPairs( getAllPossibleLinks() );

        /* Exclude any pairs which represent links between different rows
         * of the same table. */
        eliminateInternalLinks( pairs );

        /* Join up pairs into larger groupings. */
        LinkSet links = agglomerateLinks( pairs );
        pairs = null;

        /* This could introduce more internal links - get rid of them. */
        eliminateInternalLinks( links );

        /* We now have a set of links corresponding to all the matches
         * with one entry for each of two or more of the input tables.
         * In the case that we want to output some links with unmatched
         * parts, add new singleton row links as necessary. */
        LinkSet[] missing = new LinkSet[ nTable ];
        for ( int i = 0; i < nTable; i++ ) {
            if ( useAll[ i ] ) {
                missing[ i ] = missingSingles( links, i );
            }
        }
        for ( int i = 0; i < nTable; i++ ) {
            if ( missing[ i ] != null ) {
                for ( Iterator it = missing[ i ].iterator(); it.hasNext(); ) {
                    links.addLink( (RowLink) it.next() );
                }
                missing[ i ] = null;
            }
        }

        /* Now filter the links to contain only those composite rows we're
         * interested in. */
        for ( Iterator it = links.iterator(); it.hasNext(); ) {
            RowLink link = (RowLink) it.next();
            if ( ! acceptRow( link, useAll ) ) {
                it.remove();
            }
        }

        /* Return the matched list. */
        endMatch();
        return links;
    }

    /**
     * Returns a list of RowLink objects corresponding to all the internal
     * matches in this matcher's sole table using its match engine.
     *
     * @param  includeSingles  whether to include unmatched (singleton) 
     *         row links in the returned link set
     * @return a set of {@link RowLink} objects giving all the groups of
     *         matched objects in this matcher's sole table
     */
    public LinkSet findInternalMatches( boolean includeSingles ) 
            throws IOException, InterruptedException {
        checkRandom();

        /* Check we have a single table. */
        if ( nTable != 1 ) {
            throw new IllegalStateException( "Internal matches only make sense "
                                           + "with a single table" );
        }
        startMatch();

        /* Locate all the pairs. */
        LinkSet links = findPairs( getAllPossibleLinks() );

        /* Join up pairs into larger groupings. */
        links = agglomerateLinks( links );

        /* Add unmatched rows if required. */
        if ( includeSingles ) {
            for ( Iterator it = missingSingles( links, 0 ).iterator();
                  it.hasNext(); ) {
                links.addLink( (RowLink) it.next() );
                it.remove();
            }
        }

        /* Return the list. */
        endMatch();
        return links;
    }

    /**
     * Identifies all the pairs of equivalent rows in a set of RowLinks.
     * Internal matches (ones corresponding to two rows of the same table)
     * are included as well as external ones.
     * The input set <code>possibleLinks</code> may be affected
     * by this routine.
     * 
     * @param  possibleLinks  a set of {@link RowLink} objects which 
     *         correspond to groups of possibly matched objects according
     *         to the match engine's criteria
     * @return  a set of RowLink objects which represent all the actual
     *          distinct row pairs from <tt>possibleLinks</tt>
     */
    private LinkSet findPairs( LinkSet possibleLinks )
            throws IOException, InterruptedException {
        LinkSet pairs = createLinkSet();
        double nLink = (double) possibleLinks.size();
        int iLink = 0;
        indicator.startStage( "Locating pairs" );
        for ( Iterator it = possibleLinks.iterator(); it.hasNext(); ) {

            /* Obtain the link and remove it from the input set for 
             * memory efficiency. */
            RowLink link = (RowLink) it.next();
            it.remove();

            /* Check whether this link is non-trivial. */
            int nref = link.size();
            if ( nref > 1 ) {

                /* Cache the rows from each ref since it may be expensive
                 * to get them multiple times. */
                Object[][] binnedRows = new Object[ nref ][];
                for ( int i = 0; i < nref; i++ ) {
                    RowRef ref = link.getRef( i );
                    StarTable table = tables[ ref.getTableIndex() ];
                    binnedRows[ i ] = table.getRow( ref.getRowIndex() );
                }

                /* Do a pairwise comparison of all the rows in the same group.
                 * If they match, add the new pair to the set of pairs. */
                for ( int i = 0; i < nref; i++ ) {
                    for ( int j = 0; j < i; j++ ) {
                        RowLink pair = new RowLink( link.getRef( i ),
                                                    link.getRef( j ) );
                        if ( ! pairs.containsLink( pair ) ) {
                            double score = engine.matchScore( binnedRows[ i ],
                                                              binnedRows[ j ] );
                            if ( score >= 0 ) {
                                pair.setScore( score );
                                pairs.addLink( pair );
                            }
                        }
                    }
                }
            }
            indicator.setLevel( ++iLink / nLink );
        }
        indicator.endStage();
        return pairs;
    }

    /**
     * Identifies all the pairs of equivalent rows from a set of RowLinks
     * between rows from two specified tables.
     *
     * <p>The input set, <code>possibleLinks</code>, may be affected.
     *
     * <p>Since links are being sought between a pair of tables, a more
     * efficient algorithm can be used than for {@link #findPairs}.
     *
     * @param  possibleLinks  a set of {@link RowLink} objects which 
     *         correspond to groups of possibly matched objects 
     *         according to the match engine's criteria
     * @param  index1  index of the first table
     * @param  index2  index of the second table
     * @return   matched row pairs
     */
    private LinkSet findInterPairs( LinkSet possibleLinks,
                                    int index1, int index2 )
            throws IOException, InterruptedException {
        LinkSet pairs = createLinkSet();
        double nLink = (double) possibleLinks.size();
        int iLink = 0;
        indicator.startStage( "Locating inter-table pairs" );
        for ( Iterator it = possibleLinks.iterator(); it.hasNext(); ) {

            /* Get the next link and delete it from the input list, for
             * memory efficiency. */
            RowLink link = (RowLink) it.next();
            it.remove();

            /* Check if we have a non-trivial link. */
            int nref = link.size();
            if ( nref > 1 ) {

                /* Check if rows from both the required tables are present
                 * in this link - if not, there can't be any suitable pairs. */
                boolean got1 = false;
                boolean got2 = false;
                for ( int i = 0; i < nref && ! ( got1 && got2 ); i++ ) {
                    int tableIndex = link.getRef( i ).getTableIndex();
                    got1 = got1 || tableIndex == index1;
                    got2 = got2 || tableIndex == index2;
                }
                if ( got1 && got2 ) {

                    /* Cache the rows from each ref, since it may be expensive
                     * to get them multiple times. */
                    Object[][] binnedRows = new Object[ nref ][];
                    for ( int i = 0; i < nref; i++ ) {
                        RowRef ref = link.getRef( i );
                        StarTable table = tables[ ref.getTableIndex() ];
                        binnedRows[ i ] = table.getRow( ref.getRowIndex() );
                    }

                    /* Do a pairwise comparison of each eligible pair in the
                     * group.  If they match, add the new pair to the set
                     * of matched pairs. */
                    for ( int i = 0; i < nref; i++ ) {
                        RowRef refI = link.getRef( i );
                        int iTableI = refI.getTableIndex();
                        for ( int j = 0; j < i; j++ ) {
                            RowRef refJ = link.getRef( j );
                            int iTableJ = refJ.getTableIndex();
                            if ( iTableI == index1 && iTableJ == index2 ||
                                 iTableI == index2 && iTableJ == index1 ) {
                                RowLink pair = new RowLink( refI, refJ );
                                if ( ! pairs.containsLink( pair ) ) {
                                    double score = 
                                        engine.matchScore( binnedRows[ i ],
                                                           binnedRows[ j ] );
                                    if ( score >= 0 ) {
                                        pair.setScore( score );
                                        pairs.addLink( pair );
                                    }
                                }
                            }
                        }
                    }
                }
            }
            indicator.setLevel( ++iLink / nLink );
        }
        indicator.endStage();
        return pairs;
    }

    /**
     * Goes through all tables and gets a preliminary set of all
     * the groups of rows which are possibly linked by a chain of
     * matches.  This includes all inter- and intra-table matches.
     *
     * @return  set of {@link RowLink} objects which constitute possible
     *          matches
     */
    private LinkSet getAllPossibleLinks()
            throws IOException, InterruptedException {
        BinContents bins = new BinContents( indicator );
        long totalRows = 0;
        long nBin = 0;
        for ( int itab = 0; itab < nTable; itab++ ) {
            StarTable table = tables[ itab ];
            ProgressRowSequence rseq = 
                new ProgressRowSequence( tables[ itab ], indicator,
                                         "Binning rows for table " + 
                                         (itab + 1 ) );
            for ( long lrow = 0; rseq.nextProgress(); lrow++ ) {
                Object[] keys = engine.getBins( rseq.getRow() );
                int nkey = keys.length;
                if ( nkey > 0 ) {
                    RowRef rref = new RowRef( itab, lrow );
                    for ( int ikey = 0; ikey < nkey; ikey++ ) {
                        bins.putRowInBin( keys[ ikey ], rref );
                    }
                }
                nBin += nkey;
                totalRows++;
            }
            rseq.close();
        }
        indicator.logMessage( "Average bin count per row: " +
                              (float) ( nBin / (double) totalRows ) );
        LinkSet links = createLinkSet();
        bins.addRowLinks( links );
        return links;
    }

    /**
     * Gets a list of all the pairs of rows which constitute possible 
     * links between two tables.
     *
     * @param   index1   index of the first table
     * @param   index2   index of the second table
     * @return  set of {@link RowLink} objects which constitute possible
     *          matches
     */
    public LinkSet getPossibleInterLinks( int index1, int index2 )
            throws IOException, InterruptedException {
        int ncol = tables[ index1 ].getColumnCount();
        if ( tables[ index2 ].getColumnCount() != ncol ) {
            throw new IllegalStateException();
        }

        long nIncludedRows1 = tables[ index1 ].getRowCount();
        long nIncludedRows2 = tables[ index2 ].getRowCount();

        /* If we have a match engine which is capable of working out a 
         * restricted region over which matches are possible, find the
         * intersection of those ranges for the two tables being matched.
         * This will enable us to throw out any potential matches outside
         * this region without having to bin them, and can save a lot
         * of time and memory for some cases. */
        Comparable[] min = new Comparable[ ncol ];
        Comparable[] max = new Comparable[ ncol ];
        if ( engine.canBoundMatch() ) {
            indicator.logMessage( "Attempt to locate " +
                                  "restricted common region" );
            try {
                Comparable[][] bounds1 = getBounds( index1 );
                Comparable[][] bounds2 = getBounds( index2 );
                Comparable[] min1 = bounds1[ 0 ];
                Comparable[] max1 = bounds1[ 1 ];
                Comparable[] min2 = bounds2[ 0 ];
                Comparable[] max2 = bounds2[ 1 ];
                for ( int i = 0; i < ncol; i++ ) {
                    if ( min1[ i ] != null && min2[ i ] != null ) {
                        min[ i ] = min1[ i ].compareTo( min2[ i ] ) > 0 
                                 ? min1[ i ]
                                 : min2[ i ];
                    }
                    if ( max1[ i ] != null && max2[ i ] != null ) {
                        max[ i ] = max1[ i ].compareTo( max2[ i ] ) < 0
                                 ? max1[ i ]
                                 : max2[ i ];
                    }
                }
                logTupleBounds( "Potential match region: ", min, max );

                /* Count the rows in the match region for each table. */
                nIncludedRows1 = countInRange( index1, min, max );
                nIncludedRows2 = countInRange( index2, min, max );
            }

            /* The Comparable.compareTo() method which is used heavily in
             * the above processing will result if some of the columns are
             * comparable, but not mutually comparable (e.g. different 
             * Number classes like Float and Double).  In this case catch
             * the error, forget about trying to locate max/mins, and move on.
             * This will only impact efficiency, not correctness. */ 
            catch ( ClassCastException e ) {
                indicator.logMessage( "Common region location failed " +
                                      "(incompatible value types)" );
                min = new Comparable[ ncol ];
                max = new Comparable[ ncol ];
            }
        }

        /* Now do the actual binning and identification of possible links.
         * For efficiency, we want to do the table which will fill the 
         * smallest number of bins first (presumably the one with the
         * smallest number of rows in the match region. */
        return nIncludedRows1 < nIncludedRows2 
             ? getPossibleInterLinks( index1, index2, min, max )
             : getPossibleInterLinks( index2, index1, min, max );
    }
        
    /**
     * Gets a list of all the pairs of rows which constitute possible 
     * links between two tables.  For efficiency reasons, 
     * the table at <tt>index1</tt> ought to be the one with fewer
     * rows in the match region.
     *
     * @param   index1   index of the first table
     * @param   index2   index of the second table
     * @param   min      array of tuple elements to consider as minimum
     *                   values to consider for the match.
     *                   If one of the elements, or <tt>min</tt> itself,
     *                   is null, no minimum is considered to be in effect
     * @param   max      array of tuple elements to consider as maximum
     *                   values to consider for the match.
     *                   If one of the elements, or <tt>max</tt> itself,
     *                   is null, no maximum is considered to be in effect
     * @return  set of {@link RowLink} objects which constitute possible
     *          matches
     */
    private LinkSet getPossibleInterLinks( int index1, int index2,
                                           Comparable[] min, Comparable[] max )
            throws IOException, InterruptedException {
        BinContents bins = new BinContents( indicator );

        /* Bin all the rows in the first (hopefully shorter) table. */
        { // code block prevents variable leakage
            ProgressRowSequence rseq1 = 
                new ProgressRowSequence( tables[ index1 ], indicator,
                                         "Binning rows for table " + 
                                         ( index1 + 1 ) );
            long exclude1 = 0;
            for ( long lrow1 = 0; rseq1.nextProgress(); lrow1++ ) {
                Object[] row = rseq1.getRow();
                if ( inRange( row, min, max ) ) {
                    Object[] keys = engine.getBins( row );
                    int nkey = keys.length;
                    if ( nkey > 0 ) {
                        RowRef rref = new RowRef( index1, lrow1 );
                        for ( int ikey = 0; ikey < nkey; ikey++ ) {
                            bins.putRowInBin( keys[ ikey ], rref );
                        }
                    }
                }
            }
            rseq1.close();
            if ( exclude1 > 0 ) {
                indicator.logMessage( exclude1 + 
                                      " rows excluded (out of match region)" );
            }
        }

        /* Bin any of the rows in the second table which will go in bins
         * we've already added from the first one.  There's no point in
         * doing ones which haven't been filled by the first table, 
         * since they can't result in inter-table matches. */
        { // code block prevents variable leakage
            ProgressRowSequence rseq2 =
                new ProgressRowSequence( tables[ index2 ], indicator,
                                         "Binning rows for table " + 
                                         ( index2 + 1 ) );
            long exclude2 = 0;
            for ( long lrow2 = 0; rseq2.nextProgress(); lrow2++ ) {
                Object[] row = rseq2.getRow();
                if ( inRange( row, min, max ) ) {
                    Object[] keys = engine.getBins( row );
                    int nkey = keys.length;
                    if ( nkey > 0 ) {
                        RowRef rref = new RowRef( index2, lrow2 );
                        for ( int ikey = 0; ikey < nkey; ikey++ ) {
                            Object key = keys[ ikey ];
                            if ( bins.containsKey( key ) ) {
                                bins.putRowInBin( key, rref );
                            }
                        }
                    }
                }
            }
            rseq2.close();
            if ( exclude2 > 0 ) {
                indicator.logMessage( exclude2 +
                                      " rows excluded (out of match region)" );
            }
        }

        /* Return the result. */
        LinkSet links = createLinkSet();
        bins.addRowLinks( links );
        return links;
    }

    /**
     * Removes any links in a set between different rows of the same table.
     * More precisely, every RowLink in the given set is checked and 
     * if necessary replaced by one that contains no more than one RowRef
     * from any one table.  If any refs are discarded from a group,
     * the one earliest in the sort order is retained.
     *
     * @param   links  a mutable collection of {@link RowLink} objects 
     *          to operate on
     */
    private void eliminateInternalLinks( LinkSet links )
            throws InterruptedException {
        RowRef[] refs = new RowRef[ nTable ];
        LinkSet replacements = createLinkSet();

        /* Go through every link in the set. */
        indicator.startStage( "Eliminating internal links" );
        double nLink = (double) links.size();
        int iLink = 0;
        for ( Iterator it = links.iterator(); it.hasNext(); ) {
            RowLink link = (RowLink) it.next();
            int nref = link.size();
            if ( link.size() > 1 ) {

                /* Fill up slots for each table with zero or one RowRef,
                 * noting if we come across any multiple potential entries
                 * from the same table. */
                Arrays.fill( refs, null );
                boolean dup = false;
                for ( int i = 0; i < nref; i++ ) {
                    RowRef ref = link.getRef( i );
                    int iTable = ref.getTableIndex();
                    if ( refs[ iTable ] == null ) {
                        refs[ iTable ] = ref;
                    }
                    else {
                        dup = true;
                    }
                }

                /* If we came across duplicates, remove this entry from the
                 * set and schedule a replacement for addition later. */
                if ( dup ) {
                    it.remove();
                    List repRefs = new ArrayList();
                    for ( int i = 0; i < nTable; i++ ) {
                        if ( refs[ i ] != null ) {
                            repRefs.add( refs[ i ] );
                        }
                    }
                    RowLink repLink = new RowLink( repRefs );
                    replacements.addLink( repLink );
                }
            }
            indicator.setLevel( ++iLink / nLink );
        }
        indicator.endStage();
        indicator.logMessage( "Internal links removed: " 
                            + replacements.size() );
        for ( Iterator it = replacements.iterator(); it.hasNext(); ) {
            RowLink repLink = (RowLink) it.next();
            links.addLink( repLink );
            it.remove();
        }
    }

    /**
     * Constructs a set of singleton row links representing rows which 
     * are not already represented in a given link set.
     *
     * @param   links  set of {@link RowLink}s
     * @param   iTable  table index for the rows which must be represented
     * @return  new set of RowLinks containing one singleton entry for
     *          each RowRef in table <tt>iTable</tt> which does not appear
     *          in any of the links in <tt>links</tt>
     */
    private LinkSet missingSingles( LinkSet links, int iTable ) {

        /* Find out what rowrefs for this table are present in all the links. */
        BitSet present = new BitSet();
        for ( Iterator it = links.iterator(); it.hasNext(); ) {
            RowLink link = (RowLink) it.next();
            int nref = link.size();
            for ( int i = 0; i < nref; i++ ) {
                RowRef ref = link.getRef( i );
                if ( ref.getTableIndex() == iTable ) {
                    present.set( checkedLongToInt( ref.getRowIndex() ) );
                }
            }
        }

        /* Construct and return a set with one new singleton RowRef for 
         * each row that is not present. */
        int nrow = checkedLongToInt( tables[ iTable ].getRowCount() );
        LinkSet singles = createLinkSet();
        for ( int iRow = 0; iRow < nrow; iRow++ ) {
            if ( ! present.get( iRow ) ) {
                singles.addLink( new RowLink( new RowRef( iTable, iRow ) ) );
            }
        }
        return singles;
    }

    /**
     * Applies a set of options to a RowLink, eliminating
     * any elements which do not fit the given options.
     *
     * @param  link   RowLink representing the row to be tested
     * @param  useAll  array of flags indicating for each table whether
     *         all rows should be used or just matching ones
     */
    private boolean acceptRow( RowLink link, boolean[] useAll ) {
        boolean[] present = new boolean[ nTable ];
        int nref = link.size();
        for ( int i = 0; i < nref; i++ ) {
            RowRef ref = link.getRef( i );
            int iTable = ref.getTableIndex();
            present[ iTable ] = true;
        }
        boolean ok = true;
        for ( int i = 0; i < nTable; i++ ) {
            boolean pres = present[ i ];
            if ( useAll[ i ] ) {
                if ( pres ) {
                    return true;
                }
            }
            else {
                if ( ! pres ) {
                    ok = false;
                }
            }
        }
        return ok;
    }

    /**
     * Goes through a set of matched pairs and makes sure that no RowRef is
     * contained in more than one pair.  If multiple pairs exist containing
     * the same RowRef, all but one (the one with the lowest score) 
     * are discarded.  
     * <p>The links in the input set should be 
     * the keys are 2-ref <tt>RowLink</tt>s representing a matched pair,
     * with non-blank pair scores.
     * The pairs may only contain RowRefs with a table index of 0 or 1.
     *
     * <p>The input set, <code>pairs</code>, may be affected by this method.
     * 
     * @param  pairs  set of {@link RowLink} objects representing matched pairs
     * @return  set resembling pairs but with multiple entries discarded
     */
    private LinkSet eliminateMultipleRowEntries( LinkSet pairs ) 
            throws InterruptedException {

        /* Set up a map to keep track of the best score so far keyed by
         * RowRef. */
        Map bestRowScores = new HashMap();

        /* We will be copying entries from the input map to an output one,
         * retaining only the best matches for each row. */
        LinkSet inPairs = pairs;
        LinkSet outPairs = createLinkSet();

        /* Iterate over each entry in the input set, deleting it and 
         * selectively copying to the output set as we go. 
         * This means we don't need double the amount of memory. */
        double nPair = inPairs.size();
        int iPair = 0;
        indicator.startStage( "Eliminating multiple row references" );
        for ( Iterator it = inPairs.iterator(); it.hasNext(); ) {
            RowLink pair = (RowLink) it.next();
            it.remove();
            double score = pair.getScore();
            if ( pair.size() != 2 || Double.isNaN( score ) || score < 0.0 ) {
                throw new IllegalArgumentException();
            }
            RowRef ref1 = pair.getRef( 0 );
            RowRef ref2 = pair.getRef( 1 );
            if ( ref1.getTableIndex() != 0 || ref2.getTableIndex() != 1 ) {
                throw new IllegalArgumentException();
            }
            RowLink best1 = (RowLink) bestRowScores.get( ref1 );
            RowLink best2 = (RowLink) bestRowScores.get( ref2 );

            /* If neither row in this pair has been seen before, or we
             * have a better match this time than previous appearances,
             * copy this entry across to the output set. */
            if ( ( best1 == null || score < best1.getScore() ) &&
                 ( best2 == null || score < best2.getScore() ) ) {

                /* If a pair associated with either of these rows has been
                 * encountered before now, remove it from the output set. */
                if ( best1 != null ) {
                    outPairs.removeLink( best1 );
                }
                if ( best2 != null ) {
                    outPairs.removeLink( best2 );
                }

                /* Copy the current pair into the output set. */
                outPairs.addLink( pair );

                /* Record the current pair indexed under both its constituent
                 * rows. */
                bestRowScores.put( ref1, pair );
                bestRowScores.put( ref2, pair );
            }

            /* Report on progress. */
            indicator.setLevel( ++iPair / nPair );
        }
        indicator.endStage();
        assert inPairs.size() == 0;
        return outPairs;
    }

    /**
     * Rationalises the elements of a set of <tt>RowLink</tt>s 
     * so that it contains guaranteed
     * mutually exclusive RowLink objects (no member RowLink contains
     * a RowRef contained by any other member).
     * Any set of rows linked up in the initial members are
     * joined with any links which are joined
     * by other links in which any of its members participates.
     * Any row which appears in the initial
     * one or more times (as part of one or more <tt>RowLink</tt>s)
     * therefore appears exactly once in the resulting set
     * as part of a new <tt>RowLink</tt> which may contain more RowRefs.
     *
     * <p>Typically this method will be called on a set
     * resulting from an invocation of {@link #findPairs}.
     * Note that in this case
     * it is not guaranteed that all the rows it links are mutually equal
     * in the sense defined by the match engine's
     * {@link MatchEngine#matches} method, but the members of each
     * <tt>RowLink</tt> will form a connected graph with the nodes
     * connected by matches.
     *
     * @param   links  set of {@link RowLink} objects
     * @return  disjoint set of {@link RowLink} objects
     */
    private LinkSet agglomerateLinks( LinkSet links ) 
            throws InterruptedException {
        indicator.logMessage( "Agglomerating links" );

        /* Construct a new hash mapping each RowRef in the given set of
         * links to a list of all the links it appears in. */
        Map refMap = new HashMap();
        for ( Iterator linkIt = links.iterator(); linkIt.hasNext(); ) {
            RowLink link = (RowLink) linkIt.next();
            int nref = link.size();
            for ( int i = 0; i < nref; i++ ) {
                RowRef ref = link.getRef( i );
                if ( ! refMap.containsKey( ref ) ) {
                    refMap.put( ref, new LinkedList() );
                }
                ((Collection) refMap.get( ref )).add( link );
            }
        }

        /* Prepare a new set to contain the agglomerated links.
         * We will populate this with disjoint links at the same time
         * as removing the corresponding RowRefs from the refMap. 
         * This both keeps track of which ones we've done and keeps
         * memory usage down. */
        LinkSet agglomeratedLinks = createLinkSet();

        /* Check for any isolated links, that is ones none of whose members
         * appear in any other links.  These can be handled more efficiently
         * than ones with more complicated relationships. */
        for ( Iterator it = links.iterator(); it.hasNext(); ) {
            RowLink link = (RowLink) it.next();
            int nref = link.size();
            boolean isolated = true;
            for ( int i = 0; isolated && i < nref; i++ ) {
                RowRef ref = link.getRef( i );
                Collection refLinks = (Collection) refMap.get( ref );
                isolated = isolated && refLinks.size() == 1;
            } 

            /* If it is isolated, just copy the link to the agglomerated list,
             * and remove the refs from the map. */
            if ( isolated ) {
                agglomeratedLinks.addLink( link );
                for ( int i = 0; i < nref; i++ ) {
                    RowRef ref = link.getRef( i );
                    Object removed = refMap.remove( ref );
                    assert removed != null;
                }
            }
        }

        /* Take a key from the map we have just constructed, and walk its
         * links recursively to see which nodes we can reach from it.
         * Collect such nodes in a set, and create a new
         * RowLink in the output list from it.  This has the side-effect
         * of removing map entries when they have no more unused links,
         * which means we don't encounter them more than once
         * (and it's also good for memory usage).
         * Repeat until there are no nodes left in the input map. */
        double nRefs = refMap.size();
        indicator.startStage( "Walking links" );
        while ( ! refMap.isEmpty() ) {
            indicator.setLevel( 1.0 - ( refMap.size() / nRefs ) );
            RowRef ref1 = (RowRef) refMap.keySet().iterator().next();
            Set refSet = new HashSet();
            walkLinks( ref1, refMap, refSet );
            agglomeratedLinks.addLink( new RowLink( refSet ) );
        }
        indicator.endStage();

        /* Replace the contents of the used list with the new contents. */
        return agglomeratedLinks;
    }

    /**
     * Recursively pulls out connected nodes (RowRefs) from a map of
     * RowRefs to RowLinks and dumps them in a set of nodes.
     *
     * @param   baseRef  the RowRef at which to start/continue the search
     * @param   refMap   a map of RowRefs to (all so far untraversed) RowLinks
     * @param   outSet   an existing set of RowRefs into which new RowRefs
     *                   connected to baseRef should be inserted
     */
    private static void walkLinks( RowRef baseRef, Map refMap, Set outSet ) {

        /* Do nothing if the output set already contains the requested
         * reference; without this test we would recurse to infinite depth. */
        if ( ! outSet.contains( baseRef ) ) {

            /* Get all the links of which this reference is a member. */
            Collection links = (Collection) refMap.get( baseRef );
            if ( ! links.isEmpty() ) {

                /* Add the current row to the output set. */
                outSet.add( baseRef );

                /* Recurse over all the so-far untraversed rows which are
                 * linked to this one. */
                for ( Iterator linkIt = links.iterator(); linkIt.hasNext(); ) {
                    RowLink link = (RowLink) linkIt.next();
                    for ( int i = 0; i < link.size(); i++ ) {
                        RowRef rref = link.getRef( i );
                        walkLinks( rref, refMap, outSet );
                    }

                    /* Having traversed this link, remove it so it is never
                     * encountered again. */
                    linkIt.remove();
                }
            }

            /* If there are no more links in this list, we can forget
             * about it. */
            if ( links.isEmpty() ) {
                refMap.remove( baseRef );
            }
        }
    }

    /**
     * Checks that the given tables all provide random access.
     *
     * @throws  IllegalArgumentException if any of <tt>tables</tt> does
     *          not provide random access
     */
    private void checkRandom() {
        for ( int itab = 0; itab < tables.length; itab++ ) {
            if ( ! tables[ itab ].isRandom() ) {
                throw new IllegalArgumentException( "Table " + tables[ itab ]
                                                  + " is not random access" );
            }
        }
    }

    /**
     * Calculates the upper and lower bounds of the region in tuple-space
     * for one of this matcher's tables.  
     *
     * @param   tIndex  index of the table to calculate limits for
     * @return  2-element Object[] array; first element contains a tuple
     *          giving the minimum values of all tuple elements that need 
     *          to be considered and the second element does the same
     *          for maximum values.  These points are opposite corners of
     *          a hyper-rectangle in tuple-space.  Any of the elements may
     *          be null to indicate no limit in that direction. 
     *          All non-null elements will be {@link java.lang.Comparable}.
     * @throws  ClassCastException  if objects are not mutually comparable
     */
    private Comparable[][] getBounds( int tIndex )
            throws IOException, InterruptedException {

        /* We can only do anything useful the match engine knows how to
         * calculate bounds. */
        if ( ! engine.canBoundMatch() ) {
            return new Comparable[ 2 ][];
        }

        /* See which columns are comparable. */
        StarTable table = tables[ tIndex ];
        int ncol = table.getColumnCount();
        boolean[] isComparable = new boolean[ ncol ];
        int ncomp = 0;
        for ( int icol = 0; icol < ncol; icol++ ) {
            if ( Comparable.class.isAssignableFrom( table.getColumnInfo( icol )
                                                   .getContentClass() ) ) {
                isComparable[ icol ] = true;
                ncomp++;
            }
        }

        /* If none of the columns are comparable, there's no point. */
        if ( ncomp == 0 ) {
            return new Comparable[ 2 ][];
        }

        /* Go through each row finding the minimum and maximum value 
         * for each column (coordinate). */
        Comparable[] mins = new Comparable[ ncol ];
        Comparable[] maxs = new Comparable[ ncol ];
        ProgressRowSequence rseq =
            new ProgressRowSequence( table, indicator,
                                     "Assessing range of coordinates " +
                                     "from table " + ( tIndex + 1 ) );
        try {
            for ( long lrow = 0; rseq.nextProgress(); lrow++ ) {
                Object[] row = rseq.getRow();
                for ( int icol = 0; icol < ncol; icol++ ) {
                    if ( isComparable[ icol ] ) {
                        Object cell = row[ icol ];
                        if ( cell instanceof Comparable ) {
                            Comparable val = (Comparable) cell;
                            if ( mins[ icol ] == null || 
                                 mins[ icol ].compareTo( val ) > 0 ) {
                                mins[ icol ] = val;
                            }
                            if ( maxs[ icol ] == null ||
                                 maxs[ icol ].compareTo( val ) < 0 ) {
                                maxs[ icol ] = val;
                            }
                        }
                    }
                }
            }
        }

        /* It's possible, though not particularly likely, that a 
         * compareTo invocation can result in a ClassCastException 
         * (e.g. comparing an Integer to a Double).  Such ClassCastExceptions
         * should get caught higher up, but we need to make sure the
         * row sequence is closed or the logging will get in a twist. */
        finally {
            rseq.close();
        }

        /* Report and return. */
        logTupleBounds( "Limits are: ", mins, maxs );

        /* Get the match engine to convert the min/max values into 
         * a possible match region (presumably by adding a separation
         * region on). */
        return engine.getMatchBounds( mins, maxs );
    }

    /**
     * Determines whether a point in tuple-space is within a range in 
     * the same space.  Each element of the 'point' is compared with 
     * a given minimum and maximum; the point is considered in range if 
     * each element (coordinate) of <tt>row</tt> 
     * is between the corresponding elements
     * of the <tt>min</tt> and <tt>max</tt> arrays (inclusive) <em>or</em>
     * if any of the participants in the comparison is <tt>null</tt>.
     * The way it's used, it is important that if in doubt, it should
     * be considered in range.
     * 
     * <p>As a special case, if either of the arrays <tt>max</tt> or 
     * <tt>min</tt> is <tt>null</tt>, the point is considered to be in range.
     * 
     * @param  row  point to assess
     * @param  min  lower bound of permissible coordinates
     * @param  max  upper bound of permissible coordinates
     * @return true iff <tt>row</tt> is between <tt>min</tt> and <tt>max</tt>
     * @throws  ClassCastException  if objects are not mutually comparable
     */
    private boolean inRange( Object[] row, 
                             Comparable[] min, Comparable[] max ) {
        if ( min != null && max != null ) {
            int ncol = row.length;
            for ( int i = 0; i < ncol; i++ ) {
                if ( row[ i ] instanceof Comparable ) {
                    Comparable val = (Comparable) row[ i ];
                    if ( min[ i ] != null && val.compareTo( min[ i ] ) < 0 ||
                         max[ i ] != null && val.compareTo( max[ i ] ) > 0 ) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Returns the number of rows in a table which fall within a given
     * range of min/max values.
     * 
     * @param  tIndex  index of table to assess
     * @param  min  lower bound of permissible coordinates
     * @param  max  upper bound of permissible coordinates
     * @return  number of rows of <tt>table</tt> that fall in the coordinate
     *          range defined by <tt>min</tt> and <tt>max</tt>
     * @throws  ClassCastException  if objects are not mutually comparable
     */
    private long countInRange( int tIndex, Comparable[] min, 
                               Comparable[] max )
            throws IOException, InterruptedException {
        ProgressRowSequence rseq = 
            new ProgressRowSequence( tables[ tIndex ], indicator, 
                                     "Counting rows in match region " +
                                     "for table " + ( tIndex + 1 ) );
        long nInclude = 0;
        try {
            for ( long lrow = 0; rseq.nextProgress(); lrow++ ) {
                if ( inRange( rseq.getRow(), min, max ) ) {
                    nInclude++;
                }
            }
        }
        finally {
            rseq.close();
        }
        indicator.logMessage( nInclude + " rows in match region" );
        return nInclude;
    }

    /**
     * Writes a log message to the indicator about a set of min..max 
     * limits.
     *
     * @param  heading  lead text
     * @param  mins     minimum values
     * @param  maxs     maximum values
     */
    private void logTupleBounds( String heading, Object[] mins, 
                                 Object[] maxs ) {
        int ncol = 0;
        if ( mins != null ) {
            ncol = mins.length;
        }
        else if ( maxs != null ) {
            ncol = maxs.length;
        }
        if ( ncol == 0 ) {
            return;
        }
        StringBuffer sbuf = new StringBuffer();
        for ( int i = 0; i < ncol; i++ ) {
            if ( i > 0 ) {
                sbuf.append( ", " );
            }
            if ( mins != null && mins[ i ] instanceof Number ) {
                sbuf.append( ((Number) mins[ i ]).floatValue() );
            }
            sbuf.append( " .. " );
            if ( maxs != null && maxs[ i ] instanceof Number ) {
                sbuf.append( ((Number) maxs[ i ]).floatValue() );
            }
        }
        indicator.logMessage( heading + sbuf );
    }

    /**
     * Signals the start of a user-visible matching process.
     */
    private void startMatch() {
        startTime = new Date().getTime();
    }

    /**
     * Signals the end of a user-visible matching process.
     */
    private void endMatch() {
        long millis = new Date().getTime() - startTime;
        indicator.logMessage( "Elapsed time for match: " + ( millis / 1000 ) + 
                              " seconds" );
    }

    /**
     * Turns a <tt>long</tt> into an <tt>int</tt>, throwing an unchecked
     * exception if it can't be done.
     */
    private int checkedLongToInt( long lval ) {
        return Tables.checkedLongToInt( lval );
    }

}
