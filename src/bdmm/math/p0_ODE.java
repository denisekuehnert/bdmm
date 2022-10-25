package bdmm.math;

import org.apache.commons.math3.ode.FirstOrderDifferentialEquations;
import org.apache.commons.math3.ode.FirstOrderIntegrator;
import org.apache.commons.math3.ode.nonstiff.DormandPrince853Integrator;

import bdmm.core.util.Utils;


/**
 * @author dkuh004
 *         Date: May 24, 2012
 *         Time: 6:42:00 PM
 */

public class p0_ODE implements FirstOrderDifferentialEquations {

	double[] b;
	Double[] b_ij;
	double[] d;
	Double[] s;

	Double[] M;

	int dimension;
	int intervals;
	Double[] times;
	int index;

	public p0_ODE(double[] b, Double[] b_ij, double[] d, Double[] s, Double[] M, int dimension , int intervals, Double[] times) {

		this.b = b;
		this.b_ij = b_ij;
		this.d = d;
		this.s = s;
		this.M = M;
		this.dimension = dimension;
		this.intervals = intervals;

		this.times = times;

	}

	// updateRates is not used here because a new p0_ODE is created each time PiecewiseBirthDeathMigrationDistribution.updateRates() is called (called through setUpIntegrators())
	public void updateRates(double[] b, Double[] b_ij, double[] d, Double[] s, Double[] M, Double[] times){

		this.b = b;
		this.b_ij = b_ij;
		this.d = d;
		this.s = s;
		this.M = M;
		this.times = times;

	}

	public int getDimension() {
		return this.dimension;
	}

	public void computeDerivatives(double t, double[] y, double[] yDot) {

		index = Utils.index(t, times, intervals); //finds the indexTimeInterval of the time interval t lies in
		int k, l;

		for (int i = 0; i<dimension; i++){

			k = i*intervals + index;

			yDot[i] = + (b[k]+d[k]+s[k])*y[i] - d[k] - b[k]*y[i]*y[i] ;

			for (int j=0; j<dimension; j++){

				l = (i*(dimension-1)+(j<i?j:j-1))*intervals + index;

				if (i!=j){

					if (b_ij!=null){     // infection among demes

						yDot[i] += b_ij[l]*y[i]; 
						yDot[i] -= b_ij[l]*y[i]*y[j];
					}

					if (M[0]!=null) {// migration:
						yDot[i] += M[l] * y[i];
						yDot[i] -= M[l] * y[j];
					}
				}
			}
		}

	}

	/**
	 * Some basic tests for numerical integration with this class
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception{
		
		// 2d test
		double[] b = {1.03,1.06};
		double[] d = {1.,1.};
		Double[] s = {0.02,0.04};
		Double[] M = new Double[]{3.,4.};

		FirstOrderIntegrator integrator = new DormandPrince853Integrator(1.0e-8, 100.0, 1.0e-20, 1.0e-9);//new ClassicalRungeKuttaIntegrator(.01); //
		FirstOrderDifferentialEquations ode = new p0_ODE(b,null,d,s,M, 2, 1, new Double[]{0.});
		double[] y0 = new double[]{1.,1.};
		double[] y = new double[2];

		//        StepHandler stepHandler = new StepHandler() {
		//            public void init(double t0, double[] y0, double t) {
		//            }
		//
		//            public void handleStep(StepInterpolator interpolator, boolean isLast) {
		//                double   t = interpolator.getCurrentTime();
		//                double[] y = interpolator.getInterpolatedState();
		//                System.out.println(t + " " + y[0] + " " + y[1]);
		//            }
		//        };
		//        integrator.addStepHandler(stepHandler);
		//
		//
		integrator.integrate(ode, 10, y0, 1, y);

		System.out.println("Solution: " +y[0]+" "+y[1]);
		//
		//
		//        // 3d test
		//        Double[] birth = {1.03,1.06, 1.5};
		//        Double[] death = {1.,1., 1.2};
		//        Double[] sampling = {0.02,0.04, 0.1};
		//        Double[] migration = {3., 1.,4.,1.,2., 2.};
		//
		//        FirstOrderIntegrator integrator = new DormandPrince853Integrator(1.0e-8, 100.0, 1.0e-10, 1.0e-10);//new ClassicalRungeKuttaIntegrator(.01); //
		//        FirstOrderDifferentialEquations ode = new p0_ODE(birth,death,sampling,migration, 3);
		//        double[] y0 = new double[]{1.,1.,1.};
		//        double[] y = new double[3];
		//
		//        integrator.integrate(ode, 10, y0, 1, y);
		//
		//        System.out.println("Solution: " +y[0]+" "+y[1]+" "+y[2]);
	}

}


