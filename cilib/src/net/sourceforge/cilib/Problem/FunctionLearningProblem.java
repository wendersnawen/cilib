/*
 * FunctionLearningProblem.java
 *
 * Created on June 24, 2003, 21:00 PM
 *
 *
 * Copyright (C) 2003, 2004 - CIRG@UP 
 * Computational Intelligence Research Group (CIRG@UP)
 * Department of Computer Science 
 * University of Pretoria
 * South Africa
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 */

package net.sourceforge.cilib.Problem;

// TODO: Add domain validators to check that this is working on ContinuousFunctions

import java.util.Iterator;
import java.util.Random;
import java.util.Vector;

import net.sourceforge.cilib.Domain.Component;
import net.sourceforge.cilib.Domain.Compound;
import net.sourceforge.cilib.Domain.Continuous;
import net.sourceforge.cilib.Domain.Quantitative;
import net.sourceforge.cilib.Functions.Function;
import net.sourceforge.cilib.NeuralNetwork.NN;
import net.sourceforge.cilib.Random.MersenneTwister;

public class FunctionLearningProblem extends OptimisationProblemAdapter {
    private Function function;
    private int sampleSetSize = 1000;
    private double trainingSetPercentage = 0.7;
    private double testingSetPercentage = 1.0 - trainingSetPercentage;
    private Vector testingSet = new Vector();
    private Vector trainingSet = new Vector();
    private Random random = new MersenneTwister(System.currentTimeMillis());
    private NN neuralNetwork = null;
    private Compound domain = null;
    private double functionMaxValue = -Double.MAX_VALUE;

    public FunctionLearningProblem() {
    }

    public FunctionLearningProblem(
        Function function,
        int sampleSetSize,
        double trainingSetPercentage,
        NN neuralNetwork) {
        this.function = function;
        this.sampleSetSize = sampleSetSize;
        this.trainingSetPercentage = trainingSetPercentage;
        this.testingSetPercentage = 1.0 - trainingSetPercentage;
        this.neuralNetwork = neuralNetwork;
        initialise();
    }

    public void initialise() {
        if (function == null) {
            throw new RuntimeException("the function is null");
        }
        if (sampleSetSize < 0) {
            throw new RuntimeException("sample set size is less than zero");
        }
        if (trainingSetPercentage < 0 || trainingSetPercentage > 1.0) {
            throw new RuntimeException("invalid training set percentage");
        }
        if (testingSetPercentage < 0 || testingSetPercentage > 1.0) {
            throw new RuntimeException("invalid test set percentage");
        }
        if (neuralNetwork == null) {
            throw new RuntimeException("neuralNetwork is null");
        }
        if (neuralNetwork.getSizeIL() - 1 != function.getDimension()) {
            throw new RuntimeException("input layer size does not match function dimension");
        }
        if (neuralNetwork.getSizeOL() != 1) {
            throw new RuntimeException("output layer size must be 1");
        }

        // add the required number of samples to the trainingSet.
        while (trainingSet.size() < sampleSetSize * trainingSetPercentage) {
            // create a random point within the function domain.
            Double[] p = new Double[function.getDimension()];
            for (int i = 0; i < p.length; i++) {
                double r =
                    ((Quantitative) domain.getComponent(0)).getUpperBound().doubleValue() * random.nextDouble()
                        - 2.0 * ((Quantitative) domain.getComponent(0)).getLowerBound().doubleValue() * random.nextDouble();
                p[i] = new Double(r);
            }

            // evaluate the input to determine the largest value for the function.
            double[] input = convertDoubleArray(p);
            double result = ((Double) function.evaluate(input)).doubleValue();
            if (result > functionMaxValue) {
                functionMaxValue = result;
            }

            // add the solution to the training set.
            trainingSet.add(p);
        }

        Quantitative domain = (Quantitative) function.getDomainComponent().getComponent(0);

        // add the required number of samples to the testingSet.
        while (testingSet.size() < sampleSetSize * testingSetPercentage) {
            // create a random point within the function domain.
            Double[] p = new Double[function.getDimension()];
            for (int i = 0; i < p.length; i++) {
                double r =
                    domain.getUpperBound().doubleValue() * random.nextDouble()
                        - 2.0 * domain.getLowerBound().doubleValue() * random.nextDouble();
                p[i] = new Double(r);
            }

            // evaluate the input to determine the largest value for the function.
            double[] input = convertDoubleArray(p);
            double result = ((Double) function.evaluate(input)).doubleValue();
            if (result > functionMaxValue) {
                functionMaxValue = result;
            }

            // add the point to the training set.
            testingSet.add(p);
        }
    }

    /**
     * This bases the fitness of the solution on the trainingSet samples.
     * @param solution
     * @return The average of all fitness values from the trainingSample set
     */
    protected Fitness calculateFitness(Object solution) {
        double[] tmp = (double[]) solution;
        // determine if the solution matches the number of weights required for
        // the NN to function.
        if (tmp.length != neuralNetwork.getNumberOfWeights()) {
            throw new RuntimeException("size of the solution does not match the number of weights required for the NN");
        }

        double totalFitness = 0.0;
        Iterator iterator = trainingSet.iterator();
        while (iterator.hasNext()) {
            // get the input value from the training set.
            Double[] p = (Double[]) iterator.next();

            // change the Double[] to a double[] for input to the NN.
            double[] input = convertDoubleArray(p);

            // calculate the expected output for the input.
            Fitness[] exp_output = new Fitness[1];
            exp_output[0] = new MinimisationFitness(function.evaluate(input));

            // we need to scale the expected output in the range [0.0, 1.0] for the
            // NN to be evaluated properly. Since the NN only outputs values in the
            // range [0.0, 0.1].
            exp_output[0] =new MinimisationFitness(new Double(((Double) exp_output[0].getValue()).doubleValue() / functionMaxValue));
            //      double slope = 0.05;
            //      double range = 1.0;
            //      exp_output[0] = sigmoid(exp_output[0], slope, range);

            // evaluate solution using the NN.
            double[] output = new double[1];
            neuralNetwork.getOutput(input, tmp, output);

            // decrement the totalFitness.
            totalFitness -= Math.pow(((Double) exp_output[0].getValue()).doubleValue() - output[0], 2.0);
        }

        return new MinimisationFitness(new Double( (totalFitness / trainingSet.size())));
    }

    public double getError(Object solution) {
        double[] tmp = (double[]) solution;
        // determine if the solution matches the number of weights required for
        // the NN to function.
        if (tmp.length != neuralNetwork.getNumberOfWeights()) {
            throw new RuntimeException("size of the solution does not match the number of weights required for the NN");
        }

        double totalFitness = 0.0;
        Iterator iterator = testingSet.iterator();
        while (iterator.hasNext()) {
            // get the input value from the training set.
            Double[] p = (Double[]) iterator.next();

            // change the Double[] to a double[] for input to the NN.
            double[] input = convertDoubleArray(p);

            // calculate the expected output for the input.
            double[] exp_output = new double[1];
            exp_output[0] = ((Double) function.evaluate(input)).doubleValue();

            // evaluate solution using the NN.
            double[] output = new double[1];
            neuralNetwork.getOutput(input, tmp, output);

            // decrement the totalFitness.
            totalFitness -= Math.pow(exp_output[0] - output[0], 2.0);
        }

        return (double) (totalFitness / testingSet.size());
    }

    private double[] convertDoubleArray(Double[] oldArray) {
        // create memory for the new array.
        double[] newArray = new double[oldArray.length];

        // convert the Double objects into primitive doubles.
        for (int i = 0; i < oldArray.length; i++) {
            newArray[i] = oldArray[i].doubleValue();
        }

        return newArray;
    }

    public Component getDomain() {
        return domain;
    }
    public void setFunction(Function function) {
        this.function = function;
    }

    public Function getFunction() {
        return function;
    }

    public void setTestingSetPercentage(double testingSetPercentage) {
        this.testingSetPercentage = testingSetPercentage;
    }

    public double getTestingSetPercentage() {
        return testingSetPercentage;
    }

    public void setTrainingSetPercentage(double trainingSetPercentage) {
        this.trainingSetPercentage = trainingSetPercentage;
    }

    public double getTrainingSetPercentage() {
        return trainingSetPercentage;
    }

    public Vector getTrainingSet() {
        return trainingSet;
    }

    public void setTrainingSet(Vector trainingSet) {
        this.trainingSet = trainingSet;
    }

    public Vector getTestingSet() {
        return testingSet;
    }

    public void setTestingSet(Vector testingSet) {
        this.testingSet = testingSet;
    }

    public Random getRandom() {
        return random;
    }

    public void setRandom(Random random) {
        this.random = random;
    }

    public void setSampleSetSize(int sampleSetSize) {
        this.sampleSetSize = sampleSetSize;
    }

    public int getSampleSetSize() {
        return sampleSetSize;
    }

    public void setNeuralNetwork(NN neuralNetwork) {
        this.neuralNetwork = neuralNetwork;
        domain = new Compound(neuralNetwork.getNumberOfWeights(), new Continuous(-1.0, 1.0));
    }

    public NN getNeuralNetwork() {
        return neuralNetwork;
    }
}