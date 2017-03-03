import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

public class InflectionalQueryExpander {
    static private Map<String, Set<String>> lemma2Variants = new HashMap<String, Set<String>>();
    static private String lemma2VariantsFile;
    private static String TAB = "\t";
    private static StanfordCoreNLP coreNLP;

    static {
        Properties props;
        props = new Properties();
        props.put("annotators", "tokenize, ssplit, pos, lemma");
        coreNLP = new StanfordCoreNLP(props);
    }

    static void collectMappings(String text) throws IOException {

        Annotation annotation = new Annotation(text);
        coreNLP.annotate(annotation);

        List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
        for(CoreMap sentence: sentences) {
            for (CoreLabel token: sentence.get(CoreAnnotations.TokensAnnotation.class)) {
                String lemma = token.get(CoreAnnotations.LemmaAnnotation.class);
                if (token.value().equals("ai")) {
                    lemma = "ai";
                }
                Set<String> mappings = lemma2Variants.get(lemma);
                if (mappings == null) {
                    mappings = new HashSet<String>();
                    mappings.add(token.value());
                    lemma2Variants.put(lemma, mappings);
                }
                else{
                     mappings.add(token.value());
                    lemma2Variants.put(lemma, mappings);
                }

            }
        }

    }

    public static void saveLemmas(String lemma2VariantsFile) throws IOException {
        InflectionalQueryExpander.lemma2VariantsFile = lemma2VariantsFile;
        Set<Map.Entry<String, Set<String>>> entries = lemma2Variants.entrySet();
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Set<String>> e : entries) {
            sb.append(e.getKey()).append(TAB);
            for (String s : e.getValue()) {
                sb.append(s).append(", ");
            }
            sb.append("\n");
        }
        Files.deleteIfExists(Paths.get(lemma2VariantsFile));
        Files.createFile(Paths.get(lemma2VariantsFile));
        Files.write(Paths.get(lemma2VariantsFile), sb.toString().getBytes(), StandardOpenOption.WRITE);
    }

    static Map<String, Set<String>> readMappings() throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(lemma2VariantsFile), Charset.defaultCharset());

        for (String line : lines) {
            String[] split = line.split(TAB);
            String[] values = split[1].split(",");
            Set<String> valueList = new HashSet<String>();
            for (String value : values) {
                valueList.add(value.trim());
            }

            lemma2Variants.put(split[0], valueList);

        }
        return lemma2Variants;
    }

    static String getExpansion(String oldQuery) {
        Annotation annotation = new Annotation(oldQuery);
        coreNLP.annotate(annotation);
        StringBuilder sb = new StringBuilder();

        List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
        for(CoreMap sentence: sentences) {
            for (CoreLabel token: sentence.get(CoreAnnotations.TokensAnnotation.class)) {
                String lemma = token.get(CoreAnnotations.LemmaAnnotation.class);

                sb.append(token.value()).append(" ");
                if (StopWordRemover.stopWords.contains(token.value())) {
                    continue;
                }
                Set<String> set = lemma2Variants.get(lemma);


                if (set == null) {
                    continue;
                }

                // Add lemma if not in set
                if (!set.contains(lemma)) {
                    set.add(lemma);
                }

                // Token already added
                set.remove(token.value());

                for (String s : set) {
                    if (s.trim().isEmpty()) {
                        continue;
                    }
                    sb.append(s).append(" ");
                }
            }
        }

        return sb.toString();
    }

}
