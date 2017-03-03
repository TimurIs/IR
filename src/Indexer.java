import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public class Indexer {

    static Map<String, Map<String, Long>> index;
    static List<WordStatistics> wordStat;
    static Map<String, Long> tokensPerDoc;
    static final String TAB = "\t";
    static int n = 1;
    static boolean lemmatize = true;

    public static void main(String[] args) throws IOException {
        processCmdArguments(args);

        String indexFolder = "workFolder/corpusIndex";
        String indexFolderStemmed = "workFolder/corpusIndexStemmed";
        String corpusFolder = "workFolder/data/cacm_cleaned";
        String corpusFolderStemmed = "workFolder/data/cacm_cleaned_stemmed";

        index(corpusFolder, indexFolder, true);
        index(corpusFolderStemmed, indexFolderStemmed, false);
    }


    public static void index(String corpusFolder, String indexFolder, boolean lemmatizeParam) throws IOException {

        index = new HashMap<String, Map<String, Long>>();
        wordStat = new ArrayList<WordStatistics>();
        tokensPerDoc = new HashMap<String, Long>();

        lemmatize = lemmatizeParam;

        String indexFileName = "index-" + n + "-grams";
        String termFrequencyFileName = "tf-" + n + "-grams.txt";
        String docFrequencyFileName = "df-" + n + "-grams.txt";
        String docid2totalToken = "doc2totaltokens-" + n + "-grams";

        prepareFiles(indexFolder, indexFileName, termFrequencyFileName, docFrequencyFileName, docid2totalToken);
        processCorpus(corpusFolder);
        InflectionalQueryExpander.saveLemmas(indexFolder + "/lemmas");
        Files.write(Paths.get(indexFolder + "/" + indexFileName), index.toString().getBytes(), StandardOpenOption.APPEND);
        Files.write(Paths.get(indexFolder + "/" + docid2totalToken), tokensPerDoc.toString().getBytes(), StandardOpenOption.APPEND);
        createFrequencyTables(indexFolder + "/" + termFrequencyFileName, indexFolder + "/" + docFrequencyFileName);
    }

    private static void prepareFiles(String indexFolder, String indexFileName, String termFrequencyFileName,
                                     String docFrequencyFileName, String doc2totalTokens) throws IOException {
        if (!Files.exists(Paths.get(indexFolder))) {
            Files.createDirectory(Paths.get(indexFolder));
            Files.createFile(Paths.get(indexFolder + "/" + indexFileName));
            Files.createFile(Paths.get(indexFolder + "/" + termFrequencyFileName));
            Files.createFile(Paths.get(indexFolder + "/" + docFrequencyFileName));
            Files.createFile(Paths.get(indexFolder + "/" + doc2totalTokens));
        } else {
            Files.deleteIfExists(Paths.get(indexFolder + "/" + indexFileName));
            Files.createFile(Paths.get(indexFolder + "/" + indexFileName));
            Files.deleteIfExists(Paths.get(indexFolder + "/" + termFrequencyFileName));
            Files.createFile(Paths.get(indexFolder + "/" + termFrequencyFileName));
            Files.deleteIfExists(Paths.get(indexFolder + "/" + docFrequencyFileName));
            Files.createFile(Paths.get(indexFolder + "/" + docFrequencyFileName));
            Files.deleteIfExists(Paths.get(indexFolder + "/" + doc2totalTokens));
            Files.createFile(Paths.get(indexFolder + "/" + doc2totalTokens));
        }
    }

    private static void processCmdArguments(String[] args) {
        if (args.length > 0) {
            for (String arg : args) {
                if (arg.equals("lemmas")) {
                    lemmatize = true;
                }
            }
        }
    }

    static void createFrequencyTables(String termFrequencyPath, String docFrequencyPath) throws IOException {

        for (String word : index.keySet()) {
            Map<String, Long> list = index.get(word);
            List<String> docIds = new ArrayList<String>();
            Long tf = 0l;
            Integer docs = 0;
            for (Map.Entry<String, Long> entry : list.entrySet()) {
                tf += entry.getValue();
                docs++;
                docIds.add(entry.getKey());
            }
            WordStatistics wstat = new WordStatistics();
            wstat.word = word;
            wstat.tf = tf;
            wstat.docs = docs;
            wstat.docIds = docIds;
            wordStat.add(wstat);
        }
        Collections.sort(wordStat, new TFComparator());

        StringBuilder sb = new StringBuilder();

        for (WordStatistics wstat : wordStat) {
            sb.append(wstat).append("\n");
        }
        Files.write(Paths.get(termFrequencyPath), sb.toString().getBytes(), StandardOpenOption.WRITE);

        Collections.sort(wordStat, new LexiComparator());
        StringBuilder sb1 = new StringBuilder();
        for (WordStatistics wstat : wordStat) {
            sb1.append(wstat.toDFString()).append("\n");
        }
        Files.write(Paths.get(docFrequencyPath), sb1.toString().getBytes(), StandardOpenOption.WRITE);

    }

    private static void processCorpus(String corpusFolder) throws IOException {
        Files.walkFileTree(Paths.get(corpusFolder), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {

                processFile(file);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void processFile(Path file) throws IOException {
        String fileName = file.getFileName().toString();
        long totalTokens = 0l;
        List<String> lines = Files.readAllLines(file, Charset.defaultCharset());

        StringBuilder sb = new StringBuilder();

        for (String line : lines) {
            sb.append(line).append(" ");
        }

        String text = TextCleaner.cleanText(sb.toString());

        if (lemmatize) {
            InflectionalQueryExpander.collectMappings(text);
        }

        // Split by space
        String[] parts = text.split(" ");

        // Unigram index
        for (String word : parts) {
            addToIndex(fileName, word);
            totalTokens++;
        }


        tokensPerDoc.put(fileName, totalTokens);
    }

    private static void addToIndex(String fileName, String word) {
        Map<String, Long> postings = index.get(word);
        if (postings == null) {
            postings = new HashMap<String, Long>();
            postings.put(fileName, 1l);
            index.put(word, postings);

        } else {
            Long termFrequency = postings.get(fileName);
            if (termFrequency != null) {
                postings.put(fileName, termFrequency + 1);
            } else {
                postings.put(fileName, 1l);
            }
        }
    }

    static void deleteFolderWithFiles(String folder) throws IOException {
        Files.walkFileTree(Paths.get(folder), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
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

    static class WordStatistics {
        String word;
        Long tf;
        Integer docs;
        List<String> docIds = new ArrayList<String>();

        @Override
        public String toString() {
            return word + TAB + tf;
        }


        public String toDFString() {
            return word + TAB + docIds.toString() + TAB + docs;
        }

    }

    static class TFComparator implements Comparator<WordStatistics> {
        @Override
        public int compare(WordStatistics o1, WordStatistics o2) {
            if (o1.tf > o2.tf) {
                return -1;
            } else if (o1.tf < o2.tf) {
                return 1;
            } else {
                return 0;
            }
        }
    }

    static class LexiComparator implements Comparator<WordStatistics> {
        @Override
        public int compare(WordStatistics o1, WordStatistics o2) {
            return o1.word.compareTo(o2.word);
        }
    }

}
