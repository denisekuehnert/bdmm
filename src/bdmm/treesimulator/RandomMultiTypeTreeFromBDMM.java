package bdmm.treesimulator;

import beast.base.core.Input;
import beast.base.evolution.tree.Tree;
import beast.base.inference.StateNode;
import beast.base.inference.StateNodeInitialiser;
import beast.base.inference.parameter.RealParameter;
import beast.base.util.Randomizer;
import beast.evolution.tree.*;
import multitypetree.evolution.tree.MultiTypeNode;
import multitypetree.evolution.tree.MultiTypeTree;

import java.util.List;


/**
 * Generate random coloured tree from multitype birth death model
 * Caveat: non-constant parameters are not allowed (piecewise constant not implemented)
 * Also: rho-sampling is not implemented
 *
 * author: Jeremie Scire
 */

public class RandomMultiTypeTreeFromBDMM extends MultiTypeTree implements StateNodeInitialiser {

    public Input<RealParameter> birthRateInput =
            new Input<>("birthRate", "The birthRate vector.", Input.Validate.REQUIRED);
    public Input<RealParameter> deathRateInput =
            new Input<>("deathRate", "The deathRate vector.", Input.Validate.REQUIRED);
    public Input<RealParameter> frequenciesInput =
            new Input<>("frequencies", "The frequencies for each type",  Input.Validate.REQUIRED);
    public Input<RealParameter> migrationMatrixInput =
            new Input<>("migrationMatrix", "Flattened migration matrix, can be asymmetric, diagonal entries omitted");
    public Input<Integer> numberOfLeavesInput =
            new Input<>("numberOfLeaves", "The number of leaves of the simulated tree. Default is a random number between 4 and 500.");

    public Input<RealParameter> samplingRateInput =
            new Input<>("samplingRate", "The sampling rate per individual", Input.Validate.REQUIRED);      // psi-sampling

    public Input<RealParameter> rhoSamplingProbabilityInput =
            new Input<>("rhoSamplingProbability", "Rho-sampling probability for each type.");

    public Input<RealParameter> rhoSamplingTimeInput =
            new Input<>("rhoSamplingTime", "This time is considered as being the 'present'. If  ");

    public Input<RealParameter> removalProbabilityInput =
            new Input<>("removalProbability", "The removal probability   for each type");

    public Input<Integer> demeNumberInput =
            new Input<>("demeNumber", "The number of demes or locations", Input.Validate.REQUIRED);

    public Input<String> outputFileNameInput = new Input<>(
            "outputFileName", "Optional name of file to write simulated tree to.");


    static int maxTreeSize = 500; // max tree size for random size tree
    int leavesInTree;

    public RandomMultiTypeTreeFromBDMM() {}

    @Override
    public void initAndValidate() {

//        super.initAndValidate();

        int numberOfDemes = demeNumberInput.get();
        double[] birthRate = birthRateInput.get().getDoubleValues();
        double[] deathRate = deathRateInput.get().getDoubleValues();
        double[] samplingRate = samplingRateInput.get().getDoubleValues();
        double[] migrationRate = migrationMatrixInput.get().getDoubleValues();
        double[] frequencies = frequenciesInput.get().getDoubleValues();


        boolean sampledAncestors = (removalProbabilityInput.get() != null);
        double[] removalProbs = new double[]{};

        if(sampledAncestors)
            removalProbs = removalProbabilityInput.get().getDoubleValues();


        if(numberOfLeavesInput.get() != null) {
            leavesInTree = numberOfLeavesInput.get();
        } else {
            leavesInTree = 4 + Randomizer.nextInt(maxTreeSize - 4); // draw random number of leaves
        }

        // this construction in the next few lines is not very clean, but I leave it for now
        PopHistorySimulator simulator = new PopHistorySimulator(numberOfDemes, birthRate, deathRate, samplingRate, migrationRate,
                frequencies, sampledAncestors, removalProbs);
        PopHistory simulatedPopHistory = simulator.SimulatePopHistory(leavesInTree);

        MultiTypeNode rootNode = simulator.buildMTTTreeFromStructuredPopHistory(simulatedPopHistory);
        setRoot(rootNode);
        initArrays();

        super.initAndValidate();
    }

    @Override
    public void initStateNodes() { }

    @Override
    public void getInitialisedStateNodes(List<StateNode> stateNodeList) {
        stateNodeList.add(this);
    }

    public static void main(String[] args) throws Exception {


        RealParameter migrationMatrix = new RealParameter();
        migrationMatrix.initByName(
                "value", "0.3 0.3 ",
                "dimension", "2");
        RealParameter deathRate = new RealParameter();
        deathRate.initByName(
                "value", "1.0 1.0",
                "dimension", "2");

        RealParameter birthRate = new RealParameter();
        birthRate.initByName(
                "value", "1.5 1.5",
                "dimension", "2");
        RealParameter samplingRate = new RealParameter();
        samplingRate.initByName(
                "value", "0.25 0.25",
                "dimension", "2");
        RealParameter frequencies = new RealParameter();
        frequencies.initByName(
                "value", "0.5 0.5",
                "dimension", "2");

        RealParameter removalProbs = new RealParameter();
        removalProbs.initByName(
                "value", "1.0 1.0",
                "dimension", "2");

        Integer numberOfLeaves = 10;

        Integer demeNumber = 2;

        String outputFileName = "simulatedColouredTree.txt";

        RandomMultiTypeTreeFromBDMM simulatedTree = new RandomMultiTypeTreeFromBDMM();

        simulatedTree.setInputValue("migrationMatrix", migrationMatrix);
        simulatedTree.setInputValue("birthRate", birthRate);
        simulatedTree.setInputValue("deathRate", deathRate);
        simulatedTree.setInputValue("samplingRate", samplingRate);
        simulatedTree.setInputValue("frequencies", frequencies);
        simulatedTree.setInputValue("numberOfLeaves", numberOfLeaves);
        simulatedTree.setInputValue("demeNumber", demeNumber);
        simulatedTree.setInputValue("removalProbability", removalProbs);
        simulatedTree.setInputValue("outputFileName", outputFileName);

//        simulatedTree.initByName("migrationMatrix", migrationMatrix,
//                "birthRate", birthRate, "deathRate", deathRate,
//                "samplingRate", samplingRate, "numberOfLeaves", numberOfLeaves,
//                "demeNumber", demeNumber, "outputFileName", outputFileName);

        simulatedTree.initAndValidate();

        Tree flattenedTree = simulatedTree.getFlattenedTree(false);
        String res = flattenedTree.getRoot().toString() + ";";

        System.out.println(res);

    }

}
