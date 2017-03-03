import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

public class Searcher {

    private static String relevanceJudgeFile = "ProjectData/data/cacm.rel";
    private static String evaluationSummaryFile = "ProjectData/results/EvaluationSummary";
    private static String resultsFolder = "ProjectData/results/";

    public static void main(String[] args) throws IOException {

        /* DELETE RESULTS FOLDER */
        if (Files.exists(Paths.get("ProjectData/results"))) {
            deleteFolderWithFiles("ProjectData/results");
        }
        Files.createDirectory(Paths.get("ProjectData/results"));


    	Path s = Paths.get(evaluationSummaryFile);

        try {
            Files.deleteIfExists(s);
            Files.createFile(s);
        } catch (IOException e) {
            System.err.println("Failed to create evaluation summary files");
            e.printStackTrace();
        }

        String rawCorpusFolder = "ProjectData/data/cacm/";
        String rawStemmedCorpus = "ProjectData/data/stemmed/cacm_stem.txt";

        String corpusFolder = "ProjectData/data/cacm_cleaned/";
        String corpusFolderStemmed = "ProjectData/data/cacm_cleaned_stemmed/";

        String corpusIndex = "ProjectData/indexes/corpusIndex";
        String corpusIndexLucene = "ProjectData/indexes/corpusIndexLucene";
        String stemmedCorpusIndex = "ProjectData/indexes/stemmedCorpusIndex";

        String stemmedQueryFile = "ProjectData/data/stemmed/cacm_stem.query.txt";
        String queryFile = "ProjectData/data/cacm.query";
        String stopWordsFile = "ProjectData/data/common_words";


        if (Files.exists(Paths.get("ProjectData/indexes"))) {
            deleteFolderWithFiles("ProjectData/indexes");
        }
        Files.createDirectory(Paths.get("ProjectData/indexes"));

        cleanCorpuses(rawCorpusFolder, rawStemmedCorpus, corpusFolder, corpusFolderStemmed);
        indexCorpuses(corpusFolder, corpusFolderStemmed, corpusIndex, stemmedCorpusIndex);

        StopWordRemover.getStopWords(stopWordsFile);

        String[] resultSubFolders = {"BM25_BASE/", "TFIDF_BASE/", "Lucene_BASE/", "TFIDF_InflecExp/", "TFIDF_PseudoExp/",
                "TFIDF_Stopping/", "TFIDF_Stemmed/", "Lucene_Stopping/"};

        baseRuns(corpusFolder, corpusIndex, corpusIndexLucene, resultsFolder, queryFile, resultSubFolders);
        queryExpansionRuns(corpusIndex, resultsFolder, queryFile, resultSubFolders);
        stoppingAndStemRuns(corpusIndex, stemmedCorpusIndex, resultsFolder, stemmedQueryFile, queryFile, resultSubFolders);
        otherImplementationRun(corpusIndexLucene, resultsFolder, resultSubFolders, corpusFolder, queryFile);

    }

    public static void otherImplementationRun(String corpusIndexLucene, String resultsFolder, String[] resultSubFolders,
                                              String corpusFolder, String queryFile) {
    /* LUCENE WITH STOPPING AT QUERY */
        try {
            System.out.println("Starting Lucene with stopping run");
            LuceneSearcher luceneSearcher = new LuceneSearcher(corpusIndexLucene, resultsFolder + resultSubFolders[7],
                    corpusFolder, queryFile, true);
            Evaluator evaluator = buildEvaluator();
            luceneSearcher.setEvaluator(evaluator);
            luceneSearcher.search();
            storeEvaluation(evaluator, resultSubFolders[7]);
            System.out.println("Finished Lucene with stopping run");
        } catch (Exception e) {
            System.out.println("Lucene with stopping run failed");
            e.printStackTrace();
        }
    }

    public static void stoppingAndStemRuns(String corpusIndex, String stemmedCorpusIndex, String resultsFolder,
                                           String stemmedQueryFile, String queryFile, String[] resultSubFolders) {
    /* TFIDF WITH STOPPING AT QUERY */
        try {
            System.out.println("Starting TFIDF with stopping run");
            TFIDFRetriever tfidfRetriever = new TFIDFRetriever(corpusIndex, true /*stopping */ ,
                    false /*inflectional expansion*/ , false /*pseudo-relevance expansion */ );
            Evaluator evaluator = buildEvaluator();
            tfidfRetriever.setEvaluator(evaluator);
            tfidfRetriever.search(resultsFolder + resultSubFolders[5], queryFile, false);
            storeEvaluation(evaluator, resultSubFolders[5]);
            System.out.println("Finished TFIDF with stopping run");
        } catch (Exception e) {
            System.out.println("TFIDF with stopping run failed");
            e.printStackTrace();
        }


         /* TFIDF WITH STEMMED CORPUS */
        try {
            System.out.println("Starting TFIDF with stemmed corpus run");
            TFIDFRetriever tfidfRetriever = new TFIDFRetriever(stemmedCorpusIndex, false /* stopping */,
                    false /*inflectional expansion*/, false /* pseudo-relevance expansion */);
            Evaluator evaluator = buildEvaluator();
            tfidfRetriever.setEvaluator(evaluator);
            tfidfRetriever.search(resultsFolder + resultSubFolders[6], stemmedQueryFile, true);
            storeEvaluation(evaluator, resultSubFolders[6]);
            System.out.println("Finished TFIDF with stemmed corpus run");
        } catch (Exception e) {
            System.out.println("TFIDF with stemmed corpus run failed");
            e.printStackTrace();
        }
    }

    public static void queryExpansionRuns(String corpusIndex, String resultsFolder, String queryFile, String[] resultSubFolders) {
    /* TFIDF WITH INFLECTIONAL QUERY EXPANSION */
        try {
            System.out.println("Starting TFIDF with query expansion (Inflectional) run");
            TFIDFRetriever tfidfRetriever = new TFIDFRetriever(corpusIndex, false /* stopping */,
                    true /*inflectional expansion*/, false /* pseudo-relevance expansion */);
            Evaluator evaluator = buildEvaluator();
            tfidfRetriever.setEvaluator(evaluator);
            tfidfRetriever.search(resultsFolder + resultSubFolders[3], queryFile, false);
            storeEvaluation(evaluator, resultSubFolders[3]);
            System.out.println("Finished TFIDF query expansion (Inflectional) run");
        } catch (Exception e) {
            System.out.println("TFIDF with query expansion (Inflectional) run failed");
            e.printStackTrace();
        }


         /* TFIDF WITH RELEVANCE FEEDBACK EXPANSION */
        try {
            System.out.println("Starting TFIDF with query expansion (Pseudo-relevance feedback) run");
            TFIDFRetriever tfidfRetriever = new TFIDFRetriever(corpusIndex, false /* stopping */,
                    false /*inflectional expansion*/, true /* pseudo-relevance expansion */);
            Evaluator evaluator = buildEvaluator();
            tfidfRetriever.setEvaluator(evaluator);
            tfidfRetriever.search(resultsFolder + resultSubFolders[4], queryFile, false);
            storeEvaluation(evaluator, resultSubFolders[4]);
            System.out.println("Finished TFIDF query expansion (Pseudo-relevance feedback) run");
        } catch (Exception e) {
            System.out.println("TFIDF with query expansion (Pseudo-relevance feedback) run failed");
            e.printStackTrace();
        }
    }

    public static void baseRuns(String corpusFolder, String corpusIndex, String corpusIndexLucene, String resultsFolder,
                                String queryFile, String[] resultSubFolders) {
        /* BM25 */
        try {
            System.out.println("Starting BM25 base run");
            BM25Retriever bm25Searcher = new BM25Retriever(resultsFolder + resultSubFolders[0], corpusIndex,
                    relevanceJudgeFile, corpusFolder);
            Evaluator evaluator = buildEvaluator();
            bm25Searcher.setEvaluator(evaluator);
            bm25Searcher.search(queryFile);
            storeEvaluation(evaluator, resultSubFolders[0]);
            System.out.println("Finished BM25 base run");
        } catch (Exception e) {
            System.out.println("BM25 base run failed");
            e.printStackTrace();
        }


         /* TFIDF */
        try {
            System.out.println("Starting TFIDF base run");
            TFIDFRetriever tfidfRetriever = new TFIDFRetriever(corpusIndex, false /* stopping */,
                    false /*inflectional expansion*/, false /* pseudo-relevance expansion */);
            Evaluator evaluator = buildEvaluator();
            tfidfRetriever.setEvaluator(evaluator);
            tfidfRetriever.search(resultsFolder + resultSubFolders[1], queryFile, false);
            storeEvaluation(evaluator, resultSubFolders[1]);
            System.out.println("Finished TFIDF base run");
        } catch (Exception e) {
            System.out.println("TFIDF base run failed");
            e.printStackTrace();
        }


         /* LUCENE */
        try {
            System.out.println("Starting Lucene base run");
            LuceneSearcher luceneSearcher = new LuceneSearcher(corpusIndexLucene, resultsFolder + resultSubFolders[2],
                    corpusFolder, queryFile, false /* stopping */);
            Evaluator evaluator = buildEvaluator();
            luceneSearcher.setEvaluator(evaluator);
            luceneSearcher.search();
            storeEvaluation(evaluator, resultSubFolders[2]);
            System.out.println("Finished Lucene base run");
        } catch (Exception e) {
            System.out.println("Lucene base run failed");
            e.printStackTrace();
        }
    }

    public static void indexCorpuses(String corpusFolder, String corpusFolderStemmed, String corpusIndex, String stemmedCorpusIndex) {
    /* INDEX ALL */
        try {
            System.out.println("Indexing corpus");
            Indexer.index(corpusFolder, corpusIndex, true);
            System.out.println("Indexing corpus finished");
        } catch (Exception e) {
            System.out.println("Indexing corpus failed");
            e.printStackTrace();
        }


        try {
            System.out.println("Indexing stemmed corpus");
            Indexer.index(corpusFolderStemmed, stemmedCorpusIndex, false);
            System.out.println("Indexing stemmed corpus finished");
        } catch (Exception e) {
            System.out.println("Indexing stemmed corpus failed");
            e.printStackTrace();
        }
    }

    public static void cleanCorpuses(String rawCorpusFolder, String rawStemmedCorpus, String corpusFolder, String corpusFolderStemmed) {
    /* CLEAN ALL */
        try {
            System.out.println("Cleaning corpus");
            CorpusCleaner.cleanCorpus(corpusFolder, rawCorpusFolder);
            System.out.println("Cleaning corpus finished");
        } catch (Exception e) {
            System.out.println("Cleaning corpus failed");
            e.printStackTrace();
        }


        try {
            System.out.println("Cleaning stemmed corpus");
            StemmedCorpusCleaner.cleanCorpus(corpusFolderStemmed, rawStemmedCorpus);
            System.out.println("Cleaning stemmed corpus finished");
        } catch (Exception e) {
            System.out.println("Cleaning stemmed corpus failed");
            e.printStackTrace();
        }
    }

    public static Evaluator buildEvaluator() {
        Evaluator evaluator = null;
        try {
            evaluator = new Evaluator(relevanceJudgeFile);
        } catch (IOException e) {
            System.out.println("Failed to instantiate evaluator");
            e.printStackTrace();
        }
        return evaluator;
    }

    public static void storeEvaluation(Evaluator evaluator, String resultsSubFolder) throws Exception {
        StringBuilder sb = new StringBuilder();

        double MAP = evaluator.getMAP();
        double MRR = evaluator.getMRR();
        Double[] precAtK = evaluator.getPrecisionAtK();
        sb.append(resultsSubFolder.replace("/", "")).append("\n");
        sb.append("Mean average precision: " + String.format("%.5f", MAP)).append("\n");
        sb.append("Mean precision at K (5, 20): ").append("(").
                append(String.format("%.5f", precAtK[0])).append(", ").
                append(String.format("%.5f", precAtK[1])).append(")").append("\n");

        sb.append("Mean reciprocal rank: " + String.format("%.5f", MRR)).append("\n").append("\n");
        Files.write(Paths.get(evaluationSummaryFile), sb.toString().getBytes(), StandardOpenOption.APPEND);
        evaluator.writePrecisionAndRecall(resultsFolder + resultsSubFolder);
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


}