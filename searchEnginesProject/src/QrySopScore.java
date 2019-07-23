/**
 *  Copyright (c) 2018, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.lang.IllegalArgumentException;

/**
 *  The SCORE operator for all retrieval models.
 */
public class QrySopScore extends QrySop {

  /**
   *  Document-independent values that should be determined just once.
   *  Some retrieval models have these, some don't.
   */
  
  /**
   *  Indicates whether the query has a match.
   *  @param r The retrieval model that determines what is a match
   *  @return True if the query matches, otherwise false.
   */
  public boolean docIteratorHasMatch (RetrievalModel r) {
    return this.docIteratorHasMatchFirst (r);
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
    } else if (r instanceof RetrievalModelBM25) {
      return this.getScoreBM25(r);
    } else if (r instanceof RetrievalModelIndri) {
      return this.getScoreIndri(r);
    } else {
      throw new IllegalArgumentException
        (r.getClass().getName() + " does not support the SCORE operator.");
    }
  }
  
  /**
   *  getScore for the Unranked retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScoreUnrankedBoolean (RetrievalModel r) throws IOException {
    if (! this.docIteratorHasMatchCache()) {
      return 0.0;
    } else {
      return 1.0;
    }
  }


  /**
   *  getScore for the Ranked retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScoreRankedBoolean (RetrievalModel r) throws IOException {
    if (! this.docIteratorHasMatchCache()) {
      return 0.0;
    } else {
      return this.getArg(0).docIteratorGetMatchPosting().tf;
    }
  }


  /**
   *  getScore for the BM25 model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScoreBM25 (RetrievalModel r) throws IOException {
    QryIop q_i = this.getArg(0);
    if (! q_i.docIteratorHasMatch(r)) {
      return 0.0;
    } else {
        // model parameters
      double k1 = ((RetrievalModelBM25)r).k_1;
      double b = ((RetrievalModelBM25)r).b;
      double k3 = ((RetrievalModelBM25)r).k_3;

      // number of documents
      double N = Idx.getNumDocs();
      // document frequency
      double df = q_i.getDf();
      // term frequency
      double tf = this.getArg(0).docIteratorGetMatchPosting().tf;
      // document length
      String field = q_i.getField();
      double docLen = Idx.getFieldLength(field, q_i.docIteratorGetMatch());
      // average document length
      double docCount = (double) Idx.getDocCount(field);
      double avg_docLen = Idx.getSumOfFieldLengths(field) / docCount;

      // three parts: rsj, tf, user
      double rsj_weight = Math.max(0, Math.log((N - df + 0.5)/ (df + 0.5)));
      double tf_weight = tf / (tf + k1*((1 - b) + b * docLen/ avg_docLen));
      double user_weight = 1.0;

      return rsj_weight * tf_weight * user_weight;
    }
  }


  /**
   *  getScore for the Indri model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScoreIndri (RetrievalModel r) throws IOException {
      QryIop q_i = this.getArg(0);
      // model parameters
      double mu = ((RetrievalModelIndri)r).mu;
      double lambda = ((RetrievalModelIndri)r).lambda;

      if (q_i.docIteratorHasMatch(r)) {
          // term frequency
          double tf = q_i.docIteratorGetMatchPosting().tf;
          // document length
          String field = q_i.getField();
          double docLen = Idx.getFieldLength(field, q_i.docIteratorGetMatch());
          // maximum likelihood estimation
          double ctf = q_i.getCtf();
          double p_mle = ctf / (double) Idx.getSumOfFieldLengths(field);
          return (1.0 - lambda) * ((tf + mu * p_mle) / (docLen + mu)) + lambda * p_mle;
      } else {
          return 0;
      }
  }


  /**
   *  getDefaultScore for Indri retrieval method.
   * @param r The retrieval model that determines how scores are calculated.
   * @param docid The docid that the Qry doesn't match.
   * @return The document's default score.
   * @throws IOException Error accessing the Lucene index
   */

    public double getDefaultScore(RetrievalModel r, int docid) throws IOException {
        QryIop q_i = this.getArg(0);
        // model parameters
        double mu = ((RetrievalModelIndri)r).mu;
        double lambda = ((RetrievalModelIndri)r).lambda;
        String field = q_i.getField();
        // document length
        double docLen = Idx.getFieldLength(field, docid);
        // maximum likelihood estimation
        double ctf = q_i.getCtf();
        double p_mle = ctf / (double) Idx.getSumOfFieldLengths(field);

        return (1.0 - lambda) * ((0 + mu * p_mle) / (docLen + mu)) + lambda * p_mle;
    }


  /**
   *  Initialize the query operator (and its arguments), including any
   *  internal iterators.  If the query operator is of type QryIop, it
   *  is fully evaluated, and the results are stored in an internal
   *  inverted list that may be accessed via the internal iterator.
   *  @param r A retrieval model that guides initialization
   *  @throws IOException Error accessing the Lucene index.
   */
  public void initialize (RetrievalModel r) throws IOException {
      Qry q = this.args.get (0);
      q.initialize (r);
  }
}
