package uk.ac.starlink.treeview;

import java.awt.Toolkit;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import javax.swing.Icon;
import uk.ac.starlink.util.DataSource;

/**
 * A basic implementation of the {@link DataNode} interface.
 * It may be used directly for simple nodes, or it may be subclassed 
 * for convenience in writing more specific <code>DataNode</code>
 * implementors.
 *
 * @author   Mark Taylor (Starlink)
 * @version  $Id$
 */
public class DefaultDataNode implements DataNode {

    private static DataNodeFactory defaultChildMaker;

    private String name;
    private String label;
    private String desc;
    private String nodetype = "Data node";
    private DataNodeFactory childMaker;
    private CreationState creator;
    private Object parentObject;
    private short iconID = IconFactory.NO_ICON;

    /**
     * Constructs a blank <code>DefaultDataNode</code>.
     */
    public DefaultDataNode() {
    }

    /**
     * Constructs a <code>DefaultDataNode</code> with a given name.
     *
     * @param  name  the name to use for this object.
     */
    public DefaultDataNode( String name ) {
        this();
        setName( name );
    }

    /**
     * The <tt>DefaultDataNode</tt> implementation of this method returns 
     * <tt>false</tt>.
     */
    public boolean allowsChildren() {
        return false;
    }

    /**
     * The <tt>DefaultDataNode</tt> implementation of this method throws
     * <tt>UnsupportedOperationException</tt> 
     * ({@link #allowsChildren} is false).
     */
    public Iterator getChildIterator() {
        throw new UnsupportedOperationException();
    }

    public Object getParentObject() {
        return parentObject;
    }

    public void setParentObject( Object parent ) {
        this.parentObject = parent;
    }

    public void setLabel( String label ) {
        this.label = label;
    }

    public String getLabel() {
        if ( label != null ) {
            return label;
        }
        else if ( name != null ) {
            return name;
        }
        else {
            return "<unnamed>";
        }
    }

    /**
     * Sets the name of this node.  Since the name of a node should not 
     * change over its lifetime (though a label can), this is only 
     * intended for use during construction by subclasses.
     *
     * @param  name  the node's name
     */
    protected void setName( String name ) {
        this.name = name;
        if ( label == null && name != null ) {
            setLabel( label = name );
        }
    }

    public String getName() {
        return name == null ? "..." : name;
    }

    /**
     * Sets the value which will be returned by {@link #getDescription}.
     *
     * @param  desc  the description string
     */
    public void setDescription( String desc ) {
        this.desc = desc;
    }

    public String getDescription() {
        return desc;
    }

    /**
     * The <tt>DefaultDataNode</tt> implementation returns the string "...".
     *
     * @return  "..."
     */
    public String getNodeTLA() {
        return "...";
    }

    public String getNodeType() {
        return nodetype;
    }

    public String toString() {
        return TreeviewUtil.toString( this );
    }

    /**
     * This may be called by subclasses to set the icon returned by 
     * this node to one of the ones defined in the IconFactory class.
     *
     * @param   code  one of the icon identifiers defined as static
     *          final members of the {@link IconFactory} class
     */
    protected void setIconID( short id ) {
        this.iconID = id;
    }

    /**
     * Returns a default icon, unless setIconID has been called, in which
     * case it returns the one indicated by that call.
     *
     * @return   an icon representing this node
     */
    public Icon getIcon() {
        if ( iconID == IconFactory.NO_ICON ) {
            return IconFactory.getIcon( allowsChildren() ? IconFactory.PARENT
                                                         : IconFactory.LEAF );
        }
        else {
            return IconFactory.getIcon( iconID );
        }
    }

    /**
     * Returns a default separator string.
     *
     * @return "."
     */
    public String getPathSeparator() {
        return ".";
    }

    /**
     * The <tt>DefaultDataNode</tt> implementation 
     * returns the label as a default path element.
     *
     * @return  the node's label
     */
    public String getPathElement() {
        return getLabel();
    }

    /**
     * No custom configuration is performed.
     */
    public void configureDetail( DetailViewer dv ) {
    }

    public void setChildMaker( DataNodeFactory factory ) {
        childMaker = factory;
    }

    public DataNodeFactory getChildMaker() {
        if ( defaultChildMaker == null ) {
            defaultChildMaker = new DataNodeFactory();
        }
        if ( childMaker == null ) {
            childMaker = defaultChildMaker;
        }
        return childMaker;
    }

    /**
     * Uses the node's childMaker to turn objects into data nodes.
     * This convenience method just calls 
     * <tt>getChildMaker().makeChildNode(this,childObj)</tt>.
     * In general, nodes should use this method to construct their
     * children.
     *
     * @param  childObj  the object which forms the basis for a child
     *         data node
     * @see    DataNodeFactory#makeDataNode
     */
    public DataNode makeChild( Object childObj ) {
        return getChildMaker().makeChildNode( this, childObj );
    }

    /**
     * Constructs an error data node from a throwable.  This method can
     * be used to create a error which is the child of this node.
     * This convenience method just calls 
     * <tt>getChildMaker().makeErrorDataNode(this,th)</tt>
     *
     * @param  th  the throwable on which the data node will be based
     * @see   DataNodeFactory#makeErrorDataNode
     */
    public DataNode makeErrorChild( Throwable th ) {
        return getChildMaker().makeErrorDataNode( this, th );
    }

    public void setCreator( CreationState state ) {
        this.creator = state;
    }

    public CreationState getCreator() {
        return creator;
    }

    /**
     * It beeps.
     */
    public static void beep() {
        Toolkit.getDefaultToolkit().beep();
    }

}
