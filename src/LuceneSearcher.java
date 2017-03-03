import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.SimpleAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.search.highlight.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class LuceneSearcher {
	private  String indexDir;
	private  String resPath;
	private  String inputFolderPath;
	private  String queryPath;
	private  QueryLoader queries;
    private  boolean stopping = false;
	private  SimpleAnalyzer analyzer = new SimpleAnalyzer(Version.LUCENE_47);
    private static final String SYSTEM_NAME =  "timur_nataliya_LuceneEngine";

    private Evaluator evaluator;

    public LuceneSearcher(String indexDir, String resPath, String inputPath, String queryPath, boolean stopping)
	{	
		this.indexDir = indexDir;
		this.inputFolderPath = inputPath;
		this.resPath = resPath;
		this.queryPath = queryPath;
        this.stopping = stopping;
		queries = new QueryLoader();
		try 
		{
			queries.loadQueries(this.queryPath);
		}
		catch (IOException e) 
		{
			e.printStackTrace();
		}
		
	}
	
	public void search() throws Exception
	{
		File file = new File(indexDir);
		
		if (!file.exists())
		{
			if(!file.mkdir())
				throw new Exception("file: " + file.toString() + " was NOT created");
		}
		
		FSDirectory dir = FSDirectory.open(file);
		
		IndexWriterConfig config = new IndexWriterConfig(Version.LUCENE_47, analyzer);
		IndexWriter w = new IndexWriter(dir, config);
		w.deleteAll(); 
		
		File folder = new File(inputFolderPath);
		
		if (!folder.exists())
		{
			w.close();
			throw new Exception("the folder with the input data: " + inputFolderPath + " do not exist");
		}
		
		
		File[] listOfFiles = folder.listFiles();		
		
		addFilesToIndexWriter(w, listOfFiles);
		
		w.close();
		
		IndexReader reader = DirectoryReader.open(FSDirectory.open(new File(indexDir)));
		IndexSearcher searcher = new IndexSearcher(reader);
		
		folder = new File(queryPath);
		listOfFiles = folder.listFiles();
		
		Map<Integer, String> queriesMap = queries.getId2query();
		
		processQueries(queriesMap, searcher, reader);
		
	}

	private void processQueries(Map<Integer, String> queriesMap, IndexSearcher searcher, IndexReader reader) throws Exception
	{
		for(Integer qId: queriesMap.keySet())
		{
			String queryString = queriesMap.get(qId);
			List<String> queryTermsTmp = Arrays.asList(queryString.split(" "));
			List<String> queryTerms = new ArrayList<String>(queryTermsTmp);	
			if (stopping) {
				queryTerms = StopWordRemover.removeStopWords(queryTerms);
				queryString = "";
				for(String qt: queryTerms)
				{
					queryString += qt + " ";
				}
	        }
			
			TopScoreDocCollector collector = TopScoreDocCollector.create(100, true);
			Query query = new QueryParser(Version.LUCENE_47, "contents", analyzer).parse(QueryParser.escape(queryString));
			
			QueryScorer queryScorer = new QueryScorer(query);
			queryScorer.setExpandMultiTermQuery(true);
			
			Fragmenter fragmenter = new SimpleSpanFragmenter(queryScorer);
			Highlighter highlighter = new Highlighter(queryScorer);
			
			highlighter.setMaxDocCharsToAnalyze(Integer.MAX_VALUE);
			highlighter.setTextFragmenter(fragmenter);
				 
			searcher.search(query, collector);
			ScoreDoc[] hits = collector.topDocs().scoreDocs;
				
			File resultFile = new File(resPath);
			File highlightFile = new File(resPath + "highlight");
				
			if (!resultFile.exists())
			{
				if(!resultFile.mkdir())
					throw new Exception("file: " + resultFile.toString() + " was NOT created");
			}
				
			if (!highlightFile.exists())
			{
				if(!highlightFile.mkdir())
					throw new Exception("file: " + highlightFile.toString() + " was NOT created");
			}
				
			FileWriter fw = new FileWriter(resultFile.getPath() + File.separatorChar + "results_lucene_" + qId + ".txt");
			FileWriter highlightWriter = new FileWriter(highlightFile.getPath() + File.separatorChar +
                    "results_lucene_" +  qId + ".txt");
			
			writeResults(fw, highlightWriter, hits, reader, highlighter, qId, searcher);			

			fw.close();			 
			highlightWriter.close();

            List<String>  namesForEvaluation = new ArrayList<String>();
            for (ScoreDoc hit : hits) {
                Document d = searcher.doc(hit.doc);
                String path = d.get("filename");
                namesForEvaluation.add(path);
            }

            evaluator.evaluate(qId, namesForEvaluation);
		}
	}

	private void writeResults(FileWriter fw, FileWriter highlightWriter, ScoreDoc[] hits, IndexReader reader,
			Highlighter highlighter, Integer qId, IndexSearcher searcher) throws Exception
	{
        highlightWriter.write("DocRank" + "\t" + "Snippet" + "\n\n");
		for (int i = 0; i < hits.length; ++i) 
		{
			int docId = hits[i].doc;
			Document d = searcher.doc(docId);
			    
			    
			String title = d.get("contents");
			TokenStream tokenStream = TokenSources.getAnyTokenStream(reader, docId, "contents", d, new SimpleAnalyzer(Version.LUCENE_47));
			String fragment = highlighter.getBestFragments(tokenStream, title, 3, "...");
			    
			highlightWriter.write(i+1 + "\t" + fragment + "\n\n");
			fw.write("results_lucene_" + qId + " Q0 " + d.get("filename") + " " + hits[i].score + " " + SYSTEM_NAME + "\n");
		}		
	}

	private void addFilesToIndexWriter(IndexWriter w, File[] listOfFiles) throws Exception
	{
		for(File f: listOfFiles)
		{
			Document doc = new Document();
			FileReader fr = new FileReader(f);
			BufferedReader br = new BufferedReader(fr);
			StringBuilder sb = new StringBuilder();
			String line = br.readLine();
			
			while(line != null)
			{
				sb.append(line).append(" ");
				line = br.readLine();
			}
			
			doc.add(new TextField("contents", sb.toString(), Field.Store.YES));
			doc.add(new StringField("path", f.getPath(), Field.Store.YES));
			doc.add(new StringField("filename", f.getName(), Field.Store.YES));

			w.addDocument(doc);
			fr.close();
		}
	}

    public void setEvaluator(Evaluator evaluator) {
        this.evaluator = evaluator;
    }
}