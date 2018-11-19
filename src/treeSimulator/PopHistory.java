package treeSimulator;

import java.util.*;

public class PopHistory {

    final int numberOfDemes;
    Vector<Double> timingOfEvents;
    Vector<EventType> eventsWithType;
    Vector<int[]> popSizesThroughTime;

    int currentTotalPopSize = 1; // start with one individual by default
    int numberOfSamplesTaken = 0;

    int defaultVectorSize = 200;

    boolean verbose = false;

    public PopHistory() {
        numberOfDemes = 1;
        timingOfEvents = new Vector(defaultVectorSize);
        eventsWithType = new Vector<>(defaultVectorSize);
        popSizesThroughTime = new Vector<>(defaultVectorSize);

        popSizesThroughTime.add(new int[]{1});
    }

    public PopHistory(int endConditionOnNumberOfTips) {
        numberOfDemes = 1;
        timingOfEvents = new Vector(endConditionOnNumberOfTips * 3);
        eventsWithType = new Vector<>(endConditionOnNumberOfTips * 3);
        popSizesThroughTime = new Vector<>(endConditionOnNumberOfTips * 3);

        popSizesThroughTime.add(new int[]{1});
    }

    public PopHistory(int endConditionOnNumberOfTips, int numOfDemes, double[] frequencies) {
        numberOfDemes = numOfDemes;
        timingOfEvents = new Vector(endConditionOnNumberOfTips * 3);
        eventsWithType = new Vector<>(endConditionOnNumberOfTips * 3);
        popSizesThroughTime = new Vector(endConditionOnNumberOfTips * 3);

        int[] initialPop = new int[numberOfDemes];

        int ancestorType = 0;
        double randomValue = Math.random();
        // draw the type of the first individual in the population
        while(randomValue > frequencies[ancestorType]) {
            randomValue -= frequencies[ancestorType];
            ancestorType ++;
        }

        for (int i = 0; i < numberOfDemes-1; i++) {
            initialPop[i] = 0;
        }
        initialPop[ancestorType] = 1;
        popSizesThroughTime.add(initialPop);
    }

    public void addEvent(EventType event, double elapsedTime) {
        timingOfEvents.add(elapsedTime);
        eventsWithType.add(event);

        updatePopSize(event);
    }

    public void updatePopSize(EventType event) {
        int[] popSize = popSizesThroughTime.lastElement().clone();
        switch(event.typeOfEvent) {
            case BIRTH:
                popSize[event.demeAffected] += 1;
                currentTotalPopSize += 1;
                break;
            case DEATH:
                popSize[event.demeAffected] -= 1;
                currentTotalPopSize -= 1;
                break;
            case SAMPLING: // assume prob of removal upon sampling to be 1
                popSize[event.demeAffected] -= 1;
                currentTotalPopSize -= 1;
                numberOfSamplesTaken += 1;
                if(verbose) {
                    System.out.println("Sampling event. Total pop sampled: " + numberOfSamplesTaken);
                }
                break;
            case SAMPLINGWITHOUTREMOVAL:
                numberOfSamplesTaken += 1;
                if(verbose) {
                    System.out.println("Sampling event without removal. Total pop sampled: " + numberOfSamplesTaken);
                }
                break;
            case MIGRATION:
                popSize[event.demeAffected] -= 1;
                popSize[event.demeTarget] += 1;
                if(verbose) {
                    System.out.println("Migration event. From deme " + event.demeAffected + "to deme "+ event.demeTarget + "Total pop sampled: " + numberOfSamplesTaken);
                }
                break;
            default:
                throw new RuntimeException("Event type not implemented yet: " + event.typeOfEvent);
        }
        popSizesThroughTime.add(popSize);

        if(verbose) {
            System.out.println("Total pop size: " + currentTotalPopSize);
        }
    }

    public int getCurrentTotalPopSize(){
        return currentTotalPopSize;
    }

    public int[] getCurrentPopSize(){
        return popSizesThroughTime.lastElement();
    }

    public int getNumberOfSamplesTaken(){
        return numberOfSamplesTaken;
    }

    public String toString() {
        String globalRes = "";
        for (int i = 0; i < numberOfDemes; i++) {
            String result = "Pop size through time for deme " + i + "\n";
            for(int[] sizes: popSizesThroughTime){
                result += sizes[i];
                result +=  " ";
            }
            globalRes += result;
        }

        return globalRes;
    }
}
