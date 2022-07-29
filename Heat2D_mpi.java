import java.util.Date;
import mpi.*;

public class Heat2D_mpi {
    private static double a = 1.0;		  // heat speed
    private static double dt = 1.0;		  // time quantum
    private static double dd = 2.0;		  // change in system
	private final static int master = 0;  // the master rank
	private final static int tag = 0;	  // Send/Recv's tag is always 0.
	private static Date startTime;				  // Date startTime for master
    
    public static void main( String[] args ) throws MPIException {
	// Start the MPI library
	MPI.Init( args );

	// verify arguments
	if ( args.length != 4 ) {
	    System.out.
		println( "usage: " + 
			 "java Heat2D size max_time heat_time interval" );
	    System.exit( -1 );
	}

	int size = Integer.parseInt( args[0] );
	int max_time = Integer.parseInt( args[1] );
	int heat_time = Integer.parseInt( args[2] );
	int interval  = Integer.parseInt( args[3] );
	double r = a * dt / ( dd * dd );

	// compute my own stripe
	int stripe = size / MPI.COMM_WORLD.Size(); // each portion of array
	double[] z = null;

	// create a space
	z = new double[2 * size * size];
	for ( int p = 0; p < 2; p++ ) 
		for ( int x = 0; x < size; x++ )
		for ( int y = 0; y < size; y++ )
			z[p * size * size + x * size + y] = 0.0; // no heat or cold

	// start a timer
	if ( MPI.COMM_WORLD.Rank( ) == 0 )
		startTime = new Date( );

		

	// simulate heat diffusion
	for ( int t = 0; t < max_time; t++ ) {
	    int p = t % 2; // p = 0 or 1: indicates the phase
	    
	    // two left-most and two right-most columns are identical
	    for ( int y = 0; y < size; y++ ) {
			z[p * size * size + 0 * size + y] = 
					z[p * size * size + 1 * size + y];
			z[p * size * size + (size - 1) * size + y] = 
					z[p * size * size + (size - 2) * size + y];
	    }
	    
	    // two upper and lower rows are identical
	    for ( int x = 0; x < size; x++ ) {
			z[p * size * size + x * size + 0] = 
					z[p * size * size + x * size + 1];
			z[p * size * size + x * size + size - 1] = 
					z[p * size * size + x * size + size - 2];
	    }
	    
	    // keep heating the bottom until t < heat_time
	    if ( t < heat_time ) {
		for ( int x = size /3; x < size / 3 * 2; x++ )
			z[p * size * size + x * size + 0] = 19.0; // heat
	    }

		if (MPI.COMM_WORLD.Size() > 1) { // skip this if there are no slaves
			// Exchange boundary data between two neighboring ranks
			int rank = MPI.COMM_WORLD.Rank( );
			if ( rank == 0 ) { // leftmost stripe
				// Send right boundary to rank 1
				MPI.COMM_WORLD.Send(z, p * size * size + (stripe - 1) * size, 
									size, MPI.DOUBLE, 1, tag);
				
				// Receive left boundary of rank 1
				MPI.COMM_WORLD.Recv(z, p * size * size + stripe * size, size, 
									MPI.DOUBLE, 1, tag);

			} else if ( rank == MPI.COMM_WORLD.Size() - 1) { // rightmost stripe
				if (rank % 2 == 0) {
					// Send left boundary to rank (rank - 1)
					MPI.COMM_WORLD.Send(z, p * size * size 
										+ (rank * stripe) * size, size, 
										MPI.DOUBLE, rank - 1, tag);
					// Receive right boundary of rank (rank - 1)
					MPI.COMM_WORLD.Recv(z, p * size * size 
										+ (stripe * rank - 1) * size, size, 
										MPI.DOUBLE, rank - 1, tag);
				} else {
					// Receive right boundary of rank (rank - 1)
					MPI.COMM_WORLD.Recv(z, p * size * size 
										+ (stripe * rank - 1) * size, size, 
										MPI.DOUBLE, rank - 1, tag);
					// Send left boundary to rank (rank - 1)
					MPI.COMM_WORLD.Send(z, p * size * size 
										+ (rank * stripe) * size, size, 
										MPI.DOUBLE, rank - 1, tag);
				}
			} else { // has left and right neighbours
				if (rank % 2 == 0) {
					// Send to right
					MPI.COMM_WORLD.Send(z, p * size * size 
										+ (rank * stripe  + stripe - 1) * size, 
										size, MPI.DOUBLE, rank + 1, tag);
					// Send to left
					MPI.COMM_WORLD.Send(z, p * size * size 
										+ (rank * stripe) * size, 
										size, MPI.DOUBLE, rank - 1, tag);
					// Receive from left
					MPI.COMM_WORLD.Recv(z, p * size * size 
										+ (stripe * rank - 1) * size, 
										size, MPI.DOUBLE, rank - 1, tag);
					// Receive from right
					MPI.COMM_WORLD.Recv(z, p * size * size 
										+ (rank * stripe + stripe) * size, 
										size, MPI.DOUBLE, rank + 1, tag);
				} else {
					// Receive from left
					MPI.COMM_WORLD.Recv(z, p * size * size 
										+ (stripe * rank - 1) * size, 
										size, MPI.DOUBLE, rank - 1, tag);
					// Receive from right
					MPI.COMM_WORLD.Recv(z, p * size * size 
										+ (rank * stripe + stripe) * size, 
										size, MPI.DOUBLE, rank + 1, tag);
					// Send to right
					MPI.COMM_WORLD.Send(z, p * size * size 
										+ (rank * stripe  + stripe - 1) * size, 
										size, MPI.DOUBLE, rank + 1, tag);
					// Send to left
					MPI.COMM_WORLD.Send(z, p * size * size 
										+ (rank * stripe) * size, 
										size, MPI.DOUBLE, rank - 1, tag);
				}
				
			}
		}

		// display intermediate results
		if ( interval != 0 && 
		( t % interval == 0 || t == max_time - 1 ) ) {
			if ( MPI.COMM_WORLD.Rank( ) == 0 ) { // master
				if (MPI.COMM_WORLD.Size() > 1) {
					// Master collects all stripes to display intermediate status
					for (int rank = 1; rank < MPI.COMM_WORLD.Size(); rank++) {
						MPI.COMM_WORLD.Recv(z, p * size * size 
											+ (rank * stripe) * size, 
											stripe * size, MPI.DOUBLE, 
											rank, tag);
					}
				}
				System.out.println( "time = " + t );
				for ( int y = 0; y < size; y++ ) {
					for ( int x = 0; x < size; x++ )
					System.out.print( (int)( Math.floor(z[p * size * size 
														+ x * size + y] / 2) ) 
									+ " " );
					System.out.println( );
				}
				System.out.println( );
			} else {
				int rank = MPI.COMM_WORLD.Rank( );
				MPI.COMM_WORLD.Send(z, p * size * size + (rank * stripe) * size, 
									stripe * size, MPI.DOUBLE, master, tag);
			}
		} 
	    
	    // perform forward Euler method
		int rank = MPI.COMM_WORLD.Rank( );
	    int p2 = (p + 1) % 2;
		int x, xLimit;
		if (MPI.COMM_WORLD.Size() == 1) {
			x = 1;
			xLimit = size - 1;
		} else {
		if (rank == 0) {
			x = 1;
			xLimit = stripe;
		} else if (rank == MPI.COMM_WORLD.Size() - 1) {
			x = stripe * rank;
			xLimit = size - 1;
		} else {
			x = stripe * rank;
			xLimit = stripe * rank + stripe;
		}
		}	
	    for ( ; x < xLimit; x++ )
		for ( int y = 1; y < size - 1; y++ )
		z[p2 * size * size + x * size + y] = 
			z[p * size * size + x * size + y] + 
			r * ( z[p * size * size + (x + 1) * size + y] - 
			2 * z[p * size * size + x * size + y] +
			z[p * size * size + (x - 1) * size + y] ) +
			r * ( z[p * size * size + x * size + y + 1] - 
			2 * z[p * size * size + x * size + y] + 
			z[p * size * size + x * size + y - 1] );
	} // end of simulation
	
	// finish the timer
	if ( MPI.COMM_WORLD.Rank( ) == 0 ) {
	    Date endTime = new Date( );
	    System.out.println( "Elapsed time = " + 
				( endTime.getTime( ) - startTime.getTime( ) ) );
	}
	
	// Terminate the MPI library.
	MPI.Finalize( );
    }
}
