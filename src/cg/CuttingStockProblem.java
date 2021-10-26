package cg;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class CuttingStockProblem {

    int nDemandTypes = 0;
    double boardLength = 0;
    double[] demandSize;
    double[] demandQuantity;

    void readCuttingStockProblem(String filepath) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(filepath))) {
            String line = br.readLine();
            // read board width
            if (line != null) {
                boardLength = Double.parseDouble(line.trim());
                line = br.readLine();
            }
            // read number of demand types
            if (line != null) {
                nDemandTypes = Integer.parseInt(line.trim());
                demandSize = new double[nDemandTypes];
                demandQuantity = new double[nDemandTypes];
                line = br.readLine();
            }
            if (line != null) {
                String[] seq = line.trim().split(",");
                if (seq.length != nDemandTypes) {
                    boardLength = 0;
                    return;
                }
                for (int i = 0; i < seq.length; i++) {
                    demandSize[i] = Double.parseDouble(seq[i]);
                }
                line = br.readLine();
            }
            if (line != null) {
                String[] seq = line.trim().split(",");
                if (seq.length != nDemandTypes) {
                    boardLength = 0;
                    return;
                }
                for (int i = 0; i < seq.length; i++) {
                    demandQuantity[i] = Double.parseDouble(seq[i]);
                }
            }
        }
    }

    boolean valid() {
        for (double l : demandSize) {
            if (l > boardLength) {
                return false;
            }
        }
        return true;
    }
}
