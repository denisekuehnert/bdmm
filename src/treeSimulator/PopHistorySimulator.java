package treeSimulator;

import beast.evolution.tree.MultiTypeNode;
import beast.evolution.tree.MultiTypeTree;
import beast.evolution.tree.Node;
import beast.evolution.tree.Tree;
import org.jblas.util.Random;

import java.util.ArrayList;

import static beast.math.Binomial.choose2;

public class PopHistorySimulator {

    final int numberOfDemes;

    // not allowing for rate changes
    final double[] birthRate;
    final double[] deathRate;
    final double[] psiSamplingRate;
    final double[] migrationRate;
    final double[] frequencies;

    boolean sampledAncestors;
    final double[] removalProbs;

    boolean verbose = false;

    //TODO clean up the duplicated code

    public PopHistorySimulator(double birthRate, double deathRate, double psiSamplingRate) {
        this.birthRate = new double[]{birthRate};
        this.deathRate = new double[]{deathRate};
        this.psiSamplingRate = new double[]{psiSamplingRate};
        this.migrationRate = new double[]{};

        this.frequencies = new double[]{1};
        this.sampledAncestors = false;
        this.removalProbs = new double[]{0};
        this.numberOfDemes = 1;
    }

    public PopHistorySimulator(int numberOfDemes,
                               double[] birthRate, double[] deathRate, double[] psiSamplingRate, double[] migrationRate,
                               double[] frequencies, boolean sampledAncestors, double[] removalProbs) {
        this.numberOfDemes = numberOfDemes;
        this.frequencies = frequencies;

        this.birthRate = birthRate;
        this.deathRate = deathRate;
        this.psiSamplingRate = psiSamplingRate;
        this.migrationRate = migrationRate;
        this.sampledAncestors = sampledAncestors;
        if(this.sampledAncestors) this.removalProbs = removalProbs;
        else this.removalProbs = new double[numberOfDemes];
    }

    public PopHistory SimulatePopHistory(int endCondition) {

        PopHistory simulatedPopHistory;

        do {
            simulatedPopHistory = new PopHistory(endCondition, numberOfDemes, frequencies);

            double elapsedTime = 0;

            while (simulatedPopHistory.getCurrentTotalPopSize() > 0
                    && simulatedPopHistory.getNumberOfSamplesTaken() < endCondition) {

                elapsedTime += getTimeUntilNextEvent(simulatedPopHistory);
                EventType nextEvent = getTypeOfNextEvent(simulatedPopHistory);

                if (verbose) System.out.println("Elapsed time: " + elapsedTime);

                simulatedPopHistory.addEvent(nextEvent, elapsedTime);
            }

            if (verbose) System.out.println("Total number of samples taken during this run: " + simulatedPopHistory.getNumberOfSamplesTaken());

        } while(simulatedPopHistory.getNumberOfSamplesTaken() < endCondition); // repeat if sampling condition not reached

        return simulatedPopHistory;
    }

    public double getTimeUntilNextEvent(PopHistory simPopHistory){
        double aggregatedRate = this.getAggregatedRate(simPopHistory.getCurrentPopSize());
        return - Math.log(1 - Math.random())/aggregatedRate; // draw timeUntilNextEvent from exponential distr
    }

    public EventType getTypeOfNextEvent(PopHistory simPopHistory){
        double rand = Math.random();
        double aggregatedRate = 0;
        double totalAggregatedRate = getAggregatedRate(simPopHistory.getCurrentPopSize());

        int[] popSize = simPopHistory.getCurrentPopSize();

        for (int i = 0; i < numberOfDemes; i++) {
            aggregatedRate += birthRate[i]*popSize[i]/totalAggregatedRate;

            if(rand < aggregatedRate)
                return new EventType(Event.BIRTH, i);

            aggregatedRate += deathRate[i]*popSize[i]/totalAggregatedRate;

            if(rand < aggregatedRate)
                return new EventType(Event.DEATH, i);

            aggregatedRate += psiSamplingRate[i]*popSize[i]/totalAggregatedRate;

            if(rand < aggregatedRate) {
                if(sampledAncestors) {
                    double rand2 = Math.random();
                    if (rand2 < removalProbs[i])
                        return new EventType(Event.SAMPLINGWITHOUTREMOVAL, i);
                }
                return new EventType(Event.SAMPLING, i);
            }
            for (int j = 0; j < numberOfDemes; j++) {
                if(i != j){
                    int arrayOffset = j>i? i*(numberOfDemes -1) + j-1 : i*(numberOfDemes -1) + j;
                    aggregatedRate += migrationRate[arrayOffset] * popSize[i]/totalAggregatedRate;
                    if(rand < aggregatedRate)
                        return new EventType(Event.MIGRATION, i, j);
                }
            }

        }

        throw new RuntimeException("A problem occurred when looking for the type of the next event.");
    }

    public double getAggregatedRate(int[] popSize){
        double aggregatedRate = 0;
        for (int i = 0; i < numberOfDemes; i++) {
            aggregatedRate += (birthRate[i] + deathRate[i] + psiSamplingRate[i]) * popSize[i];
            for (int j = 0; j < numberOfDemes; j++) {
                if(i != j) {
                    int arrayOffset = j>i? i*(numberOfDemes -1) + j-1 : i*(numberOfDemes -1) + j;
                    aggregatedRate += migrationRate[arrayOffset] * popSize[i];
                }
            }
        }
        return aggregatedRate;
    }

    public Tree buildTreeFromPopHistory(PopHistory simulatedPopHistory) {

        if(numberOfDemes > 1) {
            throw new RuntimeException("Multitype simulation impossible wiht this method.");
        }

        ArrayList<Node> availableNodes = new ArrayList<>();

        ArrayList<ArrayList<Node>> availableNodesPerType = new ArrayList<>();

        // check that simulation is consistent with what is expected
        EventType lastEvent =  simulatedPopHistory.eventsWithType.lastElement();
        if(lastEvent.typeOfEvent != Event.SAMPLING) throw new RuntimeException("Last event should be a sampling event. Something went wrong with the population simulation.");

        int currentEventIndex = simulatedPopHistory.eventsWithType.size() -1; // start with the last event recorded

        int totalNumberOfLeaves = simulatedPopHistory.getNumberOfSamplesTaken();
        int leafID = 0; // start with leafID at 0
        int internalNodeID = totalNumberOfLeaves;

        double heightOffset = simulatedPopHistory.timingOfEvents.get(currentEventIndex);

        while(currentEventIndex > -1) {
            EventType currentEvent = simulatedPopHistory.eventsWithType.remove(currentEventIndex);
            double eventHeight = simulatedPopHistory.timingOfEvents.get(currentEventIndex);

            switch(currentEvent.typeOfEvent) {
                case SAMPLING:
                    Node leaf = new Node();
                    leaf.setNr(leafID);
                    // attempt
                    leaf.setHeight(heightOffset - eventHeight);
                    availableNodes.add(leaf);
                    leafID ++;
                    break;

                case DEATH:
                    break;

                case BIRTH:
                    boolean coalescence = false;
                    if (availableNodes.size() > 1) { // if more than one lineage in the stack, allow for potential coalescence
                        coalescence = isCoalescenceEvent(availableNodes.size(),
                                simulatedPopHistory.popSizesThroughTime.get(currentEventIndex + 1)[0]);
                    }

                    if (coalescence) {
                        // draw coalescing lineages randomly
                        int firstLineage = Random.nextInt(availableNodes.size());
                        int secondLineage = Random.nextInt(availableNodes.size() - 1);
                        if (secondLineage == firstLineage) secondLineage = availableNodes.size() - 1; // mimick sampling without replacement

                        availableNodes = coalesceLineages(availableNodes, firstLineage, secondLineage, internalNodeID, heightOffset - eventHeight);
                        internalNodeID ++;
                    }
                    break;

                default:
                    throw new RuntimeException("Not implemented yet.");
            }

            currentEventIndex --;
        }


        if(availableNodes.size() != 1)
            throw new RuntimeException("There should be exactly one lineage left.");

        // for debugging
        Tree tree = new Tree(availableNodes.get(0));

        return new Tree(availableNodes.get(0));
    }

    public MultiTypeNode buildMTTTreeFromStructuredPopHistory(PopHistory simulatedPopHistory) {

        ArrayList<ArrayList<MultiTypeNode>> availableNodesPerType = new ArrayList<>();

        for (int i = 0; i < numberOfDemes; i++) {
            availableNodesPerType.add(new ArrayList<>());
        }

        // check that simulation is consistent with what is expected
        EventType lastEvent =  simulatedPopHistory.eventsWithType.lastElement();
        if(lastEvent.typeOfEvent != Event.SAMPLING && lastEvent.typeOfEvent != Event.SAMPLINGWITHOUTREMOVAL) throw new RuntimeException("Last event should be a sampling event. Something went wrong with the population simulation.");

        int currentEventIndex = simulatedPopHistory.eventsWithType.size() -1; // start with the last event recorded

        int totalNumberOfLeaves = simulatedPopHistory.getNumberOfSamplesTaken();
        int leafID = 0; // start with leafID at 0
        int internalNodeID = totalNumberOfLeaves;

        double heightOffset = simulatedPopHistory.timingOfEvents.get(currentEventIndex);

        while(currentEventIndex > -1) {
            EventType currentEvent = simulatedPopHistory.eventsWithType.remove(currentEventIndex);
            double eventHeight = simulatedPopHistory.timingOfEvents.get(currentEventIndex);
            int availableLineagesForThisDeme = availableNodesPerType.get(currentEvent.demeAffected).size();
            MultiTypeNode leaf;


            switch(currentEvent.typeOfEvent) {
                case SAMPLING:
                    leaf = new MultiTypeNode();
                    leaf.setNr(leafID);
                    leaf.setNodeType(currentEvent.demeAffected);
                    leaf.setHeight(heightOffset - eventHeight);
                    availableNodesPerType.get(currentEvent.demeAffected).add(leaf);
                    leafID ++;
                    break;

                case SAMPLINGWITHOUTREMOVAL:
                    boolean saEvent = isSampledAncestorEvent(
                            availableNodesPerType.get(currentEvent.demeAffected).size(),
                            simulatedPopHistory.popSizesThroughTime.get(currentEventIndex + 1)[currentEvent.demeAffected]);
                    if (saEvent) { // sampled ancestor
                        // draw descendant lineage
                        int uniqueDescendant = Random.nextInt(availableLineagesForThisDeme);
                        availableNodesPerType.set(currentEvent.demeAffected,
                                buildSampledAncestor(availableNodesPerType.get(currentEvent.demeAffected), uniqueDescendant, internalNodeID, leafID,
                                        heightOffset - eventHeight, currentEvent.demeAffected));
                        internalNodeID ++;
                        leafID ++;
                    }
                    else { // classic sampling //TODO clean up duplication with case: SAMPLING
                        leaf = new MultiTypeNode();
                        leaf.setNr(leafID);
                        leaf.setNodeType(currentEvent.demeAffected);
                        leaf.setHeight(heightOffset - eventHeight);
                        availableNodesPerType.get(currentEvent.demeAffected).add(leaf);
                        leafID ++;
                    }
                    break;

                case DEATH:
                    break;

                case BIRTH:
                    boolean coalescence = false;
                    if (availableLineagesForThisDeme > 1) { // if more than one lineage in the stack of this deme, allow for potential coalescence
                        coalescence = isCoalescenceEvent(availableLineagesForThisDeme,
                                simulatedPopHistory.popSizesThroughTime.get(currentEventIndex + 1)[currentEvent.demeAffected]);
                    }

                    if (coalescence) {
                        // draw coalescing lineages randomly
                        int firstLineage = Random.nextInt(availableLineagesForThisDeme);
                        int secondLineage = Random.nextInt(availableLineagesForThisDeme - 1);
                        if (secondLineage == firstLineage) secondLineage = availableLineagesForThisDeme - 1; // mimick sampling without replacement

                        availableNodesPerType.set(currentEvent.demeAffected, coalesceLineages(availableNodesPerType.get(currentEvent.demeAffected),
                                firstLineage, secondLineage, internalNodeID, heightOffset - eventHeight, currentEvent.demeAffected));
                        internalNodeID ++;
                    }
                    break;

                case MIGRATION:
                    int originDemeIndex = currentEvent.demeAffected;
                    int demeSize = simulatedPopHistory.popSizesThroughTime.get(currentEventIndex + 1)[currentEvent.demeTarget];
                    int numLineagesAvailable = availableNodesPerType.get(currentEvent.demeTarget).size();
                    if(isMigrationEvent(numLineagesAvailable, demeSize)) {
                        migrateLineage(availableNodesPerType.get(currentEvent.demeTarget),
                                availableNodesPerType.get(currentEvent.demeAffected),
                                originDemeIndex, heightOffset - eventHeight);
                    }
                    break;

                default:
                    throw new RuntimeException("Not implemented yet.");
            }

            currentEventIndex --;
        }

        int lineagesLeft = 0;
        int ancestorDeme = 0;
        for (int i = 0; i < numberOfDemes; i++) {
            lineagesLeft += availableNodesPerType.get(i).size();
            if(availableNodesPerType.get(i).size() > 0)
                ancestorDeme = i; // keep track of the ancestor lineage
        }


        if(lineagesLeft != 1)
            throw new RuntimeException("There should be exactly one lineage left.");

        // for debugging
        //MultiTypeTree tree = new MultiTypeTree(availableNodesPerType.get(ancestorDeme).get(0));

        return availableNodesPerType.get(ancestorDeme).get(0);
    }

    public Node buildTipTypedTreeFromStructuredPopHistory(PopHistory simulatedPopHistory, boolean isUntypedTree) {

        ArrayList<ArrayList<Node>> availableNodesPerType = new ArrayList<>();

        for (int i = 0; i < numberOfDemes; i++) {
            availableNodesPerType.add(new ArrayList<>());
        }

        // check that simulation is consistent with what is expected
        EventType lastEvent =  simulatedPopHistory.eventsWithType.lastElement();
        if(lastEvent.typeOfEvent != Event.SAMPLING && lastEvent.typeOfEvent != Event.SAMPLINGWITHOUTREMOVAL) throw new RuntimeException("Last event should be a sampling event. Something went wrong with the population simulation.");

        int currentEventIndex = simulatedPopHistory.eventsWithType.size() -1; // start with the last event recorded

        int totalNumberOfLeaves = simulatedPopHistory.getNumberOfSamplesTaken();
        int leafID = 0; // start with leafID at 0
        int internalNodeID = totalNumberOfLeaves;

        double heightOffset = simulatedPopHistory.timingOfEvents.get(currentEventIndex);

        while(currentEventIndex > -1) {
            EventType currentEvent = simulatedPopHistory.eventsWithType.remove(currentEventIndex);
            double eventHeight = simulatedPopHistory.timingOfEvents.get(currentEventIndex);
            int availableLineagesForThisDeme = availableNodesPerType.get(currentEvent.demeAffected).size();
            Node leaf;

            switch(currentEvent.typeOfEvent) {
                case SAMPLING:
                    leaf = new Node();
                    leaf.setNr(leafID);

                    if (isUntypedTree) {
                        leaf.setMetaData("type", -1);
                        leaf.metaDataString = "type=-1";
                    }
                    else {
                        leaf.setMetaData("type", currentEvent.demeAffected);
                        leaf.metaDataString = "type=" + currentEvent.demeAffected;
                    }
                    //leaf.setNodeType(currentEvent.demeAffected);
                    leaf.setHeight(heightOffset - eventHeight);
                    availableNodesPerType.get(currentEvent.demeAffected).add(leaf);
                    leafID ++;
                    break;

                case SAMPLINGWITHOUTREMOVAL:
                    boolean saEvent = isSampledAncestorEvent(
                            availableNodesPerType.get(currentEvent.demeAffected).size(),
                            simulatedPopHistory.popSizesThroughTime.get(currentEventIndex + 1)[currentEvent.demeAffected]);
                    if (saEvent) { // sampled ancestor
                        // draw descendant lineage
                        int uniqueDescendant = Random.nextInt(availableLineagesForThisDeme);
                        availableNodesPerType.set(currentEvent.demeAffected,
                                buildSampledAncestor(availableNodesPerType.get(currentEvent.demeAffected), uniqueDescendant, internalNodeID, leafID,
                                        heightOffset - eventHeight, currentEvent.demeAffected, isUntypedTree));
                        internalNodeID ++;
                        leafID ++;
                    }
                    else { // classic sampling
                        leaf = new Node();
                        leaf.setNr(leafID);

                        if (isUntypedTree) {
                            leaf.setMetaData("type", -1);
                            leaf.metaDataString = "type=-1";
                        }
                        else {
                            leaf.setMetaData("type", currentEvent.demeAffected);
                            leaf.metaDataString = "type=" + currentEvent.demeAffected;
                        }
                        //leaf.setNodeType(currentEvent.demeAffected);
                        leaf.setHeight(heightOffset - eventHeight);
                        availableNodesPerType.get(currentEvent.demeAffected).add(leaf);
                        leafID ++;
                    }
                    break;

                case DEATH:
                    break;

                case BIRTH:
                    boolean coalescence = false;
                    if (availableLineagesForThisDeme > 1) { // if more than one lineage in the stack of this deme, allow for potential coalescence
                        coalescence = isCoalescenceEvent(availableLineagesForThisDeme,
                                simulatedPopHistory.popSizesThroughTime.get(currentEventIndex + 1)[currentEvent.demeAffected]);
                    }

                    if (coalescence) {
                        // draw coalescing lineages randomly
                        int firstLineage = Random.nextInt(availableLineagesForThisDeme);
                        int secondLineage = Random.nextInt(availableLineagesForThisDeme - 1);
                        if (secondLineage == firstLineage) secondLineage = availableLineagesForThisDeme - 1; // mimick sampling without replacement

                        availableNodesPerType.set(currentEvent.demeAffected, coalesceLineages(availableNodesPerType.get(currentEvent.demeAffected),
                                firstLineage, secondLineage, internalNodeID, heightOffset - eventHeight));
                        internalNodeID ++;
                    }
                    break;

                case MIGRATION:
                    int originDemeIndex = currentEvent.demeAffected;
                    int demeSize = simulatedPopHistory.popSizesThroughTime.get(currentEventIndex + 1)[currentEvent.demeTarget];
                    int numLineagesAvailable = availableNodesPerType.get(currentEvent.demeTarget).size();
                    if(isMigrationEvent(numLineagesAvailable, demeSize)) {
                        migrateLineage(availableNodesPerType.get(currentEvent.demeTarget),
                                availableNodesPerType.get(currentEvent.demeAffected),
                                originDemeIndex);
                    }
                    break;

                default:
                    throw new RuntimeException("Not implemented yet.");
            }

            currentEventIndex --;
        }

        int lineagesLeft = 0;
        int ancestorDeme = 0;
        for (int i = 0; i < numberOfDemes; i++) {
            lineagesLeft += availableNodesPerType.get(i).size();
            if(availableNodesPerType.get(i).size() > 0)
                ancestorDeme = i; // keep track of the ancestor lineage
        }


        if(lineagesLeft != 1)
            throw new RuntimeException("There should be exactly one lineage left.");

        // for debugging
        //Tree tree = new Tree(availableNodesPerType.get(ancestorDeme).get(0));

        return availableNodesPerType.get(ancestorDeme).get(0);
    }

    public boolean isCoalescenceEvent(int numberOfLineages, int popSize){
        double rand = Math.random();
        return (rand < choose2(numberOfLineages)/choose2(popSize)); // coalescent event or not
    }

    public boolean isSampledAncestorEvent(int numberOfLineages, int popSize) {
        double rand = Math.random();
        return (rand < numberOfLineages * 1.0 / popSize);
    }

    public boolean isMigrationEvent(int numberOfLineages, int popSize){
        double rand = Math.random();
        return (rand < numberOfLineages*1.0/popSize);
    }

    public ArrayList<Node> coalesceLineages(ArrayList<Node> availableNodes, int firstLineageIndex, int secondLineageIndex, int newNodeID, double newNodeHeight) {

        Node newNode = new Node();
        newNode.setNr(newNodeID);
        // attempt
        newNode.setHeight(newNodeHeight);

        Node firstChild;
        Node secondChild;

        // remove the element with the highest index first to avoid problems with renumbering between the two 'remove'
        if(firstLineageIndex > secondLineageIndex) {
            firstChild = availableNodes.remove(firstLineageIndex);
            secondChild = availableNodes.remove(secondLineageIndex);
        }
        else {
            secondChild = availableNodes.remove(secondLineageIndex);
            firstChild = availableNodes.remove(firstLineageIndex);
        }

        firstChild.setParent(newNode);
        secondChild.setParent(newNode);

        newNode.setChild(0, firstChild);
        newNode.setChild(1, secondChild);

        availableNodes.add(newNode);

        return availableNodes;
    }

    public ArrayList<MultiTypeNode> coalesceLineages(ArrayList<MultiTypeNode> availableNodes, int firstLineageIndex, int secondLineageIndex, int newNodeID, double newNodeHeight, int demeAffected) {

        MultiTypeNode newNode = new MultiTypeNode();
        newNode.setNr(newNodeID);
        newNode.setHeight(newNodeHeight);
        newNode.setNodeType(demeAffected);

        MultiTypeNode firstChild;
        MultiTypeNode secondChild;

        // remove the element with the highest index first to avoid problems with renumbering between the two 'remove'
        if(firstLineageIndex > secondLineageIndex) {
            firstChild = availableNodes.remove(firstLineageIndex);
            secondChild = availableNodes.remove(secondLineageIndex);
        } else {
            secondChild = availableNodes.remove(secondLineageIndex);
            firstChild = availableNodes.remove(firstLineageIndex);
        }

        firstChild.setParent(newNode);
        secondChild.setParent(newNode);

        newNode.setChild(0, firstChild);
        newNode.setChild(1, secondChild);

        availableNodes.add(newNode);

        return availableNodes;
    }

    public ArrayList<Node> buildSampledAncestor(ArrayList<Node> availableNodes, int descendantLineage, int newNodeID, int leafID, double newNodeHeight, int demeAffected, boolean isUntypedTree){

        Node newNode = new Node();
        newNode.setNr(newNodeID);
        newNode.setHeight(newNodeHeight);

        Node collapsedChild = new Node();
        collapsedChild.setNr(leafID);
        collapsedChild.setHeight(newNodeHeight);

        if (isUntypedTree) {
            collapsedChild.setMetaData("type", -1);
            collapsedChild.metaDataString = "type=-1";
        }
        else {
            collapsedChild.setMetaData("type", demeAffected);
             collapsedChild.metaDataString = "type=" + demeAffected;
        }

        Node uniqueChild = availableNodes.remove(descendantLineage);
        uniqueChild.setParent(newNode);
        collapsedChild.setParent(newNode);

        newNode.setChild(0, uniqueChild);
        newNode.setChild(1, collapsedChild);

        availableNodes.add(newNode);

        return availableNodes;
    }

    public ArrayList<MultiTypeNode> buildSampledAncestor(ArrayList<MultiTypeNode> availableNodes, int descendantLineage, int newNodeID, int leafID, double newNodeHeight, int demeAffected){

        MultiTypeNode newNode = new MultiTypeNode();
        newNode.setNr(newNodeID);
        newNode.setHeight(newNodeHeight);
        newNode.setNodeType(demeAffected);

        MultiTypeNode collapsedChild = new MultiTypeNode();
        collapsedChild.setNr(leafID);
        collapsedChild.setHeight(newNodeHeight);
        collapsedChild.setNodeType(demeAffected);

        MultiTypeNode uniqueChild = availableNodes.remove(descendantLineage);
        uniqueChild.setParent(newNode);
        collapsedChild.setParent(newNode);

        newNode.setChild(0, uniqueChild);
        newNode.setChild(1, collapsedChild);

        availableNodes.add(newNode);

        return availableNodes;
    }

    public void migrateLineage(ArrayList<MultiTypeNode> targetDeme, ArrayList<MultiTypeNode> originDeme, int originDemeInd ,double timeOfMigration) {
        int migratingLineage = Random.nextInt(targetDeme.size()); // we are reconstructing the tree backward in time, so we start from the affected (target) deme to the deme of origin

        MultiTypeNode migratingNode = targetDeme.remove(migratingLineage);
        migratingNode.addChange(originDemeInd, timeOfMigration);

        originDeme.add(migratingNode);
    }

    public void migrateLineage(ArrayList<Node> targetDeme, ArrayList<Node> originDeme, int originDemeInd) {
        int migratingLineage = Random.nextInt(targetDeme.size()); // we are reconstructing the tree backward in time, so we start from the affected (target) deme to the deme of origin
        Node migratingNode = targetDeme.remove(migratingLineage);
        originDeme.add(migratingNode);
    }


    public static void main(String[] args) {
//        double birthRate = 1.2;
//        double deathRate = 1.1;
//        double sampleRate = 0.1;
//        PopHistorySimulator simulator = new PopHistorySimulator(birthRate, deathRate, sampleRate);
//
//        PopHistory popHistory = simulator.SimulatePopHistory(4);
//        System.out.println(popHistory);
//
//        Tree tree = simulator.buildTreeFromPopHistory(popHistory);
//        String res = tree.getRoot().toShortNewick(true) + ";";
//
//        System.out.println(res);

        int numberOfDemes = 2;
        double[] birthRate = new double[] {1.2, 1.4};
        double[] deathRate = new double[] {1.1, 1.0};
        double[] sampleRate = new double[] {0.1, 0.2};
        double[] migrationRate = new double[]{0.6, 0.6};
        double[] freq = new double[]{0.5, 0.5};
        double[] removalProbs = new double[]{0.6, 0.9};

        boolean sampledAncestors = true;



        PopHistorySimulator simulator = new PopHistorySimulator(numberOfDemes, birthRate, deathRate, sampleRate, migrationRate,
                freq, sampledAncestors, removalProbs);

        PopHistory popHistory = simulator.SimulatePopHistory(100);
        System.out.println(popHistory);



        MultiTypeTree tree = new MultiTypeTree(simulator.buildMTTTreeFromStructuredPopHistory(popHistory));
        tree.initAndValidate();
        Tree flattenedTree = tree.getFlattenedTree(false);
        String res = flattenedTree.getRoot().toString() + ";";

        System.out.println(res);
        popHistory = simulator.SimulatePopHistory(100);

        Tree tipTypedTree = new Tree (simulator.buildTipTypedTreeFromStructuredPopHistory(popHistory, false));
        tipTypedTree.initAndValidate();
        res = tipTypedTree.getRoot().toShortNewick(true);
        System.out.println(res);
    }
}
