package uk.ac.starlink.table.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.io.OutputStream;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import uk.ac.starlink.connect.Branch;
import uk.ac.starlink.connect.FilestoreChooser;
import uk.ac.starlink.connect.Leaf;
import uk.ac.starlink.connect.Node;
import uk.ac.starlink.table.StarTable;
import uk.ac.starlink.table.StarTableOutput;
import uk.ac.starlink.table.StarTableWriter;

/**
 * Save dialogue which uses a {@link uk.ac.starlink.connect.FilestoreChooser}.
 *
 * @author   Mark Taylor (Starlink)
 * @since    25 Feb 2005
 */
class FilestoreTableSaveDialog implements TableSaveDialog {

    private final FilestoreChooser chooser_;
    private FilestorePopup popup_;

    /**
     * Constructor. 
     */
    public FilestoreTableSaveDialog() {
        chooser_ = new FilestoreChooser() {
            public void leafSelected( Leaf leaf ) {
                if ( popup_ != null ) {
                    popup_.selected( leaf );
                }
            }
        };
        chooser_.addDefaultBranches();
    }

    public String getName() {
        return "Filestore Browser";
    }

    public String getDescription() {
        return "Save table to local or remote filespace";
    }

    public boolean isAvailable() {
        return true;
    }

    public boolean showSaveDialog( Component parent, StarTableOutput sto,
                                   ComboBoxModel formatModel, 
                                   StarTable table ) {
        if ( popup_ != null ) {
            throw new IllegalStateException( "Dialogue already visible" );
        }
        Frame frame = null;
        if ( parent != null ) {
            frame = parent instanceof Frame
                  ? (Frame) parent
                  : (Frame) SwingUtilities.getAncestorOfClass( Frame.class,
                                                               parent );
        }           
        FilestorePopup popup = 
            new FilestorePopup( frame, sto, table, formatModel );

        popup.pack();
        popup.setLocationRelativeTo( parent );
        chooser_.setEnabled( true );
        popup_ = popup;
        popup_.show();
        boolean success = popup_ == popup;
        popup_ = null;
        return success;
    }

    /**
     * Returns the chooser component used by this dialogue.
     *
     * @return  chooser
     */
    public FilestoreChooser getChooser() {
        return chooser_;
    }

    /**
     * Helper class which implements the dialogue window used to confront
     * the user.
     */
    private class FilestorePopup extends JDialog {

        SaveWorker worker_;
        final JProgressBar progBar_;
        final StarTableOutput sto_;
        final StarTable table_;
        final ComboBoxModel formatModel_;

        /**
         * Constructs a new popup.
         *
         * @param  frame   owner frame
         * @param  sto     StarTableOutput used for writing tables
         * @param  table   the table to write
         * @param  formatModel  comboboxmodel for selecting output format
         */
        FilestorePopup( Frame frame, StarTableOutput sto, StarTable table,
                        ComboBoxModel formatModel ) {
            super( frame, "Save Table", true );
            sto_ = sto;
            table_ = table;
            formatModel_ = formatModel;

            setDefaultCloseOperation( JDialog.DISPOSE_ON_CLOSE );

            Action cancelAction = new AbstractAction( "Cancel" ) {
                public void actionPerformed( ActionEvent evt ) {
                    if ( worker_ != null ) {
                        worker_.cancel();
                        worker_ = null;
                    }
                    else if ( popup_ == FilestorePopup.this ) {
                        popup_.dispose();
                        popup_ = null;
                    }
                }
            };

            progBar_ = new JProgressBar();
            getContentPane().setLayout( new BorderLayout() );
            getContentPane().add( progBar_, BorderLayout.SOUTH );

            JComponent main = new JPanel( new BorderLayout() );
            getContentPane().add( main, BorderLayout.CENTER );
            main.add( chooser_, BorderLayout.CENTER );

            JComponent formatBox = Box.createHorizontalBox();
            formatBox.add( new JLabel( "Output Format: " ) );
            formatBox.add( new JComboBox( formatModel ) );
            formatBox.add( Box.createHorizontalGlue() );

            JComponent controlBox = Box.createHorizontalBox();
            controlBox.add( Box.createHorizontalGlue() );
            controlBox.add( new JButton( chooser_.getOkAction() ) );
            controlBox.add( Box.createHorizontalStrut( 5 ) );
            controlBox.add( new JButton( cancelAction ) );

            JComponent botBox = Box.createVerticalBox();
            botBox.add( formatBox );
            botBox.add( Box.createVerticalStrut( 5 ) );
            botBox.add( controlBox );
            botBox.setBorder( BorderFactory.createEmptyBorder( 5, 5, 5, 5 ) );
            main.add( botBox, BorderLayout.SOUTH );
        }

        /**
         * Invoked when a leaf has been selected in the chooser.
         *
         * @param  leaf  selected item
         */
        public void selected( final Leaf leaf ) {
            if ( worker_ == null ) {
                if ( exists( leaf ) &&
                     !  confirmOverwrite( this, leaf.toString() ) ) {
                    return;
                }
                worker_ = new SaveWorker( progBar_, table_, leaf.toString() ) {
                    protected void attemptSave( StarTable table )
                            throws IOException {
                        OutputStream stream = leaf.getOutputStream();
                        String format = (String) formatModel_.getSelectedItem();
                        StarTableWriter handler = 
                            sto_.getHandler( format, leaf.getName() );
                        sto_.writeStarTable( table, stream, handler );
                    }
                    protected void done( boolean success ) {
                        worker_ = null;
                        if ( success ) {
                            dispose();
                        }
                    }
                };
                chooser_.setEnabled( false );
                worker_.invoke();
            }
        }
    }

    /**
     * Indicates whether a leaf corresponds to an existing file or not.
     *
     * @param   leaf  abstract path for a leaf node
     * @return  true iff <tt>leaf</tt> already exists in the virtual
     *          filesystem
     */
    private static boolean exists( Leaf leaf ) {
        String name = leaf.getName();
        Branch parent = leaf.getParent();
        if ( parent != null ) {
            Node[] siblings = parent.getChildren();
            for ( int i = 0; i < siblings.length; i++ ) {
                if ( siblings[ i ].getName().equals( name ) ) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Requests confirmation from the user that an existing file can be
     * overwritten.
     *
     * @param  parent   the parent component, used for positioning
     *         dialog boxes
     * @param  loc  location of the file to overwrite
     * @return  <tt>true</tt> if the user agrees it's OK to overwrite
     */
     private static boolean confirmOverwrite( Component parent, String loc ) {
        String[] msg = new String[] {
            "Overwrite existing file \"" + loc + "\"?",
        };
        Object cancelOption = "Cancel";
        Object overwriteOption = "Overwrite";
        Object[] options = new Object[] { cancelOption, overwriteOption };
        int result = JOptionPane
                    .showOptionDialog( parent, msg, "Confirm overwrite", 0,
                                       JOptionPane.WARNING_MESSAGE, null,
                                       options, cancelOption );
        return result == 1;
    }
}
