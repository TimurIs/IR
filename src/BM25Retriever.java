import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/**
 * Created by nzozulya on 3/26/2016.
 */
public class BM25Retriever {

    ////////////////////////////////////////////////////////
    /// CONSTANTS

    private static final String Q0 = "Q0";
    private static final String SYSTEM_NAME = "timur_nataliya_BM25Engine";
    static final String TAB = "\t";

    ////////////////////////////////////////////////////////
    /// FIELDS

    long avdl;
    int N;
    Map<String, Long> tokensPerDoc = new HashMap<String, Long>();
    Map<Integer, Integer> RInfo = new HashMap<Integer, Integer>();
    Map<Integer, Map<String, Integer>> rInfo = new HashMap<Integer, Map<String, Integer>>();
    private boolean stopping = false;
    private String indexFolder;
    private String resultsFolder;
    private String resultsFilePrefix;
    private String indexUniFile;
    private String doc2tokensFileName;
    private Evaluator evaluator;
    private String relJudgeFile;
    private String corpusFolder;

    BM25Retriever(String resultsFolderParam, String indexFolderParam, String relJudgeFileParam,
                  String corpusFolderParam) throws IOException {
        resultsFolder = resultsFolderParam;
        indexFolder = indexFolderParam;
        resultsFilePrefix = resultsFolder + "results_bm25";
        indexUniFile = indexFolder + "/index-1-grams";
        doc2tokensFileName = indexFolder + "/doc2totaltokens-1-grams";
        relJudgeFile = relJudgeFileParam;
        corpusFolder = corpusFolderParam;
    }

    public void search(String queryFile) throws IOException {
        if (Files.exists(Paths.get(resultsFolder))) {
            deleteFolderWithFiles(resultsFolder);
            Files.createDirectory(Paths.get(resultsFolder));
        } else {
            Files.createDirectory(Paths.get(resultsFolder));
        }

        QueryLoader qloader = new QueryLoader();
        qloader.loadQueries(queryFile);



        /* Preload index */
        List<String> ind = Files.readAllLines(Paths.get(indexUniFile), Charset.defaultCharset());
        Map<String, Map<String, Long>> index = indexFromString(ind.get(0));


        /* Preload relevance info */
        preloadRelevanceInfo();

        /* Preload document to number of tokens - Calculate average document length, get number of documents */
        List<String> doc2tokenFile = Files.readAllLines(Paths.get(doc2tokensFileName), Charset.defaultCharset());
        tokensPerDoc = doc2tokensFromString(doc2tokenFile.get(0));
        int totalTokens = 0;
        for (Long tokens : tokensPerDoc.values()) {
            totalTokens += tokens;
        }
        avdl = totalTokens / tokensPerDoc.size();
        N = tokensPerDoc.size();

        for (Map.Entry<Integer, String> e : qloader.getId2query().entrySet()) {

            // Break query into terms
            String query = e.getValue();
            String[] qTerms = query.split(" ");

            List<String> queryTerms = new ArrayList<String>();
            for (String qTerm : qTerms) {
                queryTerms.add(qTerm);
            }

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
            Integer queryId = e.getKey();
            /* Calculate BM25 each document in a map */
            for (String doc : doc2terms.keySet()) {
                Map<String, Long> term2tf = doc2terms.get(doc);
                double BM25Score = BM25(term2tf, doc, queryStat, queryId);
                rankedDocuments.add(new ScoredDoc(doc, BM25Score));
            }

            /* Sort scored documents */
            Collections.sort(rankedDocuments, new ScoredDocumentsComparator());

            /* Prepare output */
            StringBuilder sb = new StringBuilder();


            rankedDocuments = rankedDocuments.subList(0, Math.min(rankedDocuments.size(), 100));

            for (int i = 0; i < rankedDocuments.size(); i++) {
                ScoredDoc rankedDoc = rankedDocuments.get(i);
                String fileName = rankedDoc.docId;

                sb.append(queryId).append(TAB).append(Q0).append(TAB).append(fileName.replace(".txt", "")).append(TAB).
                        append(i + 1).append(TAB).append(rankedDoc.BM25Score).append(TAB).
                        append(SYSTEM_NAME).append("\n");
            }

            String queryResultsFileName = resultsFilePrefix + "_" + queryId;
            Files.deleteIfExists(Paths.get(queryResultsFileName));
            Files.createFile(Paths.get(queryResultsFileName));
            Files.write(Paths.get(queryResultsFileName), sb.toString().getBytes(), StandardOpenOption.APPEND);

            List<String> evaluationNames = new ArrayList<String>();
            for (ScoredDoc rankedDoc : rankedDocuments) {
                evaluationNames.add(rankedDoc.docId);
            }

            evaluator.evaluate(queryId, evaluationNames);
        }
    }

    private void preloadRelevanceInfo() throws IOException {
        List<String> lines = null;
        try {
            lines = Files.readAllLines(Paths.get(relJudgeFile), Charset.defaultCharset());
        } catch (IOException e) {
            System.err.println("Relevance Judgement file can't be read! " + e.getMessage());
            throw e;
        }

        Map<Integer, List<String>> relevanceMap = new HashMap<Integer, List<String>>();

        for (String line : lines) {
            String[] relInfo = line.split(" Q0 ");
            Integer qId = Integer.parseInt(relInfo[0]);
            List<String> mapValue = relevanceMap.get(qId);
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

        Set<Integer> queryIds = relevanceMap.keySet();
        for (Integer queryId : queryIds) {
            List<String> relDocs = relevanceMap.get(queryId);
            RInfo.put(queryId, relDocs.size());
            for (String relDoc : relDocs) {
                lines = Files.readAllLines(Paths.get(corpusFolder + relDoc + ".html"), Charset.defaultCharset());

                StringBuilder sb = new StringBuilder();
                for (String line : lines) {
                    sb.append(line).append(" ");
                }

                String text = TextCleaner.cleanText(sb.toString());
                String[] split = text.split(" ");
                Set<String> uniqueTerms = new HashSet<String>();
                for (String term : split) {
                    uniqueTerms.add(term);
                }

                for (String term : uniqueTerms) {
                    Map<String, Integer> term2docFreq = rInfo.get(queryId);
                    if (term2docFreq == null) {
                        term2docFreq = new HashMap<String, Integer>();
                        term2docFreq.put(term, 1);
                    }
                    else {
                        Integer oldValue = term2docFreq.get(term);
                        int newDocFreq = oldValue == null ? 1 : oldValue + 1;
                        term2docFreq.put(term, newDocFreq);
                    }
                    rInfo.put(queryId, term2docFreq);
                }

            }

        }
    }

    private double BM25(Map<String, Long> term2tf, String docId, Map<String, QueryTermStat> queryStat, Integer qId) {

        double BM25Score = 0d;

        double k1 = 1.2d;
        double b = 0.75d;
        double k2 = 100d;

        // Document length
        double dl = tokensPerDoc.get(docId) * 1d;
        double K = k1 * ((1 - b) + b * (dl / avdl));

        // Number of relevant documents
        Integer RRaw = RInfo.get(qId);
        Integer R = RRaw == null ? 0 : RRaw;

        for (Map.Entry<String, Long> e : term2tf.entrySet()) {
            // in how many documents term occurs
            String term = e.getKey();
            int n = queryStat.get(term).docs;
            // frequency of term in query
            int qf = queryStat.get(term).queryFrequency;
            // frequency of term in document
            long f = e.getValue();
            int ri = 0;
            if (R != 0) {
                Integer riRaw = rInfo.get(qId).get(term);
                ri = riRaw == null ? 0 : riRaw;
            }
            Double nominator = (ri + 0.5d)/(R - ri + 0.5d);
            double idf_component = Math.log(nominator / ((n -ri + 0.5d) / (N - n - R + ri + 0.5d)));
            BM25Score += idf_component * (((k1 + 1) * f) / (K + f)) * (((k2 + 1) * qf) / (k2 + qf));
        }
        return BM25Score;
    }

    static Map<String, Map<String, Long>> indexFromString(String map) {
        map = map.replaceFirst("\\{", "").replace("}}", "");
        String[] submaps = map.split("}, ");
        Map<String, Map<String, Long>> index = new HashMap<String, Map<String, Long>>();
        for (String submap : submaps) {
            String[] el = submap.split("\\{");
            String word = el[0].substring(0, el[0].length() - 1);
            String[] docs = submap.substring(word.length() + 2).split(", ");
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

    static Map<Integer, String> doc2IdFromString(String map) {
        map = map.replace("{", "").replace("}", "");
        String[] elements = map.split(", ");
        Map<Integer, String> docs2file = new HashMap<Integer, String>();
        for (String element : elements) {
            String[] docS = element.split("=");
            Integer id = Integer.parseInt(docS[0]);
            String name = docS[1];
            docs2file.put(id, name);
        }
        return docs2file;
    }

    static Map<String, Long> doc2tokensFromString(String map) {
        map = map.replace("{", "").replace("}", "");
        String[] elements = map.split(", ");
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
        public ScoredDoc(String docId, double BM25Score) {
            this.docId = docId;
            this.BM25Score = BM25Score;
        }

        String docId;
        double BM25Score;
    }

    static class ScoredDocumentsComparator implements Comparator<ScoredDoc> {

        @Override
        public int compare(ScoredDoc o1, ScoredDoc o2) {
            if (o1.BM25Score > o2.BM25Score) {
                return -1;
            } else if (o1.BM25Score < o2.BM25Score) {
                return 1;
            } else {
                return 0;
            }
        }
    }

    static class QueryTermStat {
        public QueryTermStat(Integer qf, Integer docs) {

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
