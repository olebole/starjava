/* ********************************************************
 * This file automatically generated by /d2/starjava/java/source/jniast/src/perl/PermMap.pl
 * Do not edit
 **********************************************************/

package uk.ac.starlink.ast;


/**
 * Java interface to the AST PermMap class
 *  - coordinate permutation Mapping. 
 * A PermMap is a Mapping which permutes the order of coordinates,
 * and possibly also changes the number of coordinates, between its
 * input and output.
 * <p>
 * In addition to permuting the coordinate order, a PermMap may
 * also assign constant values to coordinates. This is useful when
 * the number of coordinates is being increased as it allows fixed
 * values to be assigned to any new ones.
 * 
 * 
 * @see  <a href='http://star-www.rl.ac.uk/cgi-bin/htxserver/sun211.htx/?xref_PermMap'>AST PermMap</a> 
 * @author   Mark Taylor (Starlink) 
 */
public class PermMap extends Mapping {
    /** 
     * Creates a PermMap.   
     * @param  nin  The number of input coordinates.
     * 
     * @param  inperm  An optional array with "nin" elements which, for each input
     * coordinate, should contain the number of the output
     * coordinate whose value is to be used (note that this array
     * therefore defines the inverse coordinate transformation).
     * Coordinates are numbered starting from 1.
     * <p>
     * For details of additional special values that may be used in
     * this array, see the description of the "constant" parameter.
     * <p>
     * If a NULL pointer is supplied instead of an array, each input
     * coordinate will obtain its value from the corresponding
     * output coordinate (or will be assigned the value AST__BAD if
     * there is no corresponding output coordinate).
     * 
     * @param  nout  The number of output coordinates.
     * 
     * @param  outperm  An optional array with "nout" elements which, for each output
     * coordinate, should contain the number of the input coordinate
     * whose value is to be used (note that this array therefore
     * defines the forward coordinate transformation).  Coordinates
     * are numbered starting from 1.
     * <p>
     * For details of additional special values that may be used in
     * this array, see the description of the "constant" parameter.
     * <p>
     * If a NULL pointer is supplied instead of an array, each output
     * coordinate will obtain its value from the corresponding
     * input coordinate (or will be assigned the value AST__BAD if
     * there is no corresponding input coordinate).
     * 
     * @param  constant  An optional array containing values which may be assigned to
     * input and/or output coordinates instead of deriving them
     * from other coordinate values. If either of the "inperm" or
     * "outperm" arrays contains a negative value, it is used to
     * address this "constant" array (such that -1 addresses the
     * first element, -2 addresses the second element, etc.) and the
     * value obtained is used as the corresponding coordinate value.
     * <p>
     * Care should be taken to ensure that locations lying outside
     * the extent of this array are not accidentally addressed. The
     * array is not used if the "inperm" and "outperm" arrays do not
     * contain negative values.
     * <p>
     * If a NULL pointer is supplied instead of an array, the
     * behaviour is as if the array were of infinite length and
     * filled with the value AST__BAD.
     * 
     * @throws  AstException  if an error occurred in the AST library
    */
    public PermMap( int nin, int[] inperm, int nout, int[] outperm, double[] constant ) {
        construct( nin, inperm, nout, outperm, constant );
    }
    private native void construct( int nin, int[] inperm, int nout, int[] outperm, double[] constant );

}
