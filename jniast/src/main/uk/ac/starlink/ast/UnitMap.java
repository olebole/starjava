/* ********************************************************
 * This file automatically generated by UnitMap.pl.
 *                   Do not edit.                         *
 **********************************************************/

package uk.ac.starlink.ast;


/**
 * Java interface to the AST UnitMap class
 *  - unit (null) Mapping. 
 * A UnitMap is a unit (null) Mapping that has no effect on the
 * coordinates supplied to it. They are simply copied. This can be
 * useful if a Mapping is required (e.g. to pass to another
 * function) but you do not want it to have any effect.
 * The Nin and Nout attributes of a UnitMap are always equal and
 * are specified when it is created.
 * 
 * 
 * @see  <a href='http://star-www.rl.ac.uk/cgi-bin/htxserver/sun211.htx/?xref_UnitMap'>AST UnitMap</a> 
 * @author   Mark Taylor (Starlink) 
 */
public class UnitMap extends Mapping {
    /** 
     * Creates a UnitMap.   
     * @param  ncoord  The number of input and output coordinates (these numbers are
     * necessarily the same).
     * 
     * @throws  AstException  if an error occurred in the AST library
    */
    public UnitMap( int ncoord ) {
        construct( ncoord );
    }
    private native void construct( int ncoord );

}
