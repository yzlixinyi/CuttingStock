package cg;

import ilog.concert.IloException;

import java.io.IOException;

public class Main {

    public static void main(String[] args) throws IOException, IloException {
        String datafile = "./data/cut_stock.txt";

        CuttingStockProblem problem = new CuttingStockProblem();
        problem.readCuttingStockProblem(datafile);
        if (!problem.valid()) {
            return;
        }
        ColumnGeneration method = new ColumnGeneration();
        method.solveCuttingStock(problem);
    }
}
