import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;


public class TFIDFRetriever {

    ////////////////////////////////////////////////////////
    /// CONSTANTS

    private static final String Q0 = "Q0";
    private static final String SYSTEM_NAME = "timur_nataliya_TFIDFEngine";
    static final String TAB = "\t";

    ////////////////////////////////////////////////////////
    /// FIELDS

    long avdl;
    int N;
    Map<String, Long> tokensPerDoc = new HashMap<String, Long>();
    private Map<String, Map<String, Long>> index;
    private Map<String, Long> term2DocFreq = new HashMap<String, Long>();
    private Map<String, Map<String, Long>> docs2terms;
    private boolean stopping = false;
    private boolean lemmaExp = false;
    private boolean pseudoExp = false;
    private int topRelevant = 3;

    private String indexFolder;
    private String resultsFilePrefix;
    private String indexUniFile;
    private String docFreqFile;
    private String doc2tokensFileName;
    private Evaluator evaluator;

    TFIDFRetriever(String indexFolderParam, boolean stopParam, boolean lemmaExpParam, boolean pseudoExpParam) {
        indexFolder = indexFolderParam;
        indexUniFile = indexFolder + "/index-1-grams";
        docFreqFile = indexFolder + "/df-1-grams.txt";
        doc2tokensFileName = indexFolder + "/doc2totaltokens-1-grams";
        stopping = stopParam;
        lemmaExp = lemmaExpParam;
        pseudoExp = pseudoExpParam;
    }


    public void search(String resultsFolder, String queryFile, boolean stemmed) throws IOException {
        resultsFilePrefix = resultsFolder + "results_tfidf";

        if (Files.exists(Paths.get(resultsFolder))) {
            deleteFolderWithFiles(resultsFolder);
            Files.createDirectory(Paths.get(resultsFolder));
        }
        else {
            Files.createDirectory(Paths.get(resultsFolder));
        }

        QueryLoader qloader = new QueryLoader();

        if (!stemmed) {
            qloader.loadQueries(queryFile);
        }
        else {
            qloader.loadStemmedQueries(queryFile);
        }
        Map<Integer, String> queries = qloader.getId2query();

        /* Preload index */
        List<String> ind = Files.readAllLines(Paths.get(indexUniFile), Charset.defaultCharset());
        index = indexFromString(ind.get(0));

        /* Prepare document frequency */
        List<String> docFreq = Files.readAllLines(Paths.get(docFreqFile), Charset.defaultCharset());
        for (String line : docFreq) {
            String[] split = line.split("\t");
            term2DocFreq.put(split[0], Long.parseLong(split[2]));
        }

        /* Preload document to number of tokens - Calculate average document length, get number of documents */
        List<String> doc2tokenFile = Files.readAllLines(Paths.get(doc2tokensFileName), Charset.defaultCharset());
        tokensPerDoc = doc2tokensFromString(doc2tokenFile.get(0));
        int totalTokens = 0;
        for (Long tokens : tokensPerDoc.values()) {
            totalTokens += tokens;
        }
        avdl = totalTokens / tokensPerDoc.size();
        N = tokensPerDoc.size();

        if (lemmaExp) {
            InflectionalQueryExpander.readMappings();
            Map<Integer, String> newQueries = new HashMap<Integer, String>();

            for (Map.Entry<Integer, String> entry : queries.entrySet()) {
                String newQuery = InflectionalQueryExpander.getExpansion(entry.getValue());
                newQueries.put(entry.getKey(), newQuery);
            }
            queries = newQueries;
        }




        /* Prepare document 2 term */
        docs2terms = new HashMap<String, Map<String, Long>>();
        Set<Map.Entry<String, Map<String, Long>>> entries = index.entrySet();
        for (Map.Entry<String, Map<String, Long>> e : entries){
            String term = e.getKey();
            for (Map.Entry<String, Long> posting : e.getValue().entrySet()) {
                String docId = posting.getKey();
                Long tf = posting.getValue();
                Map<String, Long> term2tf = docs2terms.get(docId);
                if (term2tf == null) {
                    term2tf = new HashMap<String, Long>();
                    term2tf.put(term, tf);
                    docs2terms.put(docId, term2tf);
                }
                else {
                    term2tf.put(term, tf);
                }
            }
        }


        for (Map.Entry<Integer, String> e : queries.entrySet()) {

            Integer queryId = e.getKey();
            // Break query into terms
            String query = e.getValue();

            List<ScoredDoc> rankedDocuments = getScoredDocs(query);

            // Get relevant file names
            List<String> rankedDocsNames = new ArrayList<String>();
            for (ScoredDoc rankedDoc : rankedDocuments) {
                rankedDocsNames.add(rankedDoc.docId);
            }

            if (pseudoExp) {
                String newQuery = PRQueryExpander.getExpandedQuery(index, query, rankedDocsNames, topRelevant, docs2terms, N);
                rankedDocuments = getScoredDocs(newQuery);
            }


            /* Prepare output */
            StringBuilder sb = new StringBuilder();

            for (int i = 0; i < rankedDocuments.size(); i++) {
                ScoredDoc rankedDoc = rankedDocuments.get(i);

                sb.append(queryId).append(TAB).append(Q0).append(TAB).append(rankedDoc.docId.replace(".txt", "")).append(TAB).
                        append(i + 1).append(TAB).append(rankedDoc.CosineSim).append(TAB).
                        append(SYSTEM_NAME).append("\n");
            }

            String queryResultsFileName = resultsFilePrefix + "_queryId_" + queryId;
            Files.deleteIfExists(Paths.get(queryResultsFileName));
            Files.createFile(Paths.get(queryResultsFileName));
            Files.write(Paths.get(queryResultsFileName), sb.toString().getBytes(), StandardOpenOption.APPEND);

            // Get relevant file names

            List<String> evaluationNames = new ArrayList<String>();
            for (ScoredDoc rankedDoc : rankedDocuments) {
                evaluationNames.add(rankedDoc.docId);
            }

            evaluator.evaluate(queryId, evaluationNames);
        }
    }

    private List<ScoredDoc> getScoredDocs(String query) {
        String[] qTerms = query.split(" ");
        List<String> queryTerms = new ArrayList<String>();
        Collections.addAll(queryTerms, qTerms);

        if (stopping) {
            queryTerms = StopWordRemover.removeStopWords(queryTerms);
        }

        // Map of documents to term stats in them (term stats is term to term frequency in a document
        Map<String, Map<String, Long>> doc2terms = new HashMap<String, Map<String, Long>>();
        /* Map of terms to general term stats, (each QueryTermStat contains query tf, document frequency) */
        Map<String, QueryTermStat> queryStat = new HashMap<String, QueryTermStat>();

        for (String term : queryTerms) {

            // Get term's inverted list from index
            Map<String, Long> invertedList = index.get(term);
            if (invertedList == null) {
                continue;
            }

            // For each document in the index, collect term frequency
            for (String doc : invertedList.keySet()) {
                Map<String, Long> term2tf = doc2terms.get(doc);
                if (term2tf == null) {
                    term2tf = new HashMap<String, Long>();
                    term2tf.put(term, invertedList.get(doc));
                } else {
                    term2tf.put(term, invertedList.get(doc));
                }
                doc2terms.put(doc, term2tf);

            }
            queryStat.put(term, new QueryTermStat(StringUtils.countMatches(query, term), invertedList.size()));
        }

        List<ScoredDoc> rankedDocuments = new ArrayList<ScoredDoc>();


            /* Calculate tfidf each document in a map */
        for (String doc : doc2terms.keySet()) {
            Map<String, Long> term2tf = doc2terms.get(doc);
            double TFIDF = CosineSim(term2tf, doc, queryStat);
            rankedDocuments.add(new ScoredDoc(doc, TFIDF));
        }

            /* Sort scored documents */
        Collections.sort(rankedDocuments, new ScoredDocumentsComparator());
        rankedDocuments = rankedDocuments.subList(0,Math.min(rankedDocuments.size(), 100));
        return rankedDocuments;
    }

    private double CosineSim(Map<String, Long> term2tf, String docId, Map<String, QueryTermStat> queryStat) {

        double sum_of_multiples = 0;

        for (Map.Entry<String, QueryTermStat> qe : queryStat.entrySet()) {
            String term = qe.getKey();

            // frequency of term in query
            int qf = queryStat.get(term).queryFrequency;
            double queryTermWeight = queryTermWeight(queryStat, qf);

            // frequency of term in document
            Long f = term2tf.get(term);
            if (f == null) {
                continue;
            }
            double termWeight = docTermWeight(docId, f, queryStat.get(term).docs * 1d);
            sum_of_multiples += termWeight * queryTermWeight;

        }

        //double cosine_sim = sum_of_multiples / Math.sqrt(sum_squares_doc * sum_squares_query);
        double cosine_sim = sum_of_multiples;
        return cosine_sim;
    }

    private double queryTermWeight(Map<String, QueryTermStat> queryStat, int qf) {
        double qik_up = (Math.log(qf) + 1); //no idf, since single query
        double qik_down = calculateQueryNormalizer(queryStat);
        return qik_up/qik_down;
        //return qik_up;
    }

    private double docTermWeight(String docId, Long f, double docFreq) {
        double idf = Math.log(N/docFreq) + 1d;
        double dik_up = (Math.log(f) + 1) * idf;
        double dik_down = calculateNormalizer(docId, N);
        return dik_up/dik_down;
    }

    private static double calculateQueryNormalizer(Map<String, QueryTermStat> queryStat) {
        double sumOfSquares = 0;

        for (Map.Entry<String, QueryTermStat> term2stat : queryStat.entrySet()) {
            double termF = term2stat.getValue().queryFrequency * 1d;
            sumOfSquares += (Math.log(termF)+1);
        }

        return Math.sqrt(sumOfSquares);
    }

    private double calculateNormalizer(String docId, Integer N) {
        Set<Map.Entry<String, Long>> entries = docs2terms.get(docId).entrySet();
        double sumOfSquares = 0;
        for (Map.Entry<String, Long> term2f : entries) {
            String term = term2f.getKey();
            Long termF = term2f.getValue();
            double docFreq = term2DocFreq.get(term) * 1d;
            sumOfSquares += (Math.log(termF)+1) * (Math.log(N/docFreq) + 1);
        }

        return Math.sqrt(sumOfSquares);
    }

    static Map<String, Map<String, Long>> indexFromString(String map){
        map = map.replaceFirst("\\{", "").replace("}}", "");
        String[] submaps = map.split("}, ") ;
        Map<String, Map<String, Long>> index = new HashMap<String, Map<String, Long>>();
        for (String submap : submaps) {
            String[] el = submap.split("\\{") ;
            String word = el[0].substring(0, el[0].length() - 1);
            String[] docs = submap.substring(word.length()+2).split(", ") ;
            Map<String, Long> list = new HashMap<String, Long>();
            for (String doc : docs) {
                String[] docS = doc.split("=");
                String id = docS[0];
                Long tf = Long.parseLong(docS[1]);
                list.put(id, tf);
            }
            index.put(word, list);
        }
        return index;
    }

    static Map<String, Long> doc2tokensFromString(String map){
        map = map.replace("{", "").replace("}", "");
        String[] elements = map.split(", ") ;
        Map<String, Long> docs2tokens = new HashMap<String, Long>();
        for (String element : elements) {
            String[] docS = element.split("=");
            String id = docS[0];
            Long tokens = Long.parseLong(docS[1]);
            docs2tokens.put(id, tokens);
        }

        return docs2tokens;
    }

    static class ScoredDoc {
        public ScoredDoc(String docId, double CosineSim) {
            this.docId = docId;
            this.CosineSim = CosineSim;
        }
        String docId;
        double CosineSim;
    }

    static class ScoredDocumentsComparator implements Comparator<ScoredDoc> {

        @Override
        public int compare(ScoredDoc o1, ScoredDoc o2) {
            if (o1.CosineSim > o2.CosineSim) {
                return -1;
            }
            else if (o1.CosineSim < o2.CosineSim) {
                return 1;
            }
            else {
                return 0;
            }
        }
    }

    static class QueryTermStat {
        public QueryTermStat (Integer qf, Integer docs) {

            this.queryFrequency = qf;
            this.docs = docs;
        }

        Integer queryFrequency;
        Integer docs;
    }

    static void deleteFolderWithFiles(String folder) throws IOException {
        Files.walkFileTree(Paths.get(folder), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException ex) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {

                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
        });
        Files.deleteIfExists(Paths.get(folder));
    }

    public void setEvaluator(Evaluator evaluator) {
        this.evaluator = evaluator;
    }

}