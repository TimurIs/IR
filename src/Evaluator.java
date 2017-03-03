import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Evaluator {
    public Map<Integer, List<String>> relevanceMap = new HashMap<Integer, List<String>>();
    private List<Double> rr = new ArrayList<Double>();

    private Map<Integer, List<Double>> precisionMap = new HashMap<Integer, List<Double>>();
    private Integer[] K = {5, 20};
    private Map<Integer, Double[]> precisionAtMap = new HashMap<Integer, Double[]>();
    private Map<Integer, List<Double>> recallMap = new HashMap<Integer, List<Double>>();
    private Map<Integer, List<Double>> relevantPrecisionMap = new HashMap<Integer, List<Double>>();
    private Map<Integer, List<Integer>> relevantInfoPerQuery = new HashMap<Integer, List<Integer>>();
    

    /**
     * @param filePath - path to the folder, where the information about relevance is stored
     * @throws IOException
     */
    public Evaluator(String filePath) throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(filePath), Charset.defaultCharset());

        for (String line : lines) {
            String[] relInfo = line.split(" Q0 ");
            Integer qId = Integer.parseInt(relInfo[0]);
            List<String> mapValue = this.relevanceMap.get(qId);
            String fileNameRaw = relInfo[1].split(" ")[0];
            String[] split = fileNameRaw.split("-");
            Integer docNum = Integer.parseInt(split[1]);
            String fileName = split[0] + "-" + String.format("%04d", docNum);


            if (mapValue == null) {
                List<String> relevantNames = new ArrayList<String>();

                relevantNames.add(fileName);
                relevanceMap.put(qId, relevantNames);
            } else {
                mapValue.add(fileName);
            }

        }

    }
    
    public double getAvP()
    {
    	double res = 0;
    	for(List<Double> list: this.precisionMap.values())
    	{
    		for(Double d: list)
    		{
    			res += d;
    		}
    	}
    	return res;
    }

    public void evaluate(Integer qId, List<String> rankedDocuments) {
        if (relevanceMap.get(qId) == null) {
            return;
        }

        List<String> cleanedRankedDocuments = new ArrayList<String>();

        for (String rankedDocument : rankedDocuments) {
            rankedDocument = rankedDocument.replaceAll(".html", "");
            cleanedRankedDocuments.add(rankedDocument);
        }

        List<String> relevantDocuments = this.relevanceMap.get(qId);
        calculateReciprocalRank(relevantDocuments, cleanedRankedDocuments);
        calculatePrecisionAndRecall(qId, relevantDocuments, cleanedRankedDocuments);
    }

    private void calculatePrecisionAndRecall(Integer qId, List<String> relevantDocuments, List<String> rankedDocuments) {
        int relRetr = 0;
        int retr = 0;
        List<Double> precision = new ArrayList<Double>();
        List<Double> relPrecision = new ArrayList<Double>();
        List<Integer> relInfo = new ArrayList<Integer>();
        Double[] precAtK = new Double[2];
        int KRank = 0;
        
        for (String rankedDocument : rankedDocuments) {
            retr++;
            if (relevantDocuments.contains(rankedDocument)) {
                relRetr++;
                relPrecision.add((double) relRetr / retr);
                relInfo.add(1);
            }
            else
            {
            	relInfo.add(0);
            }
            precision.add((double) relRetr / retr);

            if (KRank < precAtK.length && retr == K[KRank]) {
                precAtK[KRank] = (double) relRetr / retr;
                KRank++;
            }
        }


        this.precisionAtMap.put(qId, precAtK);
        this.precisionMap.put(qId, precision);
        this.relevantPrecisionMap.put(qId, relPrecision);
        this.relevantInfoPerQuery.put(qId, relInfo);

        relRetr = 0;
        int rel = relevantDocuments.size();
        List<Double> recall = new ArrayList<Double>();
        for (String rankedDocument : rankedDocuments) {
            if (relevantDocuments.contains(rankedDocument))
                relRetr++;
            recall.add((double) relRetr / rel);
        }

        this.recallMap.put(qId, recall);
    }

    private void calculateReciprocalRank(List<String> relevantDocuments, List<String> rankedDocuments) {
        Integer rank = Integer.MAX_VALUE;
        for (String relDoc : relevantDocuments) {
            if (rankedDocuments.contains(relDoc)) {
                int newRank = rankedDocuments.indexOf(relDoc) + 1;
                rank = (newRank < rank) ? newRank : rank;
            }
        }

        if (rank == Integer.MAX_VALUE) {
            rr.add(0.0);
        } else {
            rr.add(1.0 / rank);
        }
    }

    public double getMRR() {
        if (rr.isEmpty())
            return -1;
        double mrr = 0;

        for (double d : rr) {
            mrr += d;
        }

        return mrr / rr.size();
    }

    public double getMAP() {
        if (this.relevantPrecisionMap.isEmpty())
            return 0.0;

        double map = 0;
        for (Integer qId : this.relevantPrecisionMap.keySet()) {
            double ap = 0;
            List<Double> precision = this.relevantPrecisionMap.get(qId);

            if (precision.size() > 0) {
                for (Double d : precision)
                    ap += d;

                ap /= precision.size();
            }
            else {
                ap = 0;
            }
            map += ap;
        }

        return map / this.relevantPrecisionMap.size();
    }

    public Double[] getPrecisionAtK() {
        if (precisionAtMap.isEmpty()) {
            return new Double[] {0d, 0d};
        }


        double precAtK5 = 0;
        double precAtK10 = 0;

        Double[] patk = new Double[2];
        for (Integer qId : precisionAtMap.keySet()) {

            Double[] precAtK = precisionAtMap.get(qId);
            if (precAtK[0] != null) {
                precAtK5 += precAtK[0];
            }
            if (precAtK[1] != null) {
                precAtK10 += precAtK[1];
            }

        }

        patk[0] = precAtK5 / precisionAtMap.size();
        patk[1] = precAtK10 / precisionAtMap.size();
        return patk;
    }

    public void writePrecisionAndRecall(String folderPath) throws Exception {
        File folder = new File(folderPath);

        if (!folder.exists()) {
            if (!folder.mkdir())
                throw new Exception("File : " + folderPath + " was not created");
        }

        File subFolder = new File(folderPath + "precision_and_recall_results");

        if (!subFolder.exists()) {
            if (!subFolder.mkdir())
                throw new Exception("File : " + subFolder + " was not created");
        }


        for (Integer qId : this.precisionMap.keySet()) {
            FileWriter fw = new FileWriter(subFolder.getPath() + File.separatorChar + qId.toString() + ".txt");
            fw.write("QueryId" + "\t" + "IsRelevant" + "\t" + "Precision" + "\t" + "Recall"  + "\n");
            int k = 1;
            for (Double d : this.precisionMap.get(qId)) {
                fw.write(k + "\t" + this.relevantInfoPerQuery.get(qId).get(k - 1) + "\t" + d.toString() + "\t" + this.recallMap.get(qId).get(k-1)  + "\n");
                k++;
            }
            fw.close();
        }

        subFolder = new File(folderPath + "AvP_and_RR_and_PAtK_results");
        if (!subFolder.exists()) {
            if (!subFolder.mkdir())
                throw new Exception("Directory : " + subFolder + " was not created");
        }

        int k = 0;
        String resultsForQueries = subFolder.getPath() + File.separatorChar + "AvP_and_RR_and_PAtK" + ".txt";
        Files.deleteIfExists(Paths.get(resultsForQueries));
        Files.createFile(Paths.get(resultsForQueries));
        StringBuilder sb = new StringBuilder();
        sb.append("QueryId").append("\t").append("AvP").append("\t").append("RR").append("\t").
                append("K=5").append("\t").append("K=20").append("\n");

        for (Integer qId : this.relevantPrecisionMap.keySet()) {
         double avp = 0;
            for (Double d : this.relevantPrecisionMap.get(qId)) {
            	avp += d;
            }
            if (avp != 0) {
                avp /= this.relevantPrecisionMap.get(qId).size();
            }

            Double[] pAtK = precisionAtMap.get(qId);

            sb.append(qId).append("\t").append(avp).append("\t").append(this.rr.get(k)).append("\t").
                    append(pAtK[0]).append("\t").append(pAtK[1]).append("\n");

            k++;
        }
        Files.write(Paths.get(resultsForQueries), sb.toString().getBytes(), StandardOpenOption.WRITE);

    }
}