
/*****************************************************************************************
 * @file  MovieDB.java
 *
 * @author   John Miller
 */

import static java.lang.System.out;

/*****************************************************************************************
 * The MovieDB2 class loads the Movie Database.
 */
class MovieDB2
{
    /*************************************************************************************
     * Main method for loading a previously saved Movie Database.
     * @param args  the command-line arguments
     */
    public static void main (String [] args)
    {
        out.println ();

        var movie     = Table.load ("movie");
        var cinema    = Table.load ("cinema");
        var movieStar = Table.load ("movieStar");
        var starsIn   = Table.load ("starsIn");
        var movieExec = Table.load ("movieExec");
        var studio    = Table.load ("studio");

        movie.print ();
        cinema.print ();
        movieStar.print ();
        starsIn.print ();
        movieExec.print ();
        studio.print ();

    } // main

} // MovieDB2

