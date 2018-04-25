package uk.ac.starlink.topcat.activate;

import java.awt.BorderLayout;
import java.io.IOException;
import javax.swing.JPanel;
import uk.ac.starlink.table.ColumnData;
import uk.ac.starlink.table.Tables;
import uk.ac.starlink.table.gui.LabelledComponentStack;
import uk.ac.starlink.topcat.ActionForwarder;
import uk.ac.starlink.topcat.Outcome;
import uk.ac.starlink.topcat.TopcatModel;
import uk.ac.starlink.topcat.ColumnSelector;

/**
 * Partial ActivatorConfigurator implementation for activators that
 * do something with sky position columns.
 *
 * @author   Mark Taylor
 * @since    29 Mar 2018
 */
public abstract class SkyPosConfigurator extends AbstractActivatorConfigurator {

    private final LabelledComponentStack stack_;
    private final ColumnSelector raSelector_;
    private final ColumnSelector decSelector_;

    /**
     * Constructor.
     *
     * @param  tinfo  topcat model information
     */
    protected SkyPosConfigurator( TopcatModelInfo tinfo ) {
        super( new JPanel( new BorderLayout() ) );
        TopcatModel tcModel = tinfo.getTopcatModel();
        raSelector_ =
            new ColumnSelector( tcModel
                               .getColumnSelectorModel( Tables.RA_INFO ),
                                false );
        decSelector_ =
            new ColumnSelector( tcModel
                               .getColumnSelectorModel( Tables.DEC_INFO ),
                                false );
        ActionForwarder forwarder = getActionForwarder();
        raSelector_.addActionListener( forwarder );
        decSelector_.addActionListener( forwarder );
        stack_ = new LabelledComponentStack();
        stack_.addLine( "RA Column", raSelector_ );
        stack_.addLine( "Dec Column", decSelector_ );
        getPanel().add( stack_, BorderLayout.NORTH );
    }

    /**
     * Returns the component stack in which the RA and Dec selectors are placed.
     *
     * @return  component stack
     */
    public LabelledComponentStack getStack() {
        return stack_;
    }

    /**
     * Constructs an Activator based on selected RA and Dec column values.
     *
     * @param  raData  right ascension in radians
     * @param  decData  declination in radians
     * @return  new activator
     */
    protected abstract Activator createActivator( ColumnData raData,
                                                  ColumnData decData );

    /**
     * Returns a config message given that non-blank sky position columns
     * have been supplied.
     *
     * @return   reason why activator is not supplied, or null
     */
    protected abstract String getSkyConfigMessage();

    public Activator getActivator() {
        ColumnData raData = raSelector_.getColumnData();
        ColumnData decData = decSelector_.getColumnData();
        return raData != null && decData != null
             ? createActivator( raData, decData )
             : null;
    }

    public String getConfigMessage() {
        boolean hasRa = raSelector_.getColumnData() != null;
        boolean hasDec = decSelector_.getColumnData() != null;
        if ( hasRa && hasDec ) {
            return getSkyConfigMessage();
        }
        else if ( hasRa ) {
            return "Dec not specified";
        }
        else if ( hasDec ) {
            return "RA not specified";
        }
        else {
            return "RA, Dec not specified";
        }
    }

    /**
     * Partial activator implementation for use with SkyPosConfigurator.
     */
    protected static abstract class SkyPosActivator implements Activator {

        private final ColumnData raData_;
        private final ColumnData decData_;
        private final boolean invokeOnEdt_;

        /**
         * Constructor.
         *
         * @param  raData  right ascension column data in radians
         * @param  decData  declination column data in radians
         * @param  invokeOnEdt  true to invoke on EDT, false on another thread
         */
        SkyPosActivator( ColumnData raData, ColumnData decData,
                         boolean invokeOnEdt ) {
            raData_ = raData;
            decData_ = decData;
            invokeOnEdt_ = invokeOnEdt;
        }

        /**
         * Does the work of consuming the sky position corresponding to
         * an activated row.
         *
         * @param  raDeg  right ascension in degrees
         * @param  decDeg  declination in degrees
         * @return  outcome, may have null message if nothing interesting to say
         */
        protected abstract Outcome useSkyPos( double raDeg, double decDeg );

        public boolean invokeOnEdt() {
            return invokeOnEdt_;
        }

        public Outcome activateRow( long lrow, ActivationMeta meta ) {
            Object raObj;
            Object decObj;
            try {
                raObj = raData_.readValue( lrow );
                decObj = decData_.readValue( lrow );
            }
            catch ( IOException e ) {
                return Outcome.failure( e );
            }
            if ( raObj instanceof Number && decObj instanceof Number ) {
                double raRad = ((Number) raObj).doubleValue();
                double decRad = ((Number) decObj).doubleValue();
                if ( ! Double.isNaN( raRad ) && ! Double.isNaN( decRad ) ) {
                    double raDeg = Math.toDegrees( raRad );
                    double decDeg = Math.toDegrees( decRad );
                    String posTxt = new StringBuffer()
                        .append( "(" )
                        .append( (float) raDeg )
                        .append( ", " )
                        .append( (float) decDeg )
                        .append( ")" )
                        .toString();
                    Outcome resultOutcome = useSkyPos( raDeg, decDeg );
                    if ( ! resultOutcome.isSuccess() ) {
                        return resultOutcome;
                    }
                    String resultTxt = resultOutcome.getMessage();
                    StringBuffer outTxt = new StringBuffer();
                    outTxt.append( posTxt );
                    if ( resultTxt != null && resultTxt.trim().length() > 0 ) {
                        outTxt.append( " " );
                        outTxt.append( resultTxt );
                    }
                    return Outcome.success( outTxt.toString() );
                }
            }

            /* It would be nice to include in this message what the missing
             * sky position components were.  However, it's probably not
             * what the user is expecting, since the data values we have are
             * probably auto-converted into radians. */
            return Outcome.failure( "No sky position" );
        }
    }
}