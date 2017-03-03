import java.util.*;

public class PRQueryExpander {

	private static Set<String> getVocabulary(Map<String, Map<String, Long>> invInd1, String query, List<String> rankedDocuments,
                                             int k, Map<String, Map<String, Long>> docs2terms, int n)
	{
		Set<String> vocabulary = new HashSet<String>();
		
		String[] qts = query.split(" ");

		for (String qt: qts) {
			vocabulary.add(qt);
		}
		
		StringBuilder sb = new StringBuilder();
		
		List<String> relDocs = new ArrayList<String>();
		for(int i = 0; i < k; i++)
		{
			relDocs.add(rankedDocuments.get(i));
		}
		
		for (String file: relDocs)
		{
			List<TfIdfTuple> docAsVector = new ArrayList<TfIdfTuple>();
			
			sb = new StringBuilder();
			//readFileToStringBuilder(sb, docs2terms, file);
			//String fileAsString = sb.toString();
			//String[] terms = fileAsString.split(" ");

            Map<String, Long> fileTerms = docs2terms.get(file);
            for (String term : fileTerms.keySet())
			{
				TfIdfTuple t = new TfIdfTuple();
				t.term = term;
				t.tf_idf = TfIdf.TF_IDF(invInd1, fileTerms, term, n);
				docAsVector.add(t);
			}
			
			Collections.sort(docAsVector, new Comparator<TfIdfTuple>(){
				   @Override
				   public int compare(final TfIdfTuple t1, TfIdfTuple t2) 
				   {
					 if(t1.tf_idf < t2.tf_idf)
						 return 1;
					 if(t1.tf_idf == t2.tf_idf)
						 return 0;
					 return -1;
				   }
				 });
			
			int i = 0;
			for(TfIdfTuple t: docAsVector)
			{
				if(i == 5)
					break;
				i++;
				vocabulary.add(t.term);
			}
		}
		return vocabulary;
	}
	
	
	/**
	 * Expands the given query
	 * @param invInd1 - the inverted index
	 * @param query - Query to be expanded
	 * @param rankedDocuments - a List<String> that represents the list of names of ranked documents. Order from highest to lowest
	 * @param k - how many documents we assume to be relevant from the top of the given rankedDocuments
	 * @param docs2terms - documents with term frequencies
	 * @param n - total number of documents that was searched
	 * @return new expanded query as String
	 */
	public static String getExpandedQuery(Map<String, Map<String, Long>> invInd1, String query, List<String> rankedDocuments,
                                          int k, Map<String, Map<String, Long>> docs2terms, int n)
	{
		double a = 1;
		double b = 0.75;
		double g = 0.15;
		
		Set<String> vocabulary = getVocabulary(invInd1, query, rankedDocuments, k, docs2terms, n);
		int vectorSize = vocabulary.size();
		List<String> vocabularyAsList = new ArrayList<String>(vocabulary);
		Collections.sort(vocabularyAsList);
		
		List<String> relDocs = new ArrayList<String>();
		for(int i = 0; i < k; i++)
		{
			relDocs.add(rankedDocuments.get(i));
		}
		
		double[] resultVector = new double[vectorSize];
		
		computeWeightOfQuery(resultVector, vocabularyAsList, query, a);		
		computeWeightForRelDocuments(relDocs, vocabularyAsList, docs2terms, resultVector, k, b);
		computeWeightForNonRelDocuments(docs2terms, relDocs, vocabularyAsList, resultVector, k, g);
		
		return buildNewQuery(vocabularyAsList, resultVector);
	}
	

	private static String buildNewQuery(List<String> vocabularyAsList, double[] resultVector) 
	{
		int i = 0;
		StringBuilder newQuery = new StringBuilder();
		for(Double d: resultVector)
		{
			if(d <= 0)
				continue;
			String term = vocabularyAsList.get(i);
			long tf_new = Math.round(d);
			for(int j = 0; j < tf_new; j++)
				newQuery.append(term).append(" ");
			i++;
		}
		return newQuery.toString();
	}

	private static void computeWeightForNonRelDocuments(Map<String, Map<String, Long>> docs2terms, List<String> relDocs,
			List<String> vocabularyAsList, double[] resultVector, int k, double g)
	{

        Set<String> allDocs = docs2terms.keySet();
        Set<String> nonRelDocs = new HashSet<String>(allDocs);
        nonRelDocs.removeAll(relDocs);
		
		for(String nonRelDoc: nonRelDocs)
		{
            Map<String, Long> fileTerms = docs2terms.get(nonRelDoc);
			
			int num = 0;			
			for(String t: vocabularyAsList)
			{
				double res = 0;

                Long tf = fileTerms.get(t);
                if (tf == null) {
                    res = 0;
                }
                else {
                    res -= g * tf / (nonRelDocs.size());
                }
				
				resultVector[num] += res;
				num++;
			}
		}		
	}

	private static void computeWeightForRelDocuments(List<String> relDocs, List<String> vocabularyAsList,
                                                     Map<String, Map<String, Long>> docs2terms,
													 double[] resultVector, int k, double b)
	{	
		for(String file: relDocs)
		{
            Map<String, Long> fileTerms = docs2terms.get(file);
			double res = 0;

			int num = 0;
			
			for(String t: vocabularyAsList)
			{
				res = 0;
				Long tf = 0l;

                tf = fileTerms.get(t);
                if (tf == null) {
                    res = 0;
                }
                else {
                    res += b * tf / k;
                }
				resultVector[num] += res;
				num++;
			}
		}
	}

	private static void computeWeightOfQuery(double[] resultVector, List<String> vocabularyAsList, String query, double a) 
	{
		int num = 0;
		for(String t: vocabularyAsList)
		{
			double res = 0;
			int qtf = 0;
			
			for(String qt: query.split(" "))
			{
				if(qt.equals(t))
					qtf++;
			}
			
			res += a * qtf;
			resultVector[num] += res;
			num++;
		}		
	}
}