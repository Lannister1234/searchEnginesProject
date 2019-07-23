/**
 *  Copyright (c) 2018, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;

/**
 *  The OR operator for all retrieval models.
 */
public class QrySopAnd extends QrySop {

    /**
     *  Indicates whether the query has a match.
     *  @param r The retrieval model that determines what is a match
     *  @return True if the query matches, otherwise false.
     */
    public boolean docIteratorHasMatch (RetrievalModel r) {
        if (r instanceof RetrievalModelIndri) {
            return this.docIteratorHasMatchMin(r);
        }
        return this.docIteratorHasMatchAll (r);
    }

    /**
     *  Get a score for the document that docIteratorHasMatch matched.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */
    public double getScore (RetrievalModel r) throws IOException {

        if (r instanceof RetrievalModelUnrankedBoolean) {
            return this.getScoreUnrankedBoolean (r);
        } else if (r instanceof RetrievalModelRankedBoolean) {
            return this.getScoreRankedBoolean (r);
        } else if (r instanceof RetrievalModelIndri) {
            return this.getScoreIndri(r);
        } else {
            throw new IllegalArgumentException
                    (r.getClass().getName() + " doesn't support the AND operator.");
        }
    }

    /**
     *  getScore for the UnrankedBoolean retrieval model.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */
    private double getScoreUnrankedBoolean (RetrievalModel r) throws IOException {
        if (! this.docIteratorHasMatchCache()) {
            return 0.0;
        } else {
            return 1.0;
        }
    }

    /**
     *  getScore for the RankedBoolean retrieval model.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */

    private double getScoreRankedBoolean (RetrievalModel r) throws IOException {
        if (!this.docIteratorHasMatchCache()) {
            return 0.0;
        } else {
            double retVal = Double.MAX_VALUE;
            for (int i = 0; i < this.args.size(); i++) {
                Qry q_i = this.args.get(i);
                double score = ((QrySop)q_i).getScore(r);
                // get the min score
                if (score < retVal) {
                    retVal = score;
                }
            }
            return retVal;
        }
    }

    /**
     *  getScore for the Indri retrieval model.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */

    private double getScoreIndri (RetrievalModel r) throws IOException {
        int id = this.docIteratorGetMatch();
        double score = 1.0;
        int total_size = this.args.size();
        for (int i = 0; i < total_size; i++) {
            QrySop q_i = (QrySop) this.args.get(i);
            double tempScore;
            if (q_i.docIteratorHasMatch(r) && q_i.docIteratorGetMatch() == id) {
                tempScore = q_i.getScore(r);
            } else {
                tempScore = q_i.getDefaultScore(r, id);
            }
            score *= tempScore;
        }
        score = Math.pow(score, 1.0 / total_size);
        return score;
    }
    /**
     *  getDefaultScore for the Indri retrieval model.
     *  @param r The retrieval model id: documet id
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */
    public double getDefaultScore(RetrievalModel r, int id) throws IOException {
        double score = 1.0;
        int total_size = this.args.size();
        for (int i = 0; i < total_size; i++) {
            QrySop q_i = (QrySop) this.args.get(i);
            score *= q_i.getDefaultScore(r, id);
        }
        return Math.pow(score, 1.0 / total_size);
    }
}

