0. NOTES:
0.1 In our implementation we are removing number tables from the both main and stemmed corpus and save the corpus files cleaned this way in cacm_cleaned and cacm_cleaned_stemmed subfolders under ProjectData/data correspondingly.
0.2 We implemented snippeting and highlighting with the Lucene functionality
0.3 We added query ids to the stemmed query file for more convenient evaluation
0.4 Submission structure:
0.4.1 the ProjectData/data folder contains project input data
0.4.2 the Tables folder containing all per-engine and per-query tables (the structure is identical to the ProjectData/results folder described in 3.3)
0.4.3 the libs folder with the 3rd-party libraries used
0.4.4 NZozulya_TIsaev.pdf - report

1. SETUP:
1.1 Extract files from the submission archive and go to the extracted folder.
1.2 User that you will run the program under should have permissions to create\delete\read from\write to files in this folder.

2. COMPILE: You will need at least Java 8 to compile and run source code (CCIS machines have relevant version)
This program uses the following external libraries:
- commons-lang3-3.4 - Used for concise String manipulation
- Libraries for Lucene system (including highlighting)
lucene-analyzers-common-4.7.2
lucene-core-4.7.2
lucene-queryparser-4.7.2
lucene-highlighter-4.7.2
- Stanford CoreNLP is used for inflectional query expansion
stanford-corenlp-3.6.0.jar
stanford-corenlp-3.6.0-models.jar
slf4j-api.jar
slf4j-simple.jar
These libraries are provided with the submission in the libs folder.

2.1 How to compile a program. Execute the following commands from the command line:
javac -cp libs/*:. Searcher.java 

3. RUN:
This command will perform cleaning and indexing of both main and stemmed corpuses and execute all required runs followed by evaluation
The program will be informing the user of the start and end of each stage (overall execution time is ~ 2 minutes).
java -cp libs/*:. Searcher

Output:
Output will be stored in the ProjectData folder in the following way:
1. ProjectData/data
Contains all input data and cleaned corpuses in the cacm_cleaned and cacm_cleaned_stemmed. Note, that this is cleaning from number tables only, punctuation cleaning of the main corpus is performed when building an index and not stored on disk.
2. ProjectData/indexes
Contains 3 folders with indexes and related information for main and stemmed corpuses and for Lucene.
3. ProjectData/results
Contains
3.1 Summary metrics for all runs in the EvaluationSummary file
3.2 Folder, for each run, named correspondingly (e.g. TFIDF_PseudoExp, TFIDF_InflecExp, BM25_BASE etc.) with:
3.2.1 the precision_and_recall_results folder with 64 files containing per query precision and recall calculations
3.2.2 the AvP_and_RR_and_PAtK_results folder containing a file with effectiveness summary per query (average precision, RR, and precision at K=5 and K=20
3.2.3 64 files with the TOP 100 search results
All files are tab-separated
3.2.4 (Only for Lucene base run and Lucene run with stopping) the highlight folder, containing 64 files with highlighted snippets







