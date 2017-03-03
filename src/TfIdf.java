import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TfIdf {
	public static double TF(String[] terms, String term)
	{
		int f = 0;
		for(String t: terms)
		{
			if(t.equals(term))
				f++;
		}
		return f / terms.length;
	}
	
	public static double IDF(Map<String, Map<String, Long>> invInd1, String term, int n)
	{
		int f = 0;
		Map<String, Long> tmp = invInd1.get(term);
		if(tmp == null)
			return 0;
		f = tmp.size();
		
		return Math.log(n / f);
	}
	
	public static double TF_IDF(Map<String, Map<String,Long>> invInd1, Map<String, Long> fileTerms, String term, int n)
	{
		double tf = Math.log(fileTerms.get(term) + 1);
		double idf = IDF(invInd1, term, n);
		double denominator = getDenominator(invInd1, fileTerms, n);
		return tf * idf / denominator;
	}

	private static double getDenominator(Map<String, Map<String, Long>> invInd1, Map<String, Long> fileTerms, int n) {
		List<String> checkedTerms = new ArrayList<String>();
		double denominator = 0;
		
		for(String t: fileTerms.keySet())
		{
			if(checkedTerms.contains(t))
				continue;
			checkedTerms.add(t);
			denominator += Math.pow((Math.log(fileTerms.get(t) + 1) + IDF(invInd1, t, n)), 2);
		}
		return Math.sqrt(denominator);
	}

}
