/*
 *  Copyright (c) 2018, Carnegie Mellon University.  All Rights Reserved.
 *  Version 3.3.2.
 */

import java.io.*;
import java.lang.reflect.Array;
import java.util.*;

import org.apache.lucene.analysis.Analyzer.TokenStreamComponents;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

import javax.print.Doc;

/**
 * This software illRustrates the architecture for the portion of a
 * search engine that evaluates queries.  It is a guide for class
 * homework assignments, so it emphasizes simplicity over efficiency.
 * It implements an unranked Boolean retrieval model, however it is
 * easily extended to other retrieval models.  For more information,
 * see the ReadMe.txt file.
 */
public class QryEval {

    //  --------------- Constants and variables ---------------------

    private static final String USAGE =
            "Usage:  java QryEval paramFile\n\n";

    private static final String[] TEXT_FIELDS =
            {"body", "title", "url", "inlink"};


    //  --------------- Methods ---------------------------------------

    /**
     * Main class
     * @param args The only argument is the parameter file name.
     * @throws Exception Error accessing the Lucene index.
     */
    public static void main(String[] args) throws Exception {

        //  This is a timer that you may find useful.  It is used here to
        //  time how long the entire program takes, but you can move it
        //  around to time specific parts of your code.

        Timer timer = new Timer();
        timer.start();

        //  Check that a parameter file is included, and that the required
        //  parameters are present.  Just store the parameters.  They get
        //  processed later during initialization of different system
        //  components.

        if (args.length < 1) {
            throw new IllegalArgumentException(USAGE);
        }

        Map<String, String> parameters = readParameterFile(args[0]);

        for (Map.Entry<String, String> x : parameters.entrySet()) {
            System.out.println(x.getKey() + " : " + x.getValue());
        }

        //  Open the index and initialize the retrieval model.

        Idx.open(parameters.get("indexPath"));

        //  Perform experiments.
        String outputPath = parameters.get("trecEvalOutputPath");

        if (parameters.containsKey("diversity") && parameters.get("diversity").toLowerCase().equals("true")) {
            diversification(parameters);
        } else {
            RetrievalModel model = initializeRetrievalModel (parameters);
            int printLength = Integer.parseInt(parameters.get("trecEvalOutputLength"));
            processNormalQueryFile(parameters.get("queryFilePath"), model, outputPath, printLength);
        }
        //  Clean up.

        timer.stop();
        System.out.println("Time:  " + timer);
    }

    /**
     *  Process the query file.
     *  @param queryFilePath
     *  @param model
     *  @throws IOException Error accessing the Lucene index.
     */
    static void processNormalQueryFile(String queryFilePath,
                                 RetrievalModel model, String outputPath, int outputLength)
            throws IOException {

        BufferedReader input = null;
        BufferedWriter output = null;
        try {
            String qLine = null;

            input = new BufferedReader(new FileReader(queryFilePath));
            output = new BufferedWriter(new FileWriter(outputPath));
            //  Each pass of the loop processes one query.

            while ((qLine = input.readLine()) != null) {
                int d = qLine.indexOf(':');

                if (d < 0) {
                    throw new IllegalArgumentException
                            ("Syntax error:  Missing ':' in query line.");
                }

                printMemoryUsage(false);

                String qid = qLine.substring(0, d);
                String query = qLine.substring(d + 1);

                System.out.println("Query " + qLine);

                ScoreList r = null;

                r = processQuery(query, model);

                if (r != null) {
                    int i = 0;
                    int len = Math.min(r.size(), outputLength);
                    for (i = 0; i < len; i++) {
                        String externid = Idx.getExternalDocid(r.getDocid(i));
                        double score = r.getDocidScore(i);
                        output.write(String.format("%s  Q0  %s  %d  %.18f  fubar\n",
                                qid, externid, i + 1, score));
                        System.out.println(String.format("%s  Q0  %s  %d  %f  run-1",
                                qid, externid, i + 1, score));
                    }

                    if (i == 0) {
                        output.write(String.format("%s  Q0  %s  %d  %d  fubar\n", qid, "dummyRecord", 1, 0));
                        System.out.print(String.format("%s  Q0  %s  %d  %d  fubar\n", qid, "dummyRecord", 1, 0));
                    }
                } else {
                    output.write(String.format("%s  Q0  %s  %d  %d  fubar\n", qid, "dummy", 1, 0));
                    System.out.println(String.format("%s  Q0  %s  %d  %f  run-1", qid, "dummy", 1, 0));
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            input.close();
            output.close();
        }
    }



    /**
     * Allocate the retrieval model and initialize it using parameters
     * from the parameter file.
     *
     * @return The initialized retrieval model
     * @throws IOException Error accessing the Lucene index.
     */
    private static RetrievalModel initializeRetrievalModel(Map<String, String> parameters)
            throws IOException {

        RetrievalModel model = null;
        String modelString = parameters.get("retrievalAlgorithm").toLowerCase();

        if (modelString.equals("unrankedboolean")) {
            model = new RetrievalModelUnrankedBoolean();
        } else if (modelString.equals("rankedboolean")) {
            model = new RetrievalModelRankedBoolean();
        } else if (modelString.equals("bm25")) {
            // Add BM25 and initialize it.
            double k_1 = Double.parseDouble(parameters.get("BM25:k_1"));
            double k_3 = Double.parseDouble(parameters.get("BM25:k_3"));
            double b = Double.parseDouble(parameters.get("BM25:b"));
            model = new RetrievalModelBM25(k_1, b, k_3);
        } else if (modelString.equals("indri")) {
            // Add Indri and initialize it.
            double lambda = Double.parseDouble(parameters.get("Indri:lambda"));
            double mu = Double.parseDouble(parameters.get("Indri:mu"));
            model = new RetrievalModelIndri(mu, lambda);
        } else {
            throw new IllegalArgumentException
                    ("Unknown retrieval model " + parameters.get("retrievalAlgorithm"));
        }

        return model;
    }

    /**
     * diversification
     * @param parameters
     * @throws Exception
     */
    public static void diversification(Map<String, String> parameters) throws Exception {
        // initial ranking file is specified
        String queryFilePath = parameters.get("queryFilePath");
        String intentFilePath = parameters.get("diversity:intentsFile");
        int inputLen = Integer.parseInt(parameters.get("diversity:maxInputRankingsLength"));
        String diversify_algorithm = parameters.get("diversity:algorithm").toLowerCase();

        Map<String, List<DocScorePair>> rankingMap = new HashMap<>();
        // query - intent map
        Map<String, ArrayList<String>> queryIntentMap = null;

        Map<String, Integer> lengthMap = new HashMap<>();
        if (parameters.containsKey("diversity:initialRankingFile")) {
            rankingMap = getInfoInRankingFile(parameters);
        } else {
            RetrievalModel model = initializeRetrievalModel(parameters);
            // read query q from the query file and use q to retrieve documents
            processQueryFile(queryFilePath, model, inputLen, rankingMap, lengthMap);
            // read intent qi from the diversity:intentsFile file
            processQueryFile(intentFilePath, model, inputLen, rankingMap, lengthMap);
        }

        queryIntentMap = buildQueryIntentMap(rankingMap);
        System.out.println(queryIntentMap);

        // produce diversification ranking for each initial query
        for (Map.Entry<String, ArrayList<String>> entry: queryIntentMap.entrySet()) {
            // stores all the docs
            ArrayList<Integer> initialDocList = new ArrayList<>();
            // stores doc-scoreList
            Map<Integer, ArrayList<Double>> Id2ScoreMap = new HashMap<>();
            scalingValues(entry, rankingMap, Id2ScoreMap, initialDocList);

            String qid =entry.getKey();
            ArrayList<String> intents = queryIntentMap.get(qid);
            if (diversify_algorithm.equals("pm2")) {
                PM2(qid, parameters, intents, Id2ScoreMap, initialDocList);
            }
            else if (diversify_algorithm.equals("xquad")) {
                xQuAD(qid, parameters, intents, Id2ScoreMap, initialDocList);
            }
        }
    }

    /**
     * xQuAD
     * @param qid
     * @param parameters
     * @param intents
     * @param Id2ScoreMap
     * @param initialDocList
     * @throws Exception
     */
    public static void xQuAD(String qid, Map<String, String> parameters, ArrayList<String> intents,
                           Map<Integer, ArrayList<Double>> Id2ScoreMap, ArrayList<Integer> initialDocList) throws Exception {
        ScoreList final_scoreList = new ScoreList();
        int maxResultLen = Integer.parseInt(parameters.get("diversity:maxResultRankingLength"));
        double lambda = Double.parseDouble(parameters.get("diversity:lambda"));
        HashSet<Integer> idinRanking = new HashSet<>();
        for (int id: Id2ScoreMap.keySet()) { idinRanking.add(id);  }

        int num_intents = intents.size();
        int docListSize = initialDocList.size();
        double uniWeight = 1.0 / num_intents;
        maxResultLen = Math.min(maxResultLen, docListSize);

        Map<Integer, ArrayList<Double>> docScoreList = new HashMap<>();

        while (final_scoreList.size() < maxResultLen) {
            double maxScore = -1;
            int maxScoreDocId = -1;
            for (int internId: initialDocList) {
                double diver_score = 0.0;
                double relev_score;

                ArrayList<Double> scores = Id2ScoreMap.get(internId);
                relev_score = scores.get(0);
                for (int i = 1; i < num_intents + 1; i++) {
                    // get diversification score
                    double curr_val = 1.0;
                    for (Map.Entry<Integer, ArrayList<Double>> entry: docScoreList.entrySet()) {
                        ArrayList<Double> values = entry.getValue();
                        curr_val *= (1 - values.get(i));
                    }
                    diver_score += uniWeight * scores.get(i) * curr_val;
                }
                // final score
                double score = lambda * diver_score + (1 - lambda) * relev_score;

                if (score > maxScore) {
                    maxScore = score;
                    maxScoreDocId = internId;
                }
            }
            if (maxScore == 0) {
                for (int i = 0; i < initialDocList.size(); i++) {
                    int id = initialDocList.get(i);
                    if (final_scoreList.size() >= maxResultLen) {
                        break;
                    }
                    if(idinRanking.contains(id)) {
                        int curr_size = final_scoreList.size();
                        double score = final_scoreList.getDocidScore(curr_size - 1) * 0.5;
                        final_scoreList.add(id, score);
                        idinRanking.remove(new Integer(id));
                    }
                }
                printResults(qid, final_scoreList, maxResultLen, parameters);
                return;
            }
            idinRanking.remove(maxScoreDocId);
            ArrayList<Double> maxScoreList = Id2ScoreMap.get(maxScoreDocId);
            final_scoreList.add(maxScoreDocId, maxScore);
            initialDocList.remove(new Integer(maxScoreDocId));
            docScoreList.put(maxScoreDocId, maxScoreList);
        }
        printResults(qid, final_scoreList, maxResultLen, parameters);
    }

    /**
     * PM2
     * @param qid
     * @param parameters
     * @param intents
     * @param Id2ScoreMap
     * @param initialDocList
     * @throws Exception
     */
    public static void PM2(String qid, Map<String, String> parameters, ArrayList<String> intents,
                    Map<Integer, ArrayList<Double>> Id2ScoreMap, ArrayList<Integer> initialDocList) throws Exception{
        ScoreList final_scoreList = new ScoreList();
        HashSet<Integer> idinRanking = new HashSet<>();
        for (int id: Id2ScoreMap.keySet()) {
            idinRanking.add(id);
        }
        // max Result length
        int maxResultLen = Integer.parseInt(parameters.get("diversity:maxResultRankingLength"));
        double lambda = Double.parseDouble(parameters.get("diversity:lambda"));

        // get docList size
        int docListSize = initialDocList.size();
        maxResultLen = Math.min(maxResultLen, docListSize);
        // number of intents
        int num_intents = intents.size();
        // slots
        double[] s = new double[num_intents];
        // quotients
        double[] qt = new double[num_intents];
        // votes
        double v = (1.0 / num_intents) * maxResultLen;

        // iterate
        ArrayList<Double> lastScores = new ArrayList<>();
        for (int i = 0; i < num_intents + 1; i++) {
            lastScores.add(0.0);
        }

        while (final_scoreList.size() < maxResultLen) {
            double maxQt = -1;
            double maxScore = -1;
            int maxScoreId = -1;
            int maxQtIndex = -1;

            double sum_Score = 0.0;
            for (int i = 0; i < num_intents; i++) {
                sum_Score += lastScores.get(i + 1);
            }
            // update s, qt
            for (int i = 0; i < num_intents; i++) {
                // update s
                if (sum_Score == 0) {
                    s[i] = 0;
                } else {
                    s[i] += lastScores.get(i + 1) / sum_Score;
                }

                // update qi
                qt[i] = v / (2 * s[i] + 1);

                // update max Qt and max Qt index
                if (qt[i] > maxQt) {
                    maxQt = qt[i];
                    maxQtIndex = i;
                }
            }

            for (int internId: initialDocList) {
                ArrayList<Double> scores = Id2ScoreMap.get(internId);
                double score = 0.0;
                double coverQi = qt[maxQtIndex] * scores.get(maxQtIndex + 1);
                double coverOther = 0.0;
                for (int i = 0; i < num_intents; i++) {
                    if (i == maxQtIndex) { continue; }
                    coverOther += qt[i] * scores.get(i + 1);
                }
                score = coverQi * lambda + (1 - lambda) * coverOther;

                // update max score
                if (score > maxScore) {
                    maxScore = score;
                    maxScoreId = internId;
                }
            }

            if (maxScore == 0) {
                for (int i = 0; i < initialDocList.size(); i++) {
                    int id = initialDocList.get(i);
                    if (final_scoreList.size() >= maxResultLen) {
                        break;
                    }
                    if(idinRanking.contains(id)) {
                        int curr_size = final_scoreList.size();
                        double score = final_scoreList.getDocidScore(curr_size - 1) * 0.5;
                        final_scoreList.add(id, score);
                        idinRanking.remove(new Integer(id));
                    }
                }
                printResults(qid, final_scoreList, maxResultLen, parameters);
                return;
            }
            // update last scores list
            idinRanking.remove(maxScoreId);
            lastScores = Id2ScoreMap.get(maxScoreId);
            initialDocList.remove(new Integer(maxScoreId));
            final_scoreList.add(maxScoreId, maxScore);
        }
        printResults(qid, final_scoreList, maxResultLen, parameters);
        return;
    }


    /**
     * convert to scaling form
     * @param entry
     * @param rankingMap
     * @param Id2ScoreMap
     */
    public static void scalingValues(Map.Entry<String, ArrayList<String>> entry,
                                     Map<String, List<DocScorePair>> rankingMap,
                                     Map<Integer, ArrayList<Double>> Id2ScoreMap,
                                        ArrayList<Integer> initialDocList) {
        // get max sum value
        String qid = entry.getKey();
        ArrayList<String> intents = entry.getValue();
        int num_intents = intents.size();

        boolean shouldScale = false;

        List<DocScorePair> query_list = rankingMap.get(qid);
        List<DocScorePair> intent_list = null;
        ArrayList<Double> max_Values = new ArrayList<>();

        // read initial query list
        double sum = 0;
        for (DocScorePair pair: query_list) {
            int internId = pair.getInternalId();
            double score = pair.getDocScore();
            initialDocList.add(internId);
            ArrayList<Double> DocScores = new ArrayList<>();
            // set every DocScores to Score, 0, 0, 0, 0
            DocScores.add(score);
            for (int i = 0; i < num_intents; i++) {
                DocScores.add(0.0);
            }

            if (score > 1.0) {
                shouldScale = true;
            }

            Id2ScoreMap.put(internId, DocScores);
            sum += pair.getDocScore();
        }
        max_Values.add(sum);

        for (int intent_id = 1; intent_id < num_intents + 1; intent_id++) {
            String intent = qid + "_" + intent_id;
            intent_list = rankingMap.get(intent);
            sum = 0;
            for (DocScorePair pair: intent_list) {
                int internId = pair.getInternalId();

                // if doc not in initial ranking, continue;
                if (!initialDocList.contains(internId)) {
                    continue;
                }
                double score = pair.getDocScore();

                if (score > 1.0) {
                    shouldScale = true;
                }

                Id2ScoreMap.get(internId).set(intent_id, score);
                sum += score;
            }
            max_Values.add(sum);
        }

        if (!shouldScale) {
            return;
        }
        // do scaling
        double scalingVal = Collections.max(max_Values);
        // System.out.println(scalingVal);

        for (Map.Entry<Integer, ArrayList<Double>> entry1: Id2ScoreMap.entrySet()) {
            int internId = entry1.getKey();
            ArrayList<Double> scores = entry1.getValue();
            ArrayList<Double> temp = new ArrayList<>();
            for (double score: scores) {
                double new_score = score / scalingVal;
                temp.add(new_score);
            }
            Id2ScoreMap.put(internId, temp);
        }
        //System.out.println(Id2ScoreMap);
    }


    /**
     * buildQueryIntentMap
     *
     * @param rankingMap
     * @return
     */
    public static Map<String, ArrayList<String>> buildQueryIntentMap(Map<String, List<DocScorePair>> rankingMap) {
        ArrayList<String> queryList = new ArrayList<>();
        ArrayList<String> intentList = new ArrayList<>();

        for (String key: rankingMap.keySet()) {
            if (key.contains("_")) {
                intentList.add(key);
            } else {
                queryList.add(key);
            }
        }
        Map<String, ArrayList<String>> queryIntentMap = new HashMap<>();
        for (String qid: queryList) {
            ArrayList<String> list = new ArrayList<>();
            for (String intentId: intentList) {
                if (intentId.contains(qid)) {
                    list.add(intentId);
                }
            }
            queryIntentMap.put(qid, list);
        }
        return queryIntentMap;
    }



    /**
     * get ranking list from initial ranking file
     * @param parameters
     */
    public static Map<String, List<DocScorePair>> getInfoInRankingFile
                    (Map<String, String> parameters) throws Exception {
        String initialRankingFile = parameters.get("diversity:initialRankingFile");
        int maxInputLen = Integer.parseInt(parameters.get("diversity:maxInputRankingsLength"));
        //System.out.println(maxInputLen);

        Map<String, List<DocScorePair>> rankingMap = new HashMap<>();
        FileReader reader = null;
        BufferedReader br = null;

        try {
            reader = new FileReader(initialRankingFile);
            br = new BufferedReader(reader);
            String line = null;
            String prev_qid = null;
            String curr_qid = null;

            ArrayList<DocScorePair> docScoreList = new ArrayList<>();

            Map<String, Integer> lengthMap = new HashMap<>();
            while ((line = br.readLine()) != null) {
                // parse line information
                String[] info =line.split(" ");
                // ignore ranking >= max input length
                int ranking = Integer.parseInt(info[3].trim());
                // System.out.println("ranking " + ranking);
                if (ranking > maxInputLen) {  continue; }

                curr_qid = info[0].trim();
                if (curr_qid.contains(".")) {
                    curr_qid = curr_qid.replace(".", "_");
                }

                String externid = info[2].trim();
                double docScore = Double.parseDouble(info[4].trim());

                // qid changes, store doc score pairs in map
                if (prev_qid != null && !curr_qid.equals(prev_qid)) {
                    rankingMap.put(prev_qid, docScoreList);
                    if (!prev_qid.contains("_")) {
                        lengthMap.put(prev_qid, docScoreList.size());
                    }
                    docScoreList = new ArrayList<>();
                }

                if (curr_qid.contains("_")) {
                    int indexOfDot = curr_qid.indexOf("_");
                    String init_qid = curr_qid.substring(0, indexOfDot);
                    int maxLen = lengthMap.get(init_qid);
                    if (ranking > maxLen) { continue; }
                }

                int internId = Idx.getInternalDocid(externid);
                docScoreList.add(new DocScorePair(internId, docScore));
                prev_qid = curr_qid;
            }
            // put last doc score pair into map
            if (docScoreList.size() > 0) {
                rankingMap.put(prev_qid, docScoreList);
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            reader.close();
            br.close();
        }
        return rankingMap;
    }

    /**
     * Print a message indicating the amount of memory used. The caller can
     * indicate whether garbage collection should be performed, which slows the
     * program but reduces memory usage.
     *
     * @param gc If true, run the garbage collector before reporting.
     */
    public static void printMemoryUsage(boolean gc) {

        Runtime runtime = Runtime.getRuntime();

        if (gc)
            runtime.gc();

        System.out.println("Memory used:  "
                + ((runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L)) + " MB");
    }

    /**
     * Process one query.
     *
     * @param qString A string that contains a query.
     * @param model   The retrieval model determines how matching and scoring is done.
     * @return Search results
     * @throws IOException Error accessing the index
     */
    static ScoreList processQuery(String qString, RetrievalModel model)
            throws IOException {

        String defaultOp = model.defaultQrySopName();
        qString = defaultOp + "(" + qString + ")";
        Qry q = QryParser.getQuery(qString);

        // Show the query that is evaluated
        System.out.println("    --> " + q);

        if (q != null) {

            ScoreList r = new ScoreList();

            if (q.args.size() > 0) {        // Ignore empty queries

                q.initialize(model);

                while (q.docIteratorHasMatch(model)) {
                    int docid = q.docIteratorGetMatch();
                    double score = ((QrySop) q).getScore(model);
                    r.add(docid, score);
                    q.docIteratorAdvancePast(docid);
                }
            }
            r.sort();

            return r;
        } else
            return null;
    }

    /**
     * Process the query file.
     *
     * @param queryFilePath
     * @param model
     * @throws IOException Error accessing the Lucene index.
     */
    static void processQueryFile(String queryFilePath, RetrievalModel model,
                                 int outputLength, Map<String, List<DocScorePair>> rankingMap,
                                 Map<String, Integer> lengthMap) throws IOException {

        BufferedReader input = null;
        try {
            String qLine = null;
            input = new BufferedReader(new FileReader(queryFilePath));

            //  Each pass of the loop processes one query.
            while ((qLine = input.readLine()) != null) {
                int d = qLine.indexOf(':');

                if (d < 0) {
                    throw new IllegalArgumentException
                            ("Syntax error:  Missing ':' in query line.");
                }

                printMemoryUsage(false);

                String qid = qLine.substring(0, d);
                String query = qLine.substring(d + 1);

                System.out.println("Query " + qLine);

                ScoreList r = null;

                r = processQuery(query, model);

                int maxLen = 0;
                // change 719.1 to 719_1
                if (qid.contains(".")) {
                    qid = qid.replace(".", "_");
                    int docIndex = qid.indexOf("_");
                    String init_qid = qid.substring(0, docIndex);
                    int len = lengthMap.get(init_qid);
                    maxLen = Math.min(outputLength, r.size());
                    maxLen = Math.min(maxLen, len);
                } else {
                    maxLen = Math.min(outputLength, r.size());
                    lengthMap.put(qid, maxLen);
                }

                // add qid, docScoreList pair to map
                ArrayList<DocScorePair> docScores = new ArrayList<>();

                for (int i = 0; i < maxLen; i++) {
                    String externId = Idx.getExternalDocid(r.getDocid(i));
                    int internId = Idx.getInternalDocid(externId);
                    double score = r.getDocidScore(i);
                    docScores.add(new DocScorePair(internId, score));
                }
                rankingMap.put(qid, docScores);
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            input.close();
        }
    }

    /**
     * print results
     * @param qid
     * @param r
     * @param outputLen
     * @param parameters
     * @throws IOException
     */
    static void printResults(String qid, ScoreList r, int outputLen, Map<String, String> parameters) throws IOException {
        r.sort();
        String outputFile = parameters.get("trecEvalOutputPath");
        File file = null;
        FileWriter output = null;
        try {
            file = new File(outputFile);
            output = new FileWriter(file, true);
            if (r != null) {
                int i = 0;
                for (i = 0; i < outputLen; i++) {
                    String externid = Idx.getExternalDocid(r.getDocid(i));
                    double score = r.getDocidScore(i);
                    output.write(String.format("%s  Q0  %s  %d  %.18f  fubar\n",
                            qid, externid, i + 1, score));
                    System.out.println(String.format("%s  Q0  %s  %d  %f  run-1",
                            qid, externid, i + 1, score));
                }
                if (i == 0) {
                    output.write(String.format("%s  Q0  %s  %d  %d  fubar\n", qid, "dummyRecord", 1, 0));
                    System.out.print(String.format("%s  Q0  %s  %d  %d  fubar\n", qid, "dummyRecord", 1, 0));
                }
            } else {
                output.write(String.format("%s  Q0  %s  %d  %d  fubar\n", qid, "dummy", 1, 0));
                System.out.println(String.format("%s  Q0  %s  %d  %f  run-1", qid, "dummy", 1, 0));
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            output.close();
        }
    }

    /**
     * Read the specified parameter file, and confirm that the required
     * parameters are present.  The parameters are returned in a
     * HashMap.  The caller (or its minions) are responsible for processing
     * them.
     *
     * @return The parameters, in <key, value> format.
     */
    private static Map<String, String> readParameterFile(String parameterFileName)
            throws IOException {

        Map<String, String> parameters = new HashMap<String, String>();

        File parameterFile = new File(parameterFileName);

        if (!parameterFile.canRead()) {
            throw new IllegalArgumentException
                    ("Can't read " + parameterFileName);
        }

        Scanner scan = new Scanner(parameterFile);
        String line = null;
        do {
            line = scan.nextLine();
            String[] pair = line.split("=");
            parameters.put(pair[0].trim(), pair[1].trim());
        } while (scan.hasNext());

        scan.close();

        if (!(parameters.containsKey("indexPath") &&
                parameters.containsKey("queryFilePath") &&
                parameters.containsKey("trecEvalOutputPath"))) {
            throw new IllegalArgumentException
                    ("Required parameters were missing from the parameter file.");
        }

        return parameters;
    }

}
