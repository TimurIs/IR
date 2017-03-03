import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class StopWordRemover {
    static Set<String> stopWords = new HashSet<String>();

    public static void getStopWords(String filePath) {
        if (!stopWords.isEmpty()) {
            //already filled; return
            return;
        }
        try {
            List<String> commonWords = Files.readAllLines(Paths.get(filePath), Charset.defaultCharset());
            for (String word : commonWords) {
                stopWords.add(word);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static List<String> removeStopWords(List<String> queryTerms){
        queryTerms.removeAll(stopWords);
        return queryTerms;
    }
}
