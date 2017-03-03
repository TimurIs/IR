import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;


public class CorpusCleaner {

    private static String corpusFolderCleaned = "workFolder/data/cacm_cleaned/";
    private static String corpusFolder = "workFolder/data/cacm/";

    public static void cleanCorpus(String corpusFolderCleanedParam, String corpusFolderParam) throws IOException {
        corpusFolderCleaned = corpusFolderCleanedParam;
        corpusFolder = corpusFolderParam;

        if (Files.exists(Paths.get(corpusFolderCleaned))) {
            deleteFolderWithFiles(corpusFolderCleaned);
            Files.createDirectory(Paths.get(corpusFolderCleaned));
        }
        else {
            Files.createDirectory(Paths.get(corpusFolderCleaned));
        }
        processCorpus(corpusFolder);
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
        Path fileName = file.getFileName();
        List<String> lines = Files.readAllLines(file, Charset.defaultCharset());
        List<String> cleanedArticle = TextCleaner.precleanArticle(lines);

        StringBuilder sb = new StringBuilder();
        for (String line : cleanedArticle) {
            sb.append(line).append("\n");
        }
        Files.createFile(Paths.get(corpusFolderCleaned + fileName));
        Files.write(Paths.get(corpusFolderCleaned + fileName), sb.toString().getBytes(), StandardOpenOption.APPEND);
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
}
