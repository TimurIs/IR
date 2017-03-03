import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Created by nzozulya on 4/10/2016.
 */
public class TextCleaner {

    public static String cleanText(String cleanedText) {
        //Punctuation symbols to clean
        String spaceKinds = "\\u00a0";
        String ellipsis = "\\u2026";
        String dashes = "\\u2012\\u2013\\u2014\\u2015";
        String quotes = "\\u0022\\u2018\\u201A\\u201C\\u201E\\u00AB\\u2039\\u201F\\u2019\\u201B\\u201D\\u201F\\u00BB\\u203A″|´ /`'′́";
        String brackets_open = "\\u0028\\u005B\\u007B\\u2329";
        String brackets_close = "\\u0029\\u005D\\u007D\\u232A)";
        String commas = "\\u002C\\uFF0C";
        String listBullet = "\\u2022";
        String otherPunctChemistry = "\\u2192\\u2194";
        String otherPunctMath = "\\u003c\\u003e\\u2248\\u002b\\u00b1\\u2212\\u2264\\u2265\\u00b7\\u003D×÷";
        cleanedText = cleanedText.replaceAll("[" + spaceKinds + "]", " ");

        //In punctuation in-between digits/non-digits/punctuation 70m/s;$0.303/(kW·h)
        String regex = "((.)[/])(.)";
        Pattern pattern = Pattern.compile(regex);
        cleanedText = pattern.matcher(cleanedText).replaceAll("$1 $3");

        //Punctuation before punctuation or new line
        String punctBeforeNewLine =
                "([.:^;/!?<>=" + brackets_open + brackets_close + dashes + ellipsis + quotes + commas + "*#])" +
                        "([" + brackets_open + brackets_close + dashes + ellipsis + quotes + commas + "=*#.;!?<>:\\n\\u007C])";
        Pattern patternBeforeNewLine = Pattern.compile(punctBeforeNewLine);
        cleanedText = patternBeforeNewLine.matcher(cleanedText).replaceAll("");

        //Keep space in punctuation before space
        String punctBeforeSpace = "([" + brackets_open + brackets_close + listBullet +
                dashes + ellipsis + quotes + commas + otherPunctChemistry + otherPunctMath + "^.:;/~!?<>#*\\u007C\\u005C])( )";
        Pattern patternPunBS = Pattern.compile(punctBeforeSpace);
        cleanedText = patternPunBS.matcher(cleanedText).replaceAll("$2");


        String punctBeforeEOI =
                "([.:^;/!?<>" + brackets_open + brackets_close + dashes + ellipsis + quotes + commas + "*#])" + "\\z";
        Pattern patternBeforeEOI = Pattern.compile(punctBeforeEOI);
        cleanedText = patternBeforeEOI.matcher(cleanedText).replaceAll("");

        //In punctuation before non-digit, keep non digit
        String regex_nd = "([.;/:!^?<>=*#" + brackets_open + brackets_close + dashes + ellipsis + quotes + commas +
                otherPunctChemistry + otherPunctMath + "])(\\D)";
        Pattern pattern_nd = Pattern.compile(regex_nd);
        cleanedText = pattern_nd.matcher(cleanedText).replaceAll("$2");

        //Clean some punctuation before digit
        String regex1 = "([;/:!?^=" + brackets_open + brackets_close + dashes + ellipsis + quotes + "])(\\d)";
        Pattern pattern1 = Pattern.compile(regex1);
        cleanedText = pattern1.matcher(cleanedText).replaceAll("$2");

        //Remove extra spaces
        cleanedText = cleanedText.replaceAll("\\s+", " ");
        //Convert to lower case
        cleanedText = cleanedText.toLowerCase();
        return cleanedText.trim();
    }

    public static List<String> precleanArticle(List<String> lines) {
        List<String> cleanedLines = new ArrayList<String>();
        for (String line : lines) {
            if (line.contains("\t")) {
                break;
            }
            if (line.contains("<html>") || line.contains("</html>") || line.contains("</pre>") || line.contains("<pre>")) {
                continue;
            }
            cleanedLines.add(line);
        }

        return cleanedLines;
    }
}
