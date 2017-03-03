import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class QueryLoader {

    private Map<Integer, String> id2query = new HashMap<Integer, String>();

    public Map<Integer, String> getId2query() {
        return id2query;
    }

    public void loadQueries(String queryFile) throws IOException {
        List<String> queryLines = Files.readAllLines(Paths.get(queryFile), Charset.defaultCharset());
        boolean queryStart = false;
        boolean queryProg = false;
        StringBuilder qsb = null;
        Integer id = null;
        for (String queryLine : queryLines) {

            if (queryLine.startsWith("</DOC>")) {
                queryProg = false;
                String query = qsb.toString().trim();
                query = TextCleaner.cleanText(query);
                query = query.replaceAll("\\s+", " ");
                id2query.put(id, query);
            }

            if (queryStart) {
                qsb = new StringBuilder();
                queryStart = false;
                queryProg = true;
                if (!queryLine.isEmpty()) {
                    qsb.append(queryLine).append(" ");
                }
            }

            if (queryProg) {
                if (!queryLine.isEmpty()) {
                    qsb.append(queryLine).append(" ");
                }
            }


            if (queryLine.endsWith("</DOCNO>")) {
                queryStart = true;
                id = Integer.parseInt(queryLine.substring(queryLine.indexOf("<DOCNO>") + 7, queryLine.indexOf("</DOCNO>")).trim());
            }

        }
    }

    public void loadStemmedQueries(String queryFile) throws IOException {
        List<String> queryLines = Files.readAllLines(Paths.get(queryFile), Charset.defaultCharset());

        int id = 1;
        for (String line : queryLines) {
            String[] split = line.split("\t");
            id2query.put(Integer.parseInt(split[0]), split[1]);
            //id2query.put(id, line);
            id++;
        }
    }
}
