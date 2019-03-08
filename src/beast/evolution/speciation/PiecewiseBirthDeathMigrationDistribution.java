package beast.evolution.speciation;

import beast.core.*;
import beast.core.parameter.BooleanParameter;
import beast.core.parameter.RealParameter;
import beast.core.util.Utils;
import beast.evolution.tree.Node;
import beast.evolution.tree.TreeInterface;
import beast.math.ScaledNumbers;
import beast.math.SmallNumber;
import beast.math.SmallNumberScaler;
import beast.math.p0_ODE;
import beast.math.p0ge_InitialConditions;
import beast.math.p0ge_ODE;
import beast.util.HeapSort;
import org.apache.commons.math3.ode.FirstOrderIntegrator;
import org.apache.commons.math3.ode.nonstiff.ClassicalRungeKuttaIntegrator;
import org.apache.commons.math3.ode.nonstiff.DormandPrince54Integrator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;


/**
 * Created with IntelliJ IDEA.
 * User: Denise
 * Date: 22.08.14
 * Time: 14:05
 */
@Citation(value="Kuehnert D, Stadler T, Vaughan TG, Drummond AJ. (2016). " +
		"Phylodynamics with migration: \n" +
		"A computational framework to quantify population structure from genomic data. \n" +
		"Mol Biol Evol. 33(8):2102–2116."
		, DOI= "10.1093/molbev/msw064", year = 2016, firstAuthorSurname = "Kuehnert")

@Description("Piece-wise constant rates are assumed to be ordered by state and time. First k entries of an array give " +
		"values belonging to type 1, for intervals 1 to k, second k intervals for type 2 etc.")
public abstract class PiecewiseBirthDeathMigrationDistribution extends SpeciesTreeDistribution {


	public Input<RealParameter> frequencies =
			new Input<>("frequencies", "The frequencies for each type",  Input.Validate.REQUIRED);

	public Input<RealParameter> origin =
			new Input<>("origin", "The origin of infection x1");

	public Input<Boolean> originIsRootEdge =
			new Input<>("originIsRootEdge", "The origin is only the length of the root edge", false);

	public Input<Integer> maxEvaluations =
			new Input<>("maxEvaluations", "The maximum number of evaluations for ODE solver", 1000000);

	public Input<Boolean> conditionOnSurvival =
			new Input<>("conditionOnSurvival", "condition on at least one survival? Default true.", true);

	public Input<Double> relativeTolerance =
			new Input<>("relTolerance", "relative tolerance for numerical integration", 1e-7);

	public Input<Double> absoluteTolerance =
			new Input<>("absTolerance", "absolute tolerance for numerical integration", 1e-100 /*Double.MIN_VALUE*/);

	// the interval times for the migration rates
	public Input<RealParameter> migChangeTimesInput =
			new Input<>("migChangeTimes", "The times t_i specifying when migration rate changes occur", (RealParameter) null);

	// the interval times for the birth rate
	public Input<RealParameter> birthRateChangeTimesInput =
			new Input<>("birthRateChangeTimes", "The times t_i specifying when birth/R rate changes occur", (RealParameter) null);

	// the interval times for the birth rate among demes
	public Input<RealParameter> b_ijChangeTimesInput =
			new Input<>("birthRateAmongDemesChangeTimes", "The times t_i specifying when birth/R among demes changes occur", (RealParameter) null);

	// the interval times for the death rate
	public Input<RealParameter> deathRateChangeTimesInput =
			new Input<>("deathRateChangeTimes", "The times t_i specifying when death/becomeUninfectious rate changes occur", (RealParameter) null);

	// the interval times for sampling rate
	public Input<RealParameter> samplingRateChangeTimesInput =
			new Input<>("samplingRateChangeTimes", "The times t_i specifying when sampling rate or sampling proportion changes occur", (RealParameter) null);

	// the interval times for removal probability
	public Input<RealParameter> removalProbabilityChangeTimesInput =
			new Input<RealParameter>("removalProbabilityChangeTimes", "The times t_i specifying when removal probability changes occur", (RealParameter) null);

	public Input<RealParameter> intervalTimes =
			new Input<>("intervalTimes", "The time t_i for all parameters if they are the same", (RealParameter) null);

	public Input<Boolean> migTimesRelativeInput =
			new Input<>("migTimesRelative", "True if migration rate change times specified relative to tree height? Default false", false);

	public Input<Boolean> b_ijChangeTimesRelativeInput =
			new Input<>("birthRateAmongDemesTimesRelative", "True if birth rate change times specified relative to tree height? Default false", false);

	public Input<Boolean> birthRateChangeTimesRelativeInput =
			new Input<>("birthRateTimesRelative", "True if birth rate change times specified relative to tree height? Default false", false);

	public Input<Boolean> deathRateChangeTimesRelativeInput =
			new Input<>("deathRateTimesRelative", "True if death rate change times specified relative to tree height? Default false", false);

	public Input<Boolean> samplingRateChangeTimesRelativeInput =
			new Input<>("samplingRateTimesRelative", "True if sampling rate times specified relative to tree height? Default false", false);

	Input<Boolean> removalProbabilityChangeTimesRelativeInput =
			new Input<Boolean>("removalProbabilityTimesRelative", "True if removal probability change times specified relative to tree height? Default false", false);

	public Input<BooleanParameter> reverseTimeArraysInput =
			new Input<>("reverseTimeArrays", "True if the time arrays are given in backwards time (from the present back to root). Order: 1) birth 2) death 3) sampling 4) rho 5) r 6) migration. Default false." +
					"Careful, rate array must still be given in FORWARD time (root to tips).");

	// the times for rho sampling
	public Input<RealParameter> rhoSamplingTimes =
			new Input<>("rhoSamplingTimes", "The times t_i specifying when rho-sampling occurs", (RealParameter) null);
	public Input<Boolean> contemp =
			new Input<>("contemp", "Only contemporaneous sampling (i.e. all tips are from same sampling time, default false)", false);

	public Input<Function> birthRate =
			new Input<>("birthRate", "BirthRate = BirthRateVector * birthRateScalar, birthrate can change over time");
	public Input<Function> deathRate =
			new Input<>("deathRate", "The deathRate vector with birthRates between times");
	public Input<RealParameter> samplingRate =
			new Input<>("samplingRate", "The sampling rate per individual");      // psi

	public Input<RealParameter> m_rho =
			new Input<>("rho", "The proportion of lineages sampled at rho-sampling times (default 0.)");


	public Input<RealParameter> R0 =
			new Input<>("R0", "The basic reproduction number");
	public Input<RealParameter> becomeUninfectiousRate =
			new Input<>("becomeUninfectiousRate", "Rate at which individuals become uninfectious (through recovery or sampling)", Input.Validate.XOR, deathRate);
	public Input<RealParameter> samplingProportion =
			new Input<>("samplingProportion", "The samplingProportion = samplingRate / becomeUninfectiousRate", Input.Validate.XOR, samplingRate);

	public Input<BooleanParameter> identicalRatesForAllTypesInput =
			new Input<>("identicalRatesForAllTypes", "True if all types should have the same 1) birth 2) death 3) sampling 4) rho 5) r 6) migration rate. Default false.");

	public Input<RealParameter> R0_base =
			new Input<>("R0_base",
					"The basic reproduction number for the base pathogen class, should have the same dimension as " +
							"the number of time intervals.");
	public Input<RealParameter> lambda_ratio =
			new Input<>("lambda_ratio",
					"The ratio of basic infection rates of all other classes when compared to the base lambda, " +
							"should have the dimension of the number of pathogens - 1, as it is kept constant over intervals.");

	public Input<RealParameter> migrationMatrix =
			new Input<>("migrationMatrix", "Flattened migration matrix, can be asymmetric, diagonal entries omitted");


	public Input<RealParameter> migrationMatrixScaleFactor =
			new Input<>("migrationMatrixScaleFactor", "A real number with which each migration rate entry is scaled.");

	// adapted from SCMigrationModel class in package MultiTypeTree by Tim Vaughan
	//TODO test (add unit test) to check this works
	public Input<BooleanParameter> rateMatrixFlagsInput = new Input<>(
			"rateMatrixFlags",
			"Optional boolean parameter specifying which rates to use."
					+ " (Default is to use all rates.)");


	public Input<RealParameter> birthRateAmongDemes =
			new Input<>("birthRateAmongDemes", "birth rate vector with rate at which transmissions occur among locations");

	public Input<RealParameter> R0AmongDemes =
			new Input<>("R0AmongDemes", "The basic reproduction number determining transmissions occur among locations");


	public Input<RealParameter> removalProbability =
			new Input<RealParameter>("removalProbability", "The probability of an individual to become noninfectious immediately after the sampling");


	public Input<Integer> stateNumber =
			new Input<>("stateNumber", "The number of states or locations", Input.Validate.REQUIRED);

	public Input<RealParameter> adjustTimesInput =
			new Input<>("adjustTimes", "Origin of MASTER sims which has to be deducted from the change time arrays");
	// <!-- HACK ALERT for reestimation from MASTER sims: adjustTimes is used to correct the forward changetimes such that they don't include orig-root (when we're not estimating the origin) -->

	public Input<Boolean> useRKInput =
			new Input<>("useRK", "Use fixed step size Runge-Kutta integrator with 1000 steps. Default false", false);

	public Input<Boolean> checkRho = new Input<>("checkRho", "check if rho is set if multiple tips are given at present (default true)", true);


	public Input<Boolean> isParallelizedCalculationInput = new Input<>("parallelize", "is the calculation parallelized on sibling subtrees or not (default true)", true);

	//If a large number a cores is available (more than 8 or 10) the calculation speed can be increased by diminishing the parallelization factor
	//On the contrary, if only 2-4 cores are available, a slightly higher value (1/5 to 1/8) can be beneficial to the calculation speed.
	public Input<Double> minimalProportionForParallelizationInput = new Input<>("parallelizationFactor", "the minimal relative size the two children subtrees of a node" +
			" must have to start parallel calculations on the children. (default: 1/10). ", new Double(1/10));


	public static boolean isParallelizedCalculation;

	public static double minimalProportionForParallelization;

	//  TODO check if it's possible to have 1e-20 there
	public final static double globalPrecisionThreshold = 1e-10;

	static volatile double T = 0;
	double orig;
	int ntaxa;

	p0_ODE P;
	p0ge_ODE PG;

	FirstOrderIntegrator pg_integrator;
	public static Double minstep;
	public static Double maxstep;

	// these four arrays are totalIntervals in length
	protected double[] birth;
	double[] death;
	Double[] psi;
	static  volatile Double[] rho;
	Double[] r;

	/**
	 * The number of change points in the birth rate, b_ij, death rate, sampling rate, rho, r
	 */
	int migChanges;
	int birthChanges;
	int b_ij_Changes;
	int deathChanges;
	int samplingChanges;
	static int rhoChanges;
	int rChanges;


	public boolean SAModel;

	/**
	 * The number of times rho-sampling occurs
	 */
	int rhoSamplingCount;
	Boolean constantRho;
	Boolean[] isRhoTip;

	/**
	 * Total interval count
	 */
	static int totalIntervals;
	static int n;  // number of states / locations

	protected List<Double> migChangeTimes = new ArrayList<>();
	protected List<Double> birthRateChangeTimes = new ArrayList<>();
	protected List<Double> b_ijChangeTimes = new ArrayList<>();
	protected List<Double> deathRateChangeTimes = new ArrayList<>();
	protected List<Double> samplingRateChangeTimes = new ArrayList<>();
	protected List<Double> rhoSamplingChangeTimes = new ArrayList<>();
	protected List<Double> rChangeTimes = new ArrayList<Double>();

	Boolean contempData;
	SortedSet<Double> timesSet = new TreeSet<>();

	protected static volatile Double[] times = new Double[]{0.};

	protected Boolean transform;

	Boolean migTimesRelative = false;
	Boolean birthRateTimesRelative = false;
	Boolean b_ijTimesRelative = false;
	Boolean deathRateTimesRelative = false;
	Boolean samplingRateTimesRelative = false;
	Boolean rTimesRelative = false;
	Boolean[] reverseTimeArrays;

	Double[] M;
	Double[] b_ij;
	Boolean birthAmongDemes = false;

	Double[] freq;

	static double[][] pInitialConditions;

	protected BooleanParameter rateMatrixFlags;

	//TODO maybe change type to HashMap (then no need to resize array)
	public double[] weightOfNodeSubTree;

	double parallelizationThreshold;

	static ExecutorService executor;
	static ThreadPoolExecutor pool;


	TreeInterface tree;

	@Override
	public void initAndValidate() {

		tree = treeInput.get();

		identicalRatesForAllTypes = new Boolean[]{false, false, false, false, false, false};
		if (identicalRatesForAllTypesInput.get()!=null)
			identicalRatesForAllTypes = identicalRatesForAllTypesInput.get().getValues();

		if (removalProbability.get() != null) SAModel = true;

		birth = null;
		b_ij = null;
		death = null;
		psi = null;
		rho = null;
		r = null;
		birthRateChangeTimes.clear();
		deathRateChangeTimes.clear();
		samplingRateChangeTimes.clear();
		if (SAModel) rChangeTimes.clear();
		totalIntervals = 0;
		n = stateNumber.get();

		birthAmongDemes = (birthRateAmongDemes.get() !=null || R0AmongDemes.get()!=null);

		Double factor;
		if (migrationMatrix.get()!=null) {
			M = migrationMatrix.get().getValues();

			if (rateMatrixFlagsInput.get() != null) {
				rateMatrixFlags = rateMatrixFlagsInput.get();

				if (rateMatrixFlags.getDimension() != migrationMatrix.get().getDimension())
					throw new IllegalArgumentException("Migration rate flags"
							+ " array does not have same number of elements as"
							+ " migration rate matrix.");

				for (int i = 0; i < M.length; i++) {
					M[i] = rateMatrixFlags.getValue(i)? M[i] : 0.0;
				}
			}


			if (migrationMatrixScaleFactor.get()!=null) {
				factor = migrationMatrixScaleFactor.get().getValue();
				for (int i = 0; i < M.length; i++) M[i] *= factor;
			}

			if (n>1 && M.length != n*(n-1)) {
				double timeChanges = 0;
				if (migChangeTimesInput.get()!=null) {
					timeChanges = migChangeTimesInput.get().getDimension();
				} else if(intervalTimes.get() != null){
					timeChanges = intervalTimes.get().getDimension();
				}
				if (timeChanges == 0 || M.length != n*(n-1)*timeChanges )
					throw new RuntimeException("Migration matrix dimension is incorrect!");
			}
			migChanges = migrationMatrix.get().getDimension()/Math.max(1,(n*(n-1))) - 1;

		}


		else if (!birthAmongDemes) throw new RuntimeException("Error in BDMM setup: need to specify at least one of the following: migrationMatrix, R0AmongDemes, birthRateAmongDemes");

		birthRateTimesRelative = birthRateChangeTimesRelativeInput.get();
		b_ijTimesRelative = b_ijChangeTimesRelativeInput.get();
		migTimesRelative = migTimesRelativeInput.get();
		deathRateTimesRelative = deathRateChangeTimesRelativeInput.get();
		samplingRateTimesRelative = samplingRateChangeTimesRelativeInput.get();
		if (SAModel) rTimesRelative = removalProbabilityChangeTimesRelativeInput.get();

		reverseTimeArrays = new Boolean[]{false, false, false, false, false, false};
		if (reverseTimeArraysInput.get()!= null )  {
			Boolean[] r = reverseTimeArraysInput.get().getValues();
			for (int i=0; i<r.length; i++)
				reverseTimeArrays[i] = r[i];
		}

		rhoSamplingCount = 0;
		contempData = contemp.get();

		if (birthRate.get() == null && R0.get() == null && R0_base.get() == null && lambda_ratio.get() == null) {
			throw new RuntimeException("Either birthRate, R0, or R0_base and R0_ratio need to be specified!");
		} else if ((birthRate.get() != null && R0.get() != null)
				|| (R0.get() != null && (R0_base.get() != null || lambda_ratio.get() != null))
				|| (birthRate.get() != null && (R0_base.get() != null || lambda_ratio.get() != null))) {
			throw new RuntimeException("Only one of birthRate, or R0, or R0_base and lambda_ratio need to be specified!");
		} else if (birthRate.get() != null && deathRate.get() != null && samplingRate.get() != null) {

			transform = false;
			death = deathRate.get().getDoubleValues();
			psi = samplingRate.get().getValues();
			birth = birthRate.get().getDoubleValues();
			if (SAModel) r = removalProbability.get().getValues();

			if (birthRateAmongDemes.get()!=null ){

				birthAmongDemes = true;
				b_ij=birthRateAmongDemes.get().getValues();
			}
		} else if ((R0.get() != null || (R0_base.get() != null && lambda_ratio.get() != null)) && becomeUninfectiousRate.get() != null && samplingProportion.get() != null) {
			transform = true;
		} else {
			throw new RuntimeException("Either specify birthRate, deathRate and samplingRate OR specify R0 (or R0_base AND R0_ratio), becomeUninfectiousRate and samplingProportion!");
		}

		if (transform) {

			if (R0AmongDemes.get()!=null) {
				birthAmongDemes = true;
				b_ij_Changes = R0AmongDemes.get().getDimension()/Math.max(1,(n*(n-1))) - 1;
			}

			if (birthChanges < 1) {
				if (R0.get()!=null) {
					birthChanges = R0.get().getDimension() / n - 1;
				} else {
					birthChanges = R0_base.get().getDimension() - 1;
				}
			}
			samplingChanges = samplingProportion.get().getDimension()/n - 1;
			deathChanges = becomeUninfectiousRate.get().getDimension()/n - 1;

		} else {    //todo: b d s param doesn't work yet with rate changes (unless all parameters have equally many)

			if (birthChanges < 1) birthChanges = birthRate.get().getDimension()/n - 1;
			if (birthAmongDemes) b_ij_Changes = birthRateAmongDemes.get().getDimension()/(n*(n-1)) - 1;
			deathChanges = deathRate.get().getDimension()/n - 1;
			samplingChanges = samplingRate.get().getDimension()/n - 1;
		}


		if (SAModel) rChanges = removalProbability.get().getDimension()/n -1;

		if (m_rho.get()!=null) {
			rho = m_rho.get().getValues();
			rhoChanges = m_rho.get().getDimension()/n - 1;
		}

		freq = frequencies.get().getValues();

		double freqSum = 0;
		for (double f : freq) freqSum+= f;
		if (Math.abs(1.0-freqSum)>1e-10)
			throw new RuntimeException("Error: frequencies must add up to 1 but currently add to " + freqSum + ".");


		ntaxa = tree.getLeafNodeCount();

		int contempCount = 0;
		for (Node node : tree.getExternalNodes())
			if (node.getHeight()==0.)
				contempCount++;

		if (checkRho.get() && contempCount>1 && rho==null)
			throw new RuntimeException("Error: multiple tips given at present, but sampling probability \'rho\' is not specified.");


		checkOrigin(tree);
		collectTimes(T);
		setRho();

		weightOfNodeSubTree = new double[2*ntaxa];

		isParallelizedCalculation = isParallelizedCalculationInput.get();
		minimalProportionForParallelization = minimalProportionForParallelizationInput.get();

		if(isParallelizedCalculation) executorBootUp();

	}

	/**
	 *
	 * @param t
	 * @param PG0
	 * @param t0
	 * @param PG
	 * @return
	 */
	public static p0ge_InitialConditions getG(double t, p0ge_InitialConditions PG0, double t0, p0ge_ODE PG){// PG0 contains initial condition for p0 (0..n-1) and for ge (n..2n-1)

		try {

			if (Math.abs(T-t) < globalPrecisionThreshold|| Math.abs(t0-t) < globalPrecisionThreshold ||  T < t) {
				return PG0;
			}

			double from = t;
			double to = t0;
			double oneMinusRho;

			int indexFrom = Utils.index(from, times, times.length);
			int index = Utils.index(to, times, times.length);

			int steps = index - indexFrom;
			if (Math.abs(from-times[indexFrom]) < globalPrecisionThreshold ) steps--;
			if (index>0 && Math.abs(to-times[index-1]) < globalPrecisionThreshold ) {
				steps--;
				index--;
			}
			index--;

			// pgScaled contains the set of initial conditions scaled made to fit the requirements on the values 'double' can represent. It also contains the factor by which the numbers were multiplied
			ScaledNumbers pgScaled = SmallNumberScaler.scale(PG0);

			while (steps > 0){

				from = times[index];

				pgScaled = safeIntegrate(PG, to, pgScaled, from); // solve PG , store solution temporarily integrationResults

				// 'unscale' values in integrationResults so as to retrieve accurate values after the integration.
				PG0 = SmallNumberScaler.unscale(pgScaled.getEquation(), pgScaled.getScalingFactor());


				if (rhoChanges>0){
					for (int i=0; i<n; i++){
						oneMinusRho = 1-rho[i*totalIntervals + index];
						PG0.conditionsOnP[i] *= oneMinusRho;
						PG0.conditionsOnG[i] = PG0.conditionsOnG[i].scalarMultiply(oneMinusRho);
					}
				}

				to = times[index];

				steps--;
				index--;

				// 'rescale' the results of the last integration to prepare for the next integration step
				pgScaled = SmallNumberScaler.scale(PG0);
			}

			pgScaled = safeIntegrate(PG, to, pgScaled, t); // solve PG , store solution temporarily integrationResults

			// 'unscale' values in integrationResults so as to retrieve accurate values after the integration.
			PG0 = SmallNumberScaler.unscale(pgScaled.getEquation(), pgScaled.getScalingFactor());

		}catch(Exception e){
			// e.printStackTrace(); // for debugging

			throw new RuntimeException("couldn't calculate g");
		}

		return PG0;
	}

	void setRho(){

		isRhoTip = new Boolean[ treeInput.get().getLeafNodeCount()];
		Arrays.fill(isRhoTip,false);


		if (m_rho.get() != null) {

			constantRho = !(m_rho.get().getDimension() > n);

			if (m_rho.get().getDimension() <= n && (rhoSamplingTimes.get()==null || rhoSamplingTimes.get().getDimension() < 2)) {
				if (!contempData && ((samplingProportion.get() != null && samplingProportion.get().getDimension() <= n && samplingProportion.get().getValue() == 0.) || // todo:  instead of samplingProportion.get().getValue() == 0. need checked that samplingProportion[i]==0 for all i=0..n-1
						(samplingRate.get() != null && samplingRate.get().getDimension() <= 2 && samplingRate.get().getValue() == 0.))) {                              // todo:  instead of samplingRate.get().getValue() == 0. need checked that samplingRate[i]==0 for all i=0..n-1

					// check if data set is contemp!
					for (Node node : treeInput.get().getExternalNodes()){
						if (node.getHeight()>globalPrecisionThreshold*1E5 ) throw new RuntimeException("Error in analysis setup: Parameters set for entirely contemporaneously sampled data, but some nodeheights are > 0!"); // TODO improve the threhsold limit or have a way to circumvent it
					}

					contempData = true;
					System.out.println("BDMM: setting contemp=true.");
				}
			}

			if (contempData) {
				if (m_rho.get().getDimension() != 1 && m_rho.get().getDimension() != n)
					throw new RuntimeException("when contemp=true, rho must have dimension 1 (or equal to the stateNumber)");

				else {
					rho = new Double[n*totalIntervals];
					Arrays.fill(rho, 0.);
					Arrays.fill(isRhoTip, true);
					for (int i=1; i<=n; i++)  rho[i*totalIntervals - 1] = m_rho.get().getValue(i-1);

					rhoSamplingCount = 1;
				}
			}
			else {
				Double[] rhos = m_rho.get().getValues();
				rho = new Double[n*totalIntervals];
				Arrays.fill(rho, 0.);
				for (int i = 0; i < totalIntervals; i++) {
					for (int j=0;j<n;j++){
						rho[j*totalIntervals+i]= rhoSamplingChangeTimes.contains(times[i]) ? (rhos[constantRho? j : j*(1+rhoChanges)+rhoSamplingChangeTimes.indexOf(times[i])]) : 0.;
					}
				}
				computeRhoTips();
			}


		} else {
			rho = new Double[n*totalIntervals];
			Arrays.fill(rho, 0.);
		}

	}

	abstract void computeRhoTips();

	/**
	 * Perform an initial traversal of the tree to get the 'weights' (sum of all its edges lengths) of all sub-trees
	 * Useful for performing parallelized calculations on the tree.
	 * The weights of the subtrees tell us the depth at which parallelization should stop, so as to not parallelize on subtrees that are too small.
	 * Results are stored in 'weightOfNodeSubTree' array
	 * @param tree
	 */
	public void getAllSubTreesWeights(TreeInterface tree){
		Node root = tree.getRoot();
		double weight = 0;
		for(final Node child : root.getChildren()) {
			weight += getSubTreeWeight(child);
		}
		weightOfNodeSubTree[root.getNr()] = weight;
	}

	/**
	 * Perform an initial traversal of the subtree to get its 'weight': sum of all its edges.
	 * @param node
	 * @return
	 */
	public double getSubTreeWeight(Node node){

		// if leaf, stop recursion, get length of branch above and return
		if(node.isLeaf()) {
			weightOfNodeSubTree[node.getNr()] = node.getLength();
			return node.getLength();
		}

		// else, iterate over the children of the node
		double weight = 0;
		for(final Node child : node.getChildren()) {
			weight += getSubTreeWeight(child);
		}
		// add length of parental branch
		weight += node.getLength();
		// store the value
		if(node.getNr() >= weightOfNodeSubTree.length)
			throw new IndexOutOfBoundsException("Node number is not between 0 and ntaxa-1. This should be the case according to Node specifications.");

		weightOfNodeSubTree[node.getNr()] = weight;

		return weight;
	}


	/**
	 * Collect all the times of parameter value changes and rho-sampling events
	 */
	void collectTimes(double maxTime) {

		timesSet.clear();

		getChangeTimes(maxTime, migChangeTimes,
				migChangeTimesInput.get() != null ? migChangeTimesInput.get() : intervalTimes.get(),
				migChanges, migTimesRelative, reverseTimeArrays[5]);

		getChangeTimes(maxTime, birthRateChangeTimes,
				birthRateChangeTimesInput.get() != null ? birthRateChangeTimesInput.get() : intervalTimes.get(),
				birthChanges, birthRateTimesRelative, reverseTimeArrays[0]);

		getChangeTimes(maxTime, b_ijChangeTimes,
				b_ijChangeTimesInput.get() != null ? b_ijChangeTimesInput.get() : intervalTimes.get(),
				b_ij_Changes, b_ijTimesRelative, reverseTimeArrays[0]);

		getChangeTimes(maxTime, deathRateChangeTimes,
				deathRateChangeTimesInput.get() != null ? deathRateChangeTimesInput.get() : intervalTimes.get(),
				deathChanges, deathRateTimesRelative, reverseTimeArrays[1]);

		getChangeTimes(maxTime, samplingRateChangeTimes,
				samplingRateChangeTimesInput.get() != null ? samplingRateChangeTimesInput.get() : intervalTimes.get(),
				samplingChanges, samplingRateTimesRelative, reverseTimeArrays[2]);

		getChangeTimes(maxTime, rhoSamplingChangeTimes,
				rhoSamplingTimes.get()!=null ? rhoSamplingTimes.get() : intervalTimes.get(),
				rhoChanges, false, reverseTimeArrays[3]);

		if (SAModel) getChangeTimes(maxTime, rChangeTimes,
				removalProbabilityChangeTimesInput.get() != null ? removalProbabilityChangeTimesInput.get() : intervalTimes.get(),
				rChanges, rTimesRelative, reverseTimeArrays[4]);

		for (Double time : migChangeTimes) {
			timesSet.add(time);
		}

		for (Double time : birthRateChangeTimes) {
			timesSet.add(time);
		}

		for (Double time : b_ijChangeTimes) {
			timesSet.add(time);
		}

		for (Double time : deathRateChangeTimes) {
			timesSet.add(time);
		}

		for (Double time : samplingRateChangeTimes) {
			timesSet.add(time);
		}

		for (Double time : rhoSamplingChangeTimes) {
			timesSet.add(time);
		}

		if (SAModel) {
			for (Double time : rChangeTimes) {
				timesSet.add(time);
			}
		}


		times = timesSet.toArray(new Double[timesSet.size()]);
		// TODO potentially refactor with totalIntervals = times.length-1 so that totalIntervals really represents the number of time intervals
		totalIntervals = times.length;

	}

	/**
	 * set change times
	 */
	public void getChangeTimes(double maxTime, List<Double> changeTimes, RealParameter intervalTimes, int numChanges, boolean relative, boolean reverse) {
		changeTimes.clear();

		if (intervalTimes == null) { //equidistant

			double intervalWidth = maxTime / (numChanges + 1);

			double end;
			for (int i = 1; i <= numChanges; i++) {
				end = (intervalWidth) * i;
				changeTimes.add(end);
			}
			end = maxTime;
			changeTimes.add(end);

		} else {
			//TODO remove this check for rho-sampling times
			if (!reverse && intervalTimes.getValue(0) != 0.0) {
				throw new RuntimeException("First time in interval times parameter should always be zero.");
			}

			if (numChanges > 0 && intervalTimes.getDimension() != numChanges + 1) {
				throw new RuntimeException("The time interval parameter should be numChanges + 1 long (" + (numChanges + 1) + ").");
			}

			int dim = intervalTimes.getDimension();

			double end;
			for (int i = (reverse?0:1); i < dim; i++) {
				end = reverse ? (maxTime - intervalTimes.getValue(dim - i - 1)) : intervalTimes.getValue(i);
				if (relative) end *= maxTime;
				if (end < maxTime) changeTimes.add(end); //TODO does this mean that change times can never be input in absolute time? It looks like it does.
			}

			if (adjustTimesInput.get()!=null){

				double iTime;
				double aTime = adjustTimesInput.get().getValue();

				for (int i = 0 ; i < numChanges; i++){

					iTime = intervalTimes.getArrayValue(i+1);

					if (aTime<iTime) {
						end = iTime - aTime;
						if
								(changeTimes.size() > i) changeTimes.set(i, end);
						else
						if (end < maxTime)
							changeTimes.add(end);
					}
				}
			}
			end = maxTime;

			changeTimes.add(end); //TODO: why is the end time always a change point? Here, it seems that 'end' always refers to the size of the time interval between the first and the last sample. I don't necessarily want it to be a change point.
		}
	}


	void updateBirthDeathPsiParams(){

		double[] birthRates = birthRate.get().getDoubleValues();
		double[] deathRates = deathRate.get().getDoubleValues();
		Double[] samplingRates = samplingRate.get().getValues();
		Double[] removalProbabilities = new Double[1];

		if (SAModel) {
			removalProbabilities = removalProbability.get().getValues();
			r =  new Double[n*totalIntervals];
		}

		int state;

		for (int i = 0; i < n*totalIntervals; i++) {

			state =  i/totalIntervals;

			birth[i] = (identicalRatesForAllTypes[0]) ? birthRates[index(times[i%totalIntervals], birthRateChangeTimes)] :
					birthRates[birthRates.length > n ? (birthChanges+1)*state+index(times[i%totalIntervals], birthRateChangeTimes) : state];
			death[i] = (identicalRatesForAllTypes[1]) ? deathRates[index(times[i%totalIntervals], deathRateChangeTimes)] :
					deathRates[deathRates.length > n ? (deathChanges+1)*state+index(times[i%totalIntervals], deathRateChangeTimes) : state];
			psi[i] = (identicalRatesForAllTypes[2]) ? samplingRates[index(times[i%totalIntervals], samplingRateChangeTimes)] :
					samplingRates[samplingRates.length > n ? (samplingChanges+1)*state+index(times[i%totalIntervals], samplingRateChangeTimes) : state];
			if (SAModel) r[i] = (identicalRatesForAllTypes[4]) ? removalProbabilities[index(times[i%totalIntervals], rChangeTimes)] :
					removalProbabilities[removalProbabilities.length > n ? (rChanges+1)*state+index(times[i%totalIntervals], rChangeTimes) : state];

		}

	}


	void updateAmongParameter(Double[] param, Double[] paramFrom, int nrChanges, List<Double> changeTimes){

		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				for (int dt = 0; dt < totalIntervals; dt++) {
					if (i != j) {
						param[(i * (n - 1) + (j < i ? j : j - 1)) * totalIntervals + dt]
								= paramFrom[(paramFrom.length > (n * (n - 1)))
								? (nrChanges + 1) * (n - 1) * i + index(times[dt], changeTimes)
								: (i * (n - 1) + (j < i ? j : j - 1))];
					}
				}
			}
		}
	}


	void updateRho(){
		if (m_rho.get() != null && (m_rho.get().getDimension()==1 ||  rhoSamplingTimes.get() != null)) {

			Double[] rhos = m_rho.get().getValues();
			rho = new Double[n*totalIntervals];
			int state;

			for (int i = 0; i < totalIntervals*n; i++) {

				state =  i/totalIntervals;

				rho[i]= rhoChanges>0?
						rhoSamplingChangeTimes.contains(times[i]) ? rhos[rhos.length > n ? (rhoChanges+1)*state+index(times[i%totalIntervals], rhoSamplingChangeTimes) : state] : 0.
						: rhos[0];
			}
		}
	}

	void updateParallelizationThreshold(){
		if(isParallelizedCalculation) {
			getAllSubTreesWeights(tree);
			// set 'parallelizationThreshold' to a fraction of the whole tree weight.
			// The size of this fraction is determined by a tuning parameter. This parameter should be adjusted (increased) if more computation cores are available
			parallelizationThreshold = weightOfNodeSubTree[tree.getRoot().getNr()] * minimalProportionForParallelization;
		}
	}


	/**
	 * @param t the time in question
	 * @return the index of the given time in the list of times, or if the time is not in the list, the index of the
	 *         next smallest time
	 *         This index function should only be used in transformParameters(), for likelihood calculations the times List needs to be used (e.g. with Utils.index(...))
	 */
	public int index(double t, List<Double> times) {

		int epoch = Collections.binarySearch(times, t);

		if (epoch < 0) {
			epoch = -epoch - 1;
		}

		return epoch;
	}


	public void transformWithinParameters(){

		Double[] p = samplingProportion.get().getValues();
		Double[] ds = becomeUninfectiousRate.get().getValues();
		Double[] R;
		if (R0.get() != null) {
			R = R0.get().getValues();
		} else {
			Double[] l_ratio = lambda_ratio.get().getValues();
			Double[] R_sens = R0_base.get().getValues();

			int totalIntervals = R_sens.length;
			int totalTypes = l_ratio.length + 1;
			R = new Double[totalIntervals * totalTypes];
			for (int i=0; i < totalIntervals; i++) {
				R[i] = R_sens[i];
				for (int j=1; j < totalTypes; j++) {
					double lambda = R_sens[i] * ds[ds.length > totalTypes ? index(times[i%totalIntervals], deathRateChangeTimes) : 0];
					R[i + totalIntervals * j] = (lambda * l_ratio[j - 1]) / ds[ds.length > totalTypes ? (deathChanges+1)*j+index(times[i%totalIntervals], deathRateChangeTimes) : j];
				}
			}
		}

		Double[] removalProbabilities = new Double[1];
		if (SAModel) removalProbabilities = removalProbability.get().getValues();

		int state;

		for (int i = 0; i < totalIntervals*n; i++){

			state =  i/totalIntervals;

			birth[i] = ((identicalRatesForAllTypes[0]) ? R[index(times[i%totalIntervals], birthRateChangeTimes)] :
					R[R.length > n ? (birthChanges+1)*state+index(times[i%totalIntervals], birthRateChangeTimes) : state])
					* ((identicalRatesForAllTypes[1]) ? ds[index(times[i%totalIntervals], deathRateChangeTimes)] :
					ds[ds.length > n ? (deathChanges+1)*state+index(times[i%totalIntervals], deathRateChangeTimes) : state]);

			if (!SAModel) {
				psi[i] = ((identicalRatesForAllTypes[2]) ? p[index(times[i%totalIntervals], samplingRateChangeTimes)] :
						p[p.length > n ? (samplingChanges + 1) * state + index(times[i % totalIntervals], samplingRateChangeTimes) : state])
						* ((identicalRatesForAllTypes[1]) ? ds[index(times[i%totalIntervals], deathRateChangeTimes)] :
						ds[ds.length > n ? (deathChanges+1)*state+index(times[i%totalIntervals], deathRateChangeTimes) : state]);

				death[i] = ((identicalRatesForAllTypes[1]) ? ds[index(times[i%totalIntervals], deathRateChangeTimes)] :
						ds[ds.length > n ? (deathChanges+1)*state+index(times[i%totalIntervals], deathRateChangeTimes) : state])
						- psi[i];
			}

			else {
				r[i] = (identicalRatesForAllTypes[4]) ? removalProbabilities[index(times[i%totalIntervals], rChangeTimes)] :
						removalProbabilities[removalProbabilities.length > n ? (rChanges+1)*state+index(times[i%totalIntervals], rChangeTimes) : state];

				psi[i] = ((identicalRatesForAllTypes[2]) ? p[index(times[i%totalIntervals], samplingRateChangeTimes)] :
						p[p.length > n ? (samplingChanges+1)*state+index(times[i%totalIntervals], samplingRateChangeTimes) : state])
						* ((identicalRatesForAllTypes[1]) ? ds[index(times[i%totalIntervals], deathRateChangeTimes)] :
						ds[ds.length > n ? (deathChanges+1)*state+index(times[i%totalIntervals], deathRateChangeTimes) : state])
						/ (1+(r[i]-1)*
						((identicalRatesForAllTypes[2]) ? p[index(times[i%totalIntervals], samplingRateChangeTimes)] :
								p[p.length > n ? (samplingChanges+1)*state+index(times[i%totalIntervals], samplingRateChangeTimes) : state]));


				death[i] = ((identicalRatesForAllTypes[1]) ? ds[index(times[i%totalIntervals], deathRateChangeTimes)] :
						ds[ds.length > n ? (deathChanges+1)*state+index(times[i%totalIntervals], deathRateChangeTimes) : state])
						- psi[i]*r[i];
			}
		}

	}


	public void transformAmongParameters(){

		Double[] RaD = (birthAmongDemes) ? R0AmongDemes.get().getValues() : new Double[1];
		Double[] ds = becomeUninfectiousRate.get().getValues();

		if (birthAmongDemes)    {

			for (int i = 0; i < n; i++){

				for (int j=0; j<n ; j++){

					for (int dt=0; dt<totalIntervals; dt++){

						if (i!=j){
							b_ij[(i*(n-1)+(j<i?j:j-1))*totalIntervals+dt]
									= RaD[(RaD.length>(n*(n-1)))
									?  (b_ij_Changes+1)*(n-1)*i + index(times[dt], b_ijChangeTimes)
									: (i*(n-1)+(j<i?j:j-1))]
									* ds[ds.length > n ? (deathChanges+1)*i+index(times[dt], deathRateChangeTimes) : i];
						}
					}
				}

			}
		}
	}


	void checkOrigin(TreeInterface tree){

		if (origin.get()==null){
			T = tree.getRoot().getHeight();
		}
		else {

			updateOrigin(tree.getRoot());

			if (!Boolean.valueOf(System.getProperty("beast.resume")) && orig < 0)
				throw new RuntimeException("Error: origin("+T+") must be larger than tree height("+tree.getRoot().getHeight()+")!");
		}

	}


	void updateOrigin(Node root){

		T = origin.get().getValue();
		orig = T - root.getHeight();

		if (originIsRootEdge.get()) {

			orig = origin.get().getValue();
			T = orig + root.getHeight();
		}

	}


	void setupIntegrators(){   // set up ODE's and integrators

		//TODO set these minstep and maxstep to be a class field
		if (minstep == null) minstep = T*1e-100;
		if (maxstep == null) maxstep = T/10;

		Boolean augmented = this instanceof BirthDeathMigrationModel;

		P = new p0_ODE(birth, ((birthAmongDemes) ? b_ij : null), death,psi,M, n, totalIntervals, times);
		PG = new p0ge_ODE(birth, ((birthAmongDemes) ? b_ij : null), death,psi,M, n, totalIntervals, T, times, P, maxEvaluations.get(), augmented);

		p0ge_ODE.globalPrecisionThreshold = globalPrecisionThreshold;

		if (!useRKInput.get()) {
			pg_integrator = new DormandPrince54Integrator(minstep, maxstep, absoluteTolerance.get(), relativeTolerance.get());
			PG.p_integrator = new DormandPrince54Integrator(minstep, maxstep, absoluteTolerance.get(), relativeTolerance.get());
		} else {
			pg_integrator = new ClassicalRungeKuttaIntegrator(T / 1000);
			PG.p_integrator = new ClassicalRungeKuttaIntegrator(T / 1000);
		}
	}

	/**
	 * Perform the integration of PG with initial conds in pgScaled between to and from
	 * Use an adaptive-step-size integrator
	 * "Safe" because it divides the integration interval in two
	 * if the interval is (arbitrarily) judged to be too big to give reliable results
	 * @param PG
	 * @param to
	 * @param pgScaled
	 * @param from
	 * @return
	 */
	public static ScaledNumbers safeIntegrate(p0ge_ODE PG, double to, ScaledNumbers pgScaled, double from){

		// if the integration interval is too small, nothing is done (to prevent infinite looping)
		if(Math.abs(from-to) < globalPrecisionThreshold /*(T * 1e-20)*/) return pgScaled;

		//TODO make threshold a class field
		if(T>0 && Math.abs(from-to)>T/6 ) {
			pgScaled = safeIntegrate(PG, to, pgScaled, from + (to-from)/2);
			pgScaled = safeIntegrate(PG, from + (to-from)/2, pgScaled, from);
		} else {

			//setup of the relativeTolerance and absoluteTolerance input of the adaptive integrator
			//TODO set these two as class fields
			double relativeToleranceConstant = 1e-7;
			double absoluteToleranceConstant = 1e-100;
			double[] absoluteToleranceVector = new double [2*n];
			double[] relativeToleranceVector = new double [2*n];

			for(int i = 0; i<n; i++) {
				absoluteToleranceVector[i] = absoluteToleranceConstant;
				if(pgScaled.getEquation()[i+n] > 0) { // adapt absoluteTolerance to the values stored in pgScaled
					absoluteToleranceVector[i+n] = Math.max(1e-310, pgScaled.getEquation()[i+n]*absoluteToleranceConstant);
				} else {
					absoluteToleranceVector[i+n] = absoluteToleranceConstant;
				}
				relativeToleranceVector[i] = relativeToleranceConstant;
				relativeToleranceVector[i+n] = relativeToleranceConstant;
			}

			double[] integrationResults = new double[pgScaled.getEquation().length];
			int a = pgScaled.getScalingFactor(); // store scaling factor
			int n = pgScaled.getEquation().length/2; // dimension of the ODE system


			FirstOrderIntegrator integrator = new DormandPrince54Integrator(minstep, maxstep, absoluteToleranceVector, relativeToleranceVector);
			integrator.integrate(PG, to, pgScaled.getEquation(), from, integrationResults); // perform the integration step

			double[] pConditions = new double[n];
			SmallNumber[] geConditions = new SmallNumber[n];
			for (int i = 0; i < n; i++) {
				pConditions[i] = integrationResults[i];
				geConditions[i] = new SmallNumber(integrationResults[i+n]);
			}
			pgScaled = SmallNumberScaler.scale(new p0ge_InitialConditions(pConditions, geConditions));
			pgScaled.augmentFactor(a);
		}

		return pgScaled;
	}

	/**
	 * Find all initial conditions for all future integrations on p0 equations
	 * @param tree
	 * @return an array of arrays storing the initial conditions values
	 */
	public double[][] getAllInitialConditionsForP(TreeInterface tree){

		int leafCount = tree.getLeafNodeCount();
		double[] leafHeights = new double[leafCount];
		int[] indicesSortedByLeafHeight  =new int[leafCount];

		for (int i=0; i<leafCount; i++){ // get all leaf heights
			leafHeights[i] = T - tree.getNode(i).getHeight();
			// System.out.println(nodeHeight[i]);
			indicesSortedByLeafHeight[i] = i;
		}

		HeapSort.sort(leafHeights, indicesSortedByLeafHeight); // sort leafs in order their height in the tree
		//"sort" sorts in ascending order, so we have to be careful since the integration starts from the leaves at height T and goes up to the root at height 0 (or >0)

		double[][] pInitialCondsAtLeaves = new double[leafCount + 1][n];

		double t = leafHeights[indicesSortedByLeafHeight[leafCount-1]];

		boolean rhoSampling =  (m_rho.get()!=null);

		pInitialCondsAtLeaves[indicesSortedByLeafHeight[leafCount-1]] = PG.getP(t, rhoSampling, rho);
		double t0 = t;

		if (leafCount >1 ){
			for (int i = leafCount-2; i>-1; i--){
				t = leafHeights[indicesSortedByLeafHeight[i]];

				//If the next higher leaf is actually at the same height, store previous results and skip iteration
				if (Math.abs(t-t0) < globalPrecisionThreshold) {
					t0 = t;
					pInitialCondsAtLeaves[indicesSortedByLeafHeight[i]] = pInitialCondsAtLeaves[indicesSortedByLeafHeight[i+1]];
					continue;
				} else {
					//TODO the integration performed in getP is done before all the other potentially-parallelized getG, so should not matter that it has its own integrator, but if it does (or to simplify the code), take care of passing an integrator as a local variable
					pInitialCondsAtLeaves[indicesSortedByLeafHeight[i]] = PG.getP(t, pInitialCondsAtLeaves[indicesSortedByLeafHeight[i+1]], t0, rhoSampling, rho);
					t0 = t;
				}

			}
		}

		pInitialCondsAtLeaves[leafCount] = PG.getP(0, pInitialCondsAtLeaves[indicesSortedByLeafHeight[0]], t0, rhoSampling, rho);

		return pInitialCondsAtLeaves;
	}

	protected Double updateRates() {

		birth = new double[n*totalIntervals];
		death = new double[n*totalIntervals];
		psi = new Double[n*totalIntervals];
		b_ij = new Double[totalIntervals*(n*(n-1))];
		M = new Double[totalIntervals*(n*(n-1))];
		if (SAModel) r =  new Double[n * totalIntervals];

		if (transform) {
			transformParameters();
		}
		else {

			Double[] birthAmongDemesRates = new Double[1];

			if (birthAmongDemes) birthAmongDemesRates = birthRateAmongDemes.get().getValues();

			updateBirthDeathPsiParams();

			if (birthAmongDemes) {

				updateAmongParameter(b_ij, birthAmongDemesRates, b_ij_Changes, b_ijChangeTimes);
			}
		}

		if (migrationMatrix.get()!=null) {
			Double[] migRates = migrationMatrix.get().getValues();

			Double factor;
			if (migrationMatrixScaleFactor.get()!=null) {
				factor = migrationMatrixScaleFactor.get().getValue();
				for (int i = 0; i < migRates.length; i++) migRates[i] *= factor;
			}

			if (rateMatrixFlagsInput.get() != null) {
				rateMatrixFlags = rateMatrixFlagsInput.get();

				for (int i = 0; i < migRates.length; i++) {
					migRates[i] = rateMatrixFlags.getValue(i)? migRates[i] : 0.0;
				}
			}

			updateAmongParameter(M, migRates, migChanges, migChangeTimes);
		}

		updateRho();

		freq = frequencies.get().getValues();

		setupIntegrators();

		return 0.;
	}

	public void transformParameters(){

		transformWithinParameters();
		transformAmongParameters();
	}

	Boolean[] identicalRatesForAllTypes;

	static void executorBootUp(){
		executor = Executors.newCachedThreadPool();
		pool = (ThreadPoolExecutor) executor;
	}

	static void executorShutdown(){
		pool.shutdown();
	}

	/**
	 * Obtain element of rate matrix for migration model for use in likelihood
	 * calculation.
	 *
	 * @param i
	 * @param j
	 * @return Rate matrix element.
	 */
	public double getNbyNRate(int i, int j) {
		if (i==j)
			return 0;

		int offset = getArrayOffset(i, j);

		if (migrationMatrixScaleFactor.get()==null)
			return migrationMatrix.get().getValue(offset);
		else
			return migrationMatrixScaleFactor.get().getValue()*migrationMatrix.get().getValue(offset);

	}


	/**
	 * Obtain offset into "rate matrix" and associated flag arrays.
	 *
	 * @param i
	 * @param j
	 * @return Offset (or -1 if i==j)
	 */
	protected int getArrayOffset(int i, int j) {

		if (i==j)
			throw new RuntimeException("Programmer error: requested migration "
					+ "rate array offset for diagonal element of "
					+ "migration rate matrix.");


		if (j>i)
			j -= 1;
		return i*(n-1)+j;   // todo: check if this is correct!!!
	}


	// Interface requirements:
	@Override
	public List<String> getArguments() {
		return null;
	}


	@Override
	public List<String> getConditions() {
		return null;
	}

	@Override
	public void sample(State state, Random random) {
	}

	@Override
	public boolean requiresRecalculation(){
		return true;
	}

	abstract class TraversalService implements Callable<p0ge_InitialConditions> {

		protected Node rootSubtree;
		protected double from;
		protected double to;
		protected p0ge_ODE PG;
		protected FirstOrderIntegrator pg_integrator;

		public TraversalService(Node root, double from, double to, boolean augmented) {
			this.rootSubtree = root;
			this.from = from;
			this.to = to;
			this.setupODEs(augmented);
		}

		private void setupODEs(boolean augmented){  // set up ODE's and integrators

			//TODO set minstep and maxstep to be PiecewiseBDDistr fields
			if (minstep == null) minstep = T*1e-100;
			if (maxstep == null) maxstep = T/10;

			PG = new p0ge_ODE(birth, ((birthAmongDemes) ? b_ij : null), death,psi,M, n, totalIntervals, T, times, P, maxEvaluations.get(), augmented);

			p0ge_ODE.globalPrecisionThreshold = globalPrecisionThreshold;

			pg_integrator = new DormandPrince54Integrator(minstep, maxstep, absoluteTolerance.get(), relativeTolerance.get());
		}

		abstract protected p0ge_InitialConditions calculateSubtreeLikelihoodInThread();

		@Override
		public p0ge_InitialConditions call() throws Exception {
			// traverse the tree in a potentially-parallelized way
			return calculateSubtreeLikelihoodInThread();
		}
	}

}
