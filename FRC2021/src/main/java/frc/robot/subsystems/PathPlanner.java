package frc.robot.subsystems;

import java.util.LinkedList;
import java.util.List;

import frc.robot.Constants;

public class PathPlanner {

    //Path Variables
	public double[][] mOrigPath;
	public double[][] mNodeOnlyPath;
	public double[][] mSmoothPath;
	public double[][] mLeftPath;
	public double[][] mRightPath;

	//Orig Velocity
	public double[][] mOrigCenterVelocity;
	public double[][] mOrigRightVelocity;
	public double[][] mOrigLeftVelocity;

	//smooth velocity
	public double[][] mSmoothCenterVelocity;
	public double[][] mSmoothRightVelocity;
	public double[][] mSmoothLeftVelocity;

	//accumulated heading
	public double[][] mHeading;

	double mNumFinalPoints;

	double mPathAlpha;
	double mPathBeta;
	double mPathTolerance;

	double mVelocityAlpha;
	double mVelocityBeta;
	double mVelocityTolerance;    
    
    public PathPlanner(double[][] path) {
        
		this.mOrigPath = doubleArrayCopy(path);

		//default values DO NOT MODIFY;
		mPathAlpha = Constants.kPathAlpha;
		mPathBeta = Constants.kPathBeta;
		mPathTolerance = Constants.kPathTolerance;

		mVelocityAlpha = Constants.kVelocityAlpha;
		mVelocityBeta = Constants.kVelocityBeta;
		mVelocityTolerance = Constants.kVelocityTolerance;

    }

    // prints out as column vectors for X  Y                    note to mar: smart dashboard?
    public static void print(double[][] path) {
		System.out.println("X: \t Y:");

		for(double[] u: path)
			System.out.println(u[0] + "\t" + u[1]);
    }
    
    // Performs a deep copy of a 2 Dimensional Array looping through each element in the 2D array
    public static double[][] doubleArrayCopy(double[][] arr) {

		//size first dimension of array
		double[][] temp = new double[arr.length][arr[0].length];

		for(int i=0; i<arr.length; i++) {
			//Resize second dimension of array
			temp[i] = new double[arr[i].length];

			//Copy Contents
			for(int j=0; j<arr[i].length; j++) {
				temp[i][j] = arr[i][j];
			}
		}

		return temp;

	}
	
	// converts feet per minute to wheel rotations per minute
	public double[][] convertArray(double[][] array) {

		double temp[][] = new double[array.length][2];

		for (int i=0; i<array.length; i++) {
			temp[i][0] = array[i][0];
			temp[i][1] = ((array[i][1]) * Constants.kDriveTicksPerFoot * Constants.kTalonUpdateTime); // .1 = sec to ms 
		}

		return temp;
	}
    
    public double[][] inject(double[][] orig, int numToInject) {
		double morePoints[][];

		//create extended 2 Dimensional array to hold additional points
		morePoints = new double[orig.length + ((numToInject)*(orig.length-1))][2];

		int index = 0;

		//loop through original array
		for(int i=0; i<orig.length-1; i++)
		{
			//copy first
			morePoints[index][0] = orig[i][0];
			morePoints[index][1] = orig[i][1];
			index++;

			for(int j=1; j<numToInject+1; j++)
			{
				//calculate intermediate x points between j and j+1 original points
				morePoints[index][0] = j*((orig[i+1][0]-orig[i][0])/(numToInject+1))+orig[i][0];

				//calculate intermediate y points  between j and j+1 original points
				morePoints[index][1] = j*((orig[i+1][1]-orig[i][1])/(numToInject+1))+orig[i][1];

				index++;
			}
		}

		//copy last
		morePoints[index][0] = orig[orig.length-1][0];
		morePoints[index][1] = orig[orig.length-1][1];
		index++;

		return morePoints;
    }
    
    // Calculating method that smooths out paths to create a smooth trajectory using gradients
    public double[][] smoother(double[][] path, double weight_data, double weight_smooth, double tolerance) {

		//copy array
		double[][] newPath = doubleArrayCopy(path);

		double change = tolerance;
		while(change >= tolerance)
		{
			change = 0.0;
			for(int i=1; i<path.length-1; i++){
				for(int j=0; j<path[i].length; j++)
				{
					double aux = newPath[i][j];
					newPath[i][j] += weight_data * (path[i][j] - newPath[i][j]) + weight_smooth * (newPath[i-1][j] + newPath[i+1][j] - (2.0 * newPath[i][j]));
					change += Math.abs(aux - newPath[i][j]);	
				}	
			}				
		}

		return newPath;

    }
    
    // transposes points into nodes that change direction -> needed for vectors direction
    public static double[][] nodeOnlyWayPoints(double[][] path) {

		List<double[]> li = new LinkedList<double[]>();

		//save first value
		li.add(path[0]);

		//find intermediate nodes
		for(int i=1; i<path.length-1; i++) {
			//calculate direction
			double vector1 = Math.atan2(path[i][1]-path[i-1][1], path[i][0]-path[i-1][0]);
			double vector2 = Math.atan2(path[i+1][1]-path[i][1], path[i+1][0]-path[i][0]);
       
			//determine if both vectors have a change in direction
			if(Math.abs(vector2-vector1)>=0.01) {
				li.add(path[i]);				
			}  	
		}

		//save last
		li.add(path[path.length-1]);

		//re-write nodes into new 2D Array
		double[][] temp = new double[li.size()][2];

		for (int i=0; i<li.size(); i++) {
			temp[i][0] = li.get(i)[0];
			temp[i][1] = li.get(i)[1];
		}	

		return temp;
    }
    
    // turns smooth position arrays into velocity vector arrays
    double[][] velocity(double[][] smoothPath, double timeStep) {
		double[] dxdt = new double[smoothPath.length];
		double[] dydt = new double[smoothPath.length];
		double[][] velocity = new double[smoothPath.length][2];

		//set first instance to zero
		dxdt[0]=0;
		dydt[0]=0;
		velocity[0][0]=0;
		velocity[0][1]=0;
		mHeading[0][1]=0;

		for(int i=1; i<smoothPath.length; i++) {
			dxdt[i] = (smoothPath[i][0]-smoothPath[i-1][0])/timeStep;
			dydt[i] = (smoothPath[i][1]-smoothPath[i-1][1])/timeStep;

			//create time vector
			velocity[i][0] = velocity[i-1][0]+timeStep;
			mHeading[i][0] = mHeading[i-1][0]+timeStep;

			//calculate velocity
			velocity[i][1] = Math.sqrt(Math.pow(dxdt[i],2) + Math.pow(dydt[i],2));
		}


		return velocity;

    }
    
    // optimize velocity by minimizing the error distance at the end of travel
	// when this function converges, the fixed velocity vector will be smooth, start
	// and end with 0 velocity, and travel the same final distance as the original
	// un-smoothed velocity profile
	double[][] velocityFix(double[][] smoothVelocity, double[][] origVelocity, double tolerance) {
		//calculate error difference
		double[] difference = errorSum(origVelocity,smoothVelocity);


		//copy smooth velocity into new Vector
		double[][] fixVel = new double[smoothVelocity.length][2];

		for (int i=0; i<smoothVelocity.length; i++) {
			fixVel[i][0] = smoothVelocity[i][0];
			fixVel[i][1] = smoothVelocity[i][1];
		}

		double increase = 0.0;
		while (Math.abs(difference[difference.length-1]) > tolerance) {
			increase = difference[difference.length-1]/1/50;

			for(int i=1;i<fixVel.length-1; i++) {
				fixVel[i][1] = fixVel[i][1] - increase;
            }

			difference = errorSum(origVelocity,fixVel);
		}

		//fixVel =  smoother(fixVel, 0.001, 0.001, 0.0000001);
		return fixVel;

	}	

	// compares integral of smooth velocity versus orignial velocity to make sure
	// that the smooth velocity is still covering the distance wanted
	private double[] errorSum(double[][] origVelocity, double[][] smoothVelocity) {
		
		//copy vectors
		double[] tempOrigDist = new double[origVelocity.length];
		double[] tempSmoothDist = new double[smoothVelocity.length];
		double[] difference = new double[smoothVelocity.length];


		double timeStep = origVelocity[1][0]-origVelocity[0][0];

		//copy first elements
		tempOrigDist[0] = origVelocity[0][1];
		tempSmoothDist[0] = smoothVelocity[0][1];


		//calculate difference
		for (int i=1; i<origVelocity.length; i++) {
			tempOrigDist[i] = origVelocity[i][1]*timeStep + tempOrigDist[i-1];
			tempSmoothDist[i] = smoothVelocity[i][1]*timeStep + tempSmoothDist[i-1];

			difference[i] = tempSmoothDist[i]-tempOrigDist[i];

		}

		return difference;
	}

	// calculates parameters to find the optimal number of nodes to inject in the path
	public int[] injectionCounter2Steps(double numNodeOnlyPoints, double maxTimeToComplete, double timeStep) {
		int first = 0;
		int second = 0;
		int third = 0;

		double oldPointsTotal = 0;

		mNumFinalPoints = 0;

		int[] ret = null;

		double totalPoints = maxTimeToComplete/timeStep;

		if (totalPoints < 100) {
			double pointsFirst = 0;
			double pointsTotal = 0;


			for (int i=4; i<=6; i++) {
				for (int j=1; j<=8; j++) {
					pointsFirst = (i * (numNodeOnlyPoints - 1) + numNodeOnlyPoints);
					pointsTotal = (j * (pointsFirst - 1) + pointsFirst);

					if(pointsTotal<=totalPoints && pointsTotal>oldPointsTotal) {
						first = i;
						second = j;
						mNumFinalPoints = pointsTotal;
						oldPointsTotal = pointsTotal;
					}
				}
            }
            
			ret = new int[] {first, second, third};
		}
		else {

			double pointsFirst = 0;
			double pointsSecond = 0;
			double pointsTotal = 0;

			for (int i=1; i<=5; i++) {
				for (int j=1; j<=8; j++) {
					for (int k=1; k<8; k++) {
						pointsFirst = i *(numNodeOnlyPoints-1) + numNodeOnlyPoints;
						pointsSecond = (j*(pointsFirst-1)+pointsFirst);
						pointsTotal =  (k*(pointsSecond-1)+pointsSecond);

						if(pointsTotal <= totalPoints) {
							first = i;
							second = j;
							third = k;
							mNumFinalPoints = pointsTotal;
						}
					}
				}
			}
			ret = new int[] {first, second, third};
		}


		return ret;
	}

	// calculates left and right smooth wheel paths based on the robot track width
	public void leftRight(double[][] smoothPath, double robotTrackWidth) {

		double[][] leftPath = new double[smoothPath.length][2];
		double[][] rightPath = new double[smoothPath.length][2];

		double[][] gradient = new double[smoothPath.length][2];

		for(int i = 0; i<smoothPath.length-1; i++){
			gradient[i][1] = Math.atan2(smoothPath[i+1][1] - smoothPath[i][1],smoothPath[i+1][0] - smoothPath[i][0]);
		}

		gradient[gradient.length-1][1] = gradient[gradient.length-2][1];


		for (int i=0; i<gradient.length; i++) {
			leftPath[i][0] = (robotTrackWidth/2 * Math.cos(gradient[i][1] + Math.PI/2)) + smoothPath[i][0];
			leftPath[i][1] = (robotTrackWidth/2 * Math.sin(gradient[i][1] + Math.PI/2)) + smoothPath[i][1];

			rightPath[i][0] = (robotTrackWidth/2 * Math.cos(gradient[i][1] - Math.PI/2)) + smoothPath[i][0];
			rightPath[i][1] = robotTrackWidth/2 * Math.sin(gradient[i][1] - Math.PI/2) + smoothPath[i][1];

			//convert to degrees 0 to 360 where 0 degrees is +X - axis, accumulated to aline with WPI sensor
			double deg = Math.toDegrees(gradient[i][1]);

			gradient[i][1] = deg;

			if(i>0) {
				if((deg-gradient[i-1][1])>180) {
					gradient[i][1] = -360+deg;
				}

				if((deg-gradient[i-1][1])<-180) {
					gradient[i][1] = 360+deg;
				}
			}
		}

		this.mHeading = gradient;
		this.mLeftPath = leftPath;
		this.mRightPath = rightPath;
	}

	// returns first column of a 2d array of doubles
	public static double[] getXVector(double[][] arr) {
		double[] temp = new double[arr.length];

		for(int i=0; i<temp.length; i++) {
			temp[i] = arr[i][0];
		}

		return temp;		
	}

	// returns the second column of a 2d array of doubles
	public static double[] getYVector(double[][] arr) {
		double[] temp = new double[arr.length];

		for(int i=0; i<temp.length; i++) {
			temp[i] = arr[i][1];
		}

		return temp;		
	}

	public static double[][] transposeVector(double[][] arr) {
		double[][] temp = new double[arr[0].length][arr.length];

		for(int i=0; i<temp.length; i++){
			for(int j=0; j<temp[i].length; j++) {
				temp[i][j] = arr[j][i];
			}
		}

		return temp;		
	}

	public void setPathAlpha(double alpha) {
		mPathAlpha = alpha;
	}

	public void setPathBeta(double beta) {
		mPathAlpha = beta;
	}

	public void setPathTolerance(double tolerance) {
		mPathAlpha = tolerance;
	}

	// calculates a smooth path based on program parameters -> after this program is run, 
	//  all you need to call is .smoothVelocity[0] and .smoothVelocity[1] to the left and
	//  right motor controllers
	public void calculate(double totalTime, double timeStep, double robotTrackWidth) {

		//first find only direction changing nodes
		mNodeOnlyPath = nodeOnlyWayPoints(mOrigPath);

		//Figure out how many nodes to inject
		int[] inject = injectionCounter2Steps(mNodeOnlyPath.length, totalTime, timeStep);

		//iteratively inject and smooth the path
		for(int i=0; i<inject.length; i++) {
			if(i==0) {
				mSmoothPath = inject(mNodeOnlyPath, inject[0]);
				mSmoothPath = smoother(mSmoothPath, mPathAlpha, mPathBeta, mPathTolerance);	
			}
			else {
				mSmoothPath = inject(mSmoothPath,inject[i]);
				mSmoothPath = smoother(mSmoothPath, 0.1, 0.3, 0.0000001);	
			}
		}

		//calculate left and right path based on center path
		leftRight(mSmoothPath, robotTrackWidth);

		mOrigCenterVelocity = velocity(mSmoothPath, timeStep);
		mOrigLeftVelocity = velocity(mLeftPath, timeStep);
		mOrigRightVelocity = velocity(mRightPath, timeStep);

		//copy smooth velocities into fix Velocities
		mSmoothCenterVelocity =  doubleArrayCopy(mOrigCenterVelocity);
		mSmoothLeftVelocity =  doubleArrayCopy(mOrigLeftVelocity);
		mSmoothRightVelocity =  doubleArrayCopy(mOrigRightVelocity);

		//set final vel to zero
		mSmoothCenterVelocity[mSmoothCenterVelocity.length-1][1] = 0.0;
		mSmoothLeftVelocity[mSmoothLeftVelocity.length-1][1] = 0.0;
		mSmoothRightVelocity[mSmoothRightVelocity.length-1][1] = 0.0;

		//Smooth velocity with zero final V
		mSmoothCenterVelocity = smoother(mSmoothCenterVelocity, mVelocityAlpha, mVelocityBeta, mVelocityTolerance);
		mSmoothLeftVelocity = smoother(mSmoothLeftVelocity, mVelocityAlpha, mVelocityBeta, mVelocityTolerance);
		mSmoothRightVelocity = smoother(mSmoothRightVelocity,mVelocityAlpha, mVelocityBeta, mVelocityTolerance);

		//fix velocity distance error
		mSmoothCenterVelocity = velocityFix(mSmoothCenterVelocity, mOrigCenterVelocity, 0.0000001);
		mSmoothLeftVelocity = velocityFix(mSmoothLeftVelocity, mOrigLeftVelocity, 0.0000001);
		mSmoothRightVelocity = velocityFix(mSmoothRightVelocity, mOrigRightVelocity, 0.0000001);
		
		// convert to wheel rotations
		mSmoothCenterVelocity = convertArray(mSmoothCenterVelocity);
		mSmoothLeftVelocity = convertArray(mSmoothLeftVelocity);
		mSmoothRightVelocity = convertArray(mSmoothRightVelocity);

	}
	
}