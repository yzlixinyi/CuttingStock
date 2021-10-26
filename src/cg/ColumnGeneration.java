package cg;

import ilog.concert.*;
import ilog.cplex.*;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ColumnGeneration {

    static final double EPSILON = 1.0E-6;
    static final DecimalFormat df3 = new DecimalFormat("0.000");
    static final DecimalFormat df6 = new DecimalFormat("0.000000");

    /**
     * Cutting stock problem. min sum(x_p) : Ax >= d
     */
    IloCplex cutSolver;
    /**
     * All cutting patterns generated.
     */
    List<IloNumVar> patterns;
    /**
     * Ax >= d
     */
    List<int[]> matA;
    /**
     * Dual prices of patterns
     */
    double[] price;

    /**
     * Pattern generation problem. min 1-y^T·a_p : l^T·a_p <= L
     */
    IloCplex patSolver;
    /**
     * a new pattern a_p = [a_1p, a_2p, ..., a_np]^T
     */
    IloNumVar[] use;

    int iteration;

    public void solveCuttingStock(CuttingStockProblem problem) throws IloException {

        // Cutting stock problem
        cutSolver = new IloCplex();
//        cutSolver.setParam(IloCplex.Param.MIP.Display, 0); // No display until optimal solution has been found
        cutSolver.setOut(null); // Turn off all logging output
//        cutSolver.setParam(IloCplex.Param.RootAlgorithm, IloCplex.Algorithm.Primal); // use Primal Simplex to solve LP

        // objective: minimize boards used
        IloObjective boardsUsed = cutSolver.addMinimize();

        // Range of demands
        IloRange[] demandsToFill = new IloRange[problem.nDemandTypes];
        for (int i = 0; i < demandsToFill.length; i++) {
            demandsToFill[i] = cutSolver.addRange(problem.demandQuantity[i], Double.MAX_VALUE);
        }

        patterns = new ArrayList<>();
        matA = new ArrayList<>();

        // Initialize n patterns with a_ii = L / l_i, a_ij = 0
        for (int i = 0; i < problem.nDemandTypes; i++) {
            int a_ii = (int) (problem.boardLength / problem.demandSize[i]);
            int[] primalUse = new int[problem.nDemandTypes];
            primalUse[i] = a_ii;
            matA.add(primalUse);
            // Creates and returns a column from the specified objective and value.
            IloColumn delta_obj = cutSolver.column(boardsUsed, 1.0);
            // Creates and returns a column from the specified range and value.
            IloColumn delta_d_i = cutSolver.column(demandsToFill[i], a_ii);
            // variable x_i: times that pattern a_i is applied
            patterns.add(cutSolver.numVar(delta_obj.and(delta_d_i), 0.0, Double.MAX_VALUE));
        }


        // Pattern generation problems
        patSolver = new IloCplex();
        patSolver.setParam(IloCplex.Param.MIP.Display, 0); // no progress reports (interpreting the node log)

        IloObjective reducedCost = patSolver.addMinimize();
        // a new pattern a_p = [a_1p, a_2p, ..., a_np]^T
        use = patSolver.numVarArray(problem.nDemandTypes, 0, Double.MAX_VALUE, IloNumVarType.Int);
        // cutting feasibility: l^T·a_p <= L
        patSolver.addRange(-Double.MAX_VALUE, patSolver.scalProd(problem.demandSize, use), problem.boardLength);

        iteration = 0;
        do {
            iteration++;
            cutSolver.solve();

            // find and add a new pattern
            double[] newPattern;

            // y^T = c_B·B^{-1}
            price = cutSolver.getDuals(demandsToFill);
            // obj = 1 - y^T·a_p
            reducedCost.setExpr(patSolver.diff(1., patSolver.scalProd(use, price)));

            // report usage of patterns and dual price
            reportPatternUsage();

            patSolver.solve();
            if (patSolver.getObjValue() <= -EPSILON) {
                newPattern = patSolver.getValues(use);
                matA.add(Arrays.stream(newPattern).mapToInt(i -> (int) i).toArray());
                // report pattern generation problem
                reportNewPattern();
            } else {
                break;
            }

            // add new column for the generated pattern
            IloColumn column = cutSolver.column(boardsUsed, 1.0); // delta_obj
            for (int i = 0; i < newPattern.length; i++) {
                column = column.and(cutSolver.column(demandsToFill[i], newPattern[i])); // delta_d_i
            }
            patterns.add(cutSolver.numVar(column, 0, Double.MAX_VALUE));

        } while (true);

        // column-generation phase terminates, type of the solution should be changed from continuous to integer
        for (IloNumVar pattern : patterns) {
            cutSolver.add(cutSolver.conversion(pattern, IloNumVarType.Int));
        }

        cutSolver.solve();

        reportBestCuttingStrategy();

        cutSolver.end();
        patSolver.end();
    }

    /**
     * report total usage of boards and usage of patterns of the best cutting strategy,
     * print round-up solution and corresponding patterns
     */
    private void reportBestCuttingStrategy() throws IloException {
        System.out.println("\n------------------------------------------------------");
        System.out.println("Solution status: " + cutSolver.getStatus());
        // report total usage of boards and usage of patterns of the best cutting strategy
        System.out.println("A total of " + patterns.size() + " patterns are generated:");
        for (int p = 0; p < patterns.size(); p++) {
            System.out.print("\nPat " + p + ":" + "\t");
            for (int cut: matA.get(p)) {
                System.out.print(cut + "\t");
            }
        }
        // print round-up solution and corresponding patterns
        System.out.print("\nBest integer solution uses " + cutSolver.getObjValue() + " rolls");
        System.out.println();
        for (int j = 0; j < patterns.size(); j++) {
            System.out.print("\nPattern " + j + " = " + cutSolver.getValue(patterns.get(j)) + " ");
            if (cutSolver.getValue(patterns.get(j)) > 0.0) {
                System.out.print(Arrays.toString(matA.get(j)));
            }
        }
    }

    /**
     * report reduced cost and new pattern generated
     */
    private void reportNewPattern() throws IloException {
        System.out.println("Reduced cost is " + df6.format(patSolver.getObjValue()));
        for (int i = 0; i < use.length; i++) {
            System.out.printf("Type %d cut = %s%n", i, (int) patSolver.getValue(use[i]));
        }
    }

    /**
     * report usage of patterns and dual price
     */
    private void reportPatternUsage() throws IloException {
        System.out.println("\n>> Iteration " + iteration);
        System.out.println("Using " + df3.format(cutSolver.getObjValue()) + " boards.");
        for (int i = 0; i < patterns.size(); i++) {
            System.out.printf("Pattern %d n_cut = %s%n", i, df3.format(cutSolver.getValue(patterns.get(i))));
        }
        for (int i = 0; i < price.length; i++) {
            System.out.printf("Type %d price = %s%n", i, df3.format(price[i]));
        }
    }

}
