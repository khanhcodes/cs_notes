
/************************************************************************************
 * @file BpTreeMap.java
 *
 * @author  John Miller
 *
 * compile javac --enable-preview --release 21 BpTreeMap.java
 * run     java --enable-preview BpTreeMap
 *
 * Split Nodes on Overflow
 * Structure for order = 5 (max of 4 keys), upon first split
 * [ . k4 . -- . -- . -- . ]
 *     [ . k1 . k2 . k3 . -- . ]
 *     [ . k4 . k5 . -- . -- . ]
 * Rules: divider key (k4 added to parent in this case) is the smallest key
 *            in the right subtree (SMALLEST RIGHT)
 *        split node n into (n, right_sibling_node) with larger half staying in n
 *        internal node split promotes middle key to parent as the divider key
 */

import java.io.*;
import java.lang.reflect.Array;
import java.util.*;

import static java.lang.Math.ceil;
import static java.lang.System.out;

/************************************************************************************
 * The `BpTreeMap` class provides B+Tree maps.  B+Trees are used as multi-level index
 * structures that provide efficient access for both point queries and range queries.
 * All keys will be at the leaf level with leaf nodes linked by references.
 * Internal nodes will contain divider keys such that each divider key corresponds to
 * the smallest key in its right subtree (SMALLEST RIGHT).  Keys in left subtree are "<",
 * while keys in right subtree are ">=".
 */
public class BpTreeMap <K extends Comparable <K>, V>
       extends AbstractMap <K, V>
       implements Serializable, Cloneable // , SortedMap <K, V>
{
    private static final boolean DEBUG = true;                        // debug flag

    private static final int ORDER = 5;                               // maximum number of children for a B+Tree node.
    private static final int HALF  = (ORDER - 1) / 2;                 // half of max keys (floor)
    private static final int HALFP = ORDER - HALF;                    // rest of the keys (half plus)

    private final Class <K> classK;                                   // The class for type K.
    private final Class <V> classV;                                   // The class for type V.

//-----------------------------------------------------------------------------------
// Node inner class
//-----------------------------------------------------------------------------------

    /********************************************************************************
     * The `Node` inner class defines nodes that are stored in the B+tree map.
     * Node key: [ . k0 . k1 . k2 . k3 . ]
     *              <  <=   <=   <=   <=         note: number of keys is one less than number of refs
     *      ref:  r0   r1   r2   r3   r4
     * Leaf:      r0 -> next leaf node; r1 -> tuple (k0, ...); r2 -> tuple (k1, ...); etc.
     * Internal:  r0 -> subtree with keys < k0; r1 -> subtree with keys in [k0, k1); etc.
     * Split:     extra room in nodes allows the overflow key to be inserted before split
     */
    private class Node
    {
        boolean   isLeaf;                                             // whether the node is a leaf 
        int       keys;                                               // number of active keys
        K []      key;                                                // array of keys
        Object [] ref;                                                // array of references/pointers

        /****************************************************************************
         * Construct a BpTree node containing keys_ keys
         * @param keys_    number of initial keys
         * @param isLeaf_  whether the node is a leaf
         */
        @SuppressWarnings("unchecked")
        Node (int keys_, boolean isLeaf_)
        {
            isLeaf = isLeaf_;
            keys   = keys_;
            key    = (K []) Array.newInstance (classK, ORDER);
            ref = (isLeaf) ? new Object [ORDER + 1]
                           : (Node []) Array.newInstance (Node.class, ORDER + 1);
        } // constructor

        /****************************************************************************
         * Construct a new root node with one key (and two references) in it.
         * @param left   the left node (< dkey)
         * @param dkey   the divider key
         * @param right  the right node (>= dkey)
         */
        Node (Node left, K dkey, Node right)
        {
            this (1, false);
            key[0] = dkey;                                            // divider key
            ref[0] = left; ref[1] = right;                            // left and right references
        } // constructor

        /****************************************************************************
         * Return whether this node has overflowed (too many keys).
         */
        boolean overflow () { return keys >= ORDER; }

        /****************************************************************************
         * Find and return the first position where 'k < key_i' in this node.
         * @param k  the key whose position is sought
         */
        int find (K k)
        {
            for (var i = 0; i < keys; i++) if (k.compareTo (key[i]) < 0) return i;
            return keys;
        } // find

        /****************************************************************************
         * Find and return the first position where 'k == key_i' in this node.
         * @param k  the key whose position is sought
         */
        int findEq (K k)
        {
            for (var i = 0; i < keys; i++) if (k.compareTo (key[i]) == 0) return i;
            return -1;
        } // find

        /****************************************************************************
         * Add the new key k and value v into this node at insertion position (ip).
         * @param k  the new key
         * @param v  the new value (or node for internal nodes)
         */
        void add (K k, Object v)
        {
            var ip = find (k);                                          // find insertion position (ip)
            for (var i = keys; i > ip; i--) {                           // make room by shifting keys right
                key[i]   = key[i-1];
                ref[i+1] = ref[i];
            } // for
            key[ip]   = k;                                              // insert new key
            ref[ip+1] = v;                                              // insert new value (right of key)
            keys     += 1;                                              // increment to number of active keys
        } // add

        /****************************************************************************
         * Split this LEAF node by creating a right sibling node (rt) and moving
         * half the keys and references to that new node, leaving halfp.
         * Return the right sibling node, where the divider key is key[0].
         */
        Node split ()
        {
            var rt = new Node (HALF, true);                             // allocate leaf right sibling node (rt)
            for (var i = 0; i < HALF; i++) {                            // move largest half of keys (with refs) to rt
                rt.key[i]   = key[HALFP + i];
                rt.ref[i+1] = ref[HALFP + i + 1];                       // refs are right of keys
            } // for
            rt.ref[0] = ref[0];                                         // update LINKED LIST of nodes
            ref[0]    = rt;                                             // this -> rt -> old-right
            keys      = HALFP;                                          // reset number of active keys to help plus
            return rt;                                                  // (divider key (smallest right) in right sibling
        } // split

        /****************************************************************************
         * Split this INTERNAL node by creating a right sibling rt and moving half
         * the keys and references to that new node, leaving halfp - 1.
         * Return the divider key and the right sibling node.
         */
        Node splitI ()
        {
            var rt = new Node (HALF, false);                            // allocate internal right sibling node (rt)
            for (var i = 0; i < HALF; i++) {                            // move largest half of keys (with refs) to rt
                rt.key[i] = key[HALFP + i];
                rt.ref[i] = ref[HALFP + i];
            } // for
            rt.ref[HALF] = ref[keys];                                   // copy over the last ref
            keys = HALFP - 1;                                           // reset number of active keys to help plus - 1
            return rt;                                                  // divider key (middle key) in right sibling
        } // splitI

        /****************************************************************************
         * Convert this node to a string.
         */
        @Override
        public String toString ()
        {
            var sb = new StringBuilder ("[ . " );
            for (var i = 0; i < keys; i++) sb.append (STR."\{key[i]} . ");
            sb.append ("]");
            return sb.toString ();
        } // toString

        /****************************************************************************
         * Show the node's data structure.
         */
        void show ()
        {
            out.println (STR."isLeaf = \{isLeaf}");
            out.println (STR."keys   = \{keys}");
            out.println (STR."key    = \{Arrays.deepToString (key)}");
            out.println (STR."ref    = \{Arrays.deepToString (ref)}");
        } // show

        /****************************************************************************
         * Show the node's references.
         */
        void showRef ()
        {
            out.println (STR."ref = \{Arrays.deepToString (ref)}");
        } // showRef

    } // Node

//-----------------------------------------------------------------------------------
// Fields and constructors for B+Tree class
//-----------------------------------------------------------------------------------

    private Node root;                                                // root of the B+Tree
    private final Node firstLeaf;                                     // first (leftmost) leaf in the B+Tree

    private int count  = 0;                                           // counter for number nodes accessed (for performance testing)
    private int kCount = 0;                                           // counter for total number of keys in the B+Tree Map

    /********************************************************************************
     * Construct an empty B+Tree map.
     * @param _classK  the class for keys (K)
     * @param _classV  the class for values (V)
     */
    public BpTreeMap (Class <K> _classK, Class <V> _classV)
    {
        classK    = _classK;
        classV    = _classV;
        root      = new Node (0, true);                                // make an empty root
        firstLeaf = root;
    } // constructor

    /********************************************************************************
     * Return null to use the natural order based on the key type.  This requires the
     * key type to implement Comparable.
    public Comparator <? super K> comparator () 
    {
        return null;
    } // comparator
     */

    /********************************************************************************
     * Return the size (number of keys) in the B+Tree.
     * @return  the size of the B+Tree
     */
    public int size () { return kCount; }

//-----------------------------------------------------------------------------------
// Retrieve values or ranges (subtrees)
//-----------------------------------------------------------------------------------

    /********************************************************************************
     * Return a set containing all the entries as pairs of keys and values.
     * @return  the set view of the map
     */
    public Set <Map.Entry <K, V>> entrySet ()
    {
        var enSet = new HashSet <Map.Entry <K, V>> ();

        //  T O   B E   I M P L E M E N T E D
            
        return enSet;
    } // entrySet

    /********************************************************************************
     * Given the key, look up the value in the B+Tree map.
     * @param key  the key used for look up
     * @return  the value associated with the key or null if not found
     */
    @SuppressWarnings("unchecked")
    public V get (Object key) { return find ((K) key); }

    record NodePos (Object node, int pos) {}                          // as records are implicitly static, can't use 'Node node'

    /********************************************************************************
     * Find the given key in this B+tree and return its corresponding value.
     * Calls the recursive findp method.
     * @param key  the key to find
     */
    public V find (K key)
    {
        var np = findp (key, root);                                   // leaf node, index position
        return (np.pos >= 0) ? (V) ((Node) np.node).ref[np.pos+1]
                             : (V) null;
    } // find

    /********************************************************************************
     * Recursive helper method for finding the position of the given key in this B+tree.
     * @param key  the key to find
     * @param n    the current node
     */
    private NodePos findp (K key, Node n)
    {
        count += 1;
        return (n.isLeaf) ? new NodePos (n, n.findEq (key))
                          : findp (key, (Node) n.ref[n.find (key)]);
    } // findp

//-----------------------------------------------------------------------------------
// Put key-value pairs into the B+Tree
//-----------------------------------------------------------------------------------

    /********************************************************************************
     * Put the key-value pair in the B+Tree map.
     * @param key    the key to insert
     * @param value  the value to insert
     * @return  null, not the previous value for this key
     */
    public V put (K key, V value)
    {
        kCount += 1;
        insert (key, value, root);
        return null;
    } // put

    /********************************************************************************
     * Recursive helper function for inserting a key into a B+tree.
     * Add key-ref pair into node n and when it is full will split node n by
     * allocating a right sibling node rt and placing the lesser half of n's key in rt.
     * A split also will require a key-ref pair to be inserted at the next level up.
     * @param key  the key to insert
     * @param ref  the value/node to insert
     * @param n    the current node
     * @return  the newly allocated right sibling node of n 
     */
    @SuppressWarnings("unchecked")
    private Node insert (K key, V ref, Node n)
    {
        out.println ("=============================================================");
        out.println (STR."insert: key \{key}");
        out.println ("=============================================================");

        Node rt = null;                                               // holder right sibling node

        if (n.isLeaf) {                                               // handle LEAF node level
            rt = add (n, key, ref);
            if (rt != null) {
                if (n != root) return rt;
                root = new Node (root, rt.key[0], rt);                // make a new root
            } // if

        } else {                                                      // handle INTERNAL node level
            rt = insert (key, ref, (Node) n.ref[n.find (key)]);       // recursive call to insert
            if (DEBUG) out.println ("insert: handle internal node level");

                //  T O   B E   I M P L E M E N T E D

        } // if

        if (DEBUG) printT (root, 0);
        return rt;                                                    // return right sibling node
    } // insert

    /********************************************************************************
     * Add new key k and value v into LEAF node n.  Upon overflow, split node n,
     * in which case the new right sibling node (containing the divider key) is returned.
     * @param n  the current node
     * @param k  the new key
     * @param v  the new value
     */
    private Node add (Node n, K k, V v)
    {
        Node rt = null;                                               // holder for right sibling rt
        n.add (k, v);                                                 // add into node n
        if (n.overflow ()) rt = n.split ();                           // full => split into n and rt, divider key is rt.key[0]
        return rt;
    } // add

    /********************************************************************************
     * Add new key k and value v into INTERNAL node n.  Upon overflow, split node n,
     * in which case the new right sibling node (containing the divider key) is returned.
     * @param n  the current node
     * @param k  the new key
     * @param v  the new left value (ref a node)
     */
    private Node addI (Node n, K k, Node v)
    {
        Node rt = null;                                               // holder for right sibling rt

                //  T O   B E   I M P L E M E N T E D

        return rt;
    } // addI
    
//-----------------------------------------------------------------------------------
// Print/show the B+Tree
//-----------------------------------------------------------------------------------

    /********************************************************************************
     * Show/print this B+Tree.
     */
    void show ()
    {
        out.println ("BpTreeMap");
        printT (root, 0);
        out.println ("-".repeat (60));
    } // show

    /********************************************************************************
     * Print the B+Tree using a pre-order traversal and indenting each level.
     * @param n      the current node to print
     * @param level  the current level of the B+Tree
     */
    @SuppressWarnings("unchecked")
    private void printT (Node n, int level)
    {
        if (n != null) {
            out.println ("\t".repeat (level) + n);
            if (! n.isLeaf) {
                for (var j = 0; j <= n.keys; j++) printT ((Node) n.ref[j], level + 1);
            } // if
        } // if
    } // printT

//-----------------------------------------------------------------------------------
// Main method for running/testing the B+Tree
//-----------------------------------------------------------------------------------

    /********************************************************************************
     * The main method used for testing.  Also test for more keys and with RANDOMLY true.
     * @param  the command-line arguments (args[0] gives number of keys to insert)
     */
    public static void main (String [] args)
    {
        var totalKeys = 30;                    
        var RANDOMLY  = false;
        var bpt       = new BpTreeMap <Integer, Integer> (Integer.class, Integer.class);
        if (args.length == 1) totalKeys = Integer.valueOf (args[0]);
   
        if (RANDOMLY) {
            Random rng = new Random ();
            for (var i = 1; i <= totalKeys; i += 2) bpt.put (rng.nextInt (2 * totalKeys), i * i);
        } else {
            for (var i = 1; i <= totalKeys; i += 2) bpt.put (i, i * i);
        } // if

        bpt.printT (bpt.root, 0);
        for (var i = 0; i <= totalKeys; i++) {
            out.println (STR."key = \{i}, value = \{bpt.get (i)}");
        } // for
        out.println ("-------------------------------------------");
        out.println (STR."number of keys in BpTree = \{bpt.kCount}");
        out.println ("-------------------------------------------");
        out.println ("Average number of nodes accessed = " + bpt.count / (double) totalKeys);
    } // main

} // BpTreeMap

