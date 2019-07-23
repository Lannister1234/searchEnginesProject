import java.io.*;

/**
 *  The operator for all retrieval models.
 */
public class QrySopSum extends QrySop {

    /**
     *  Indicates whether the query has a match.
     *  @param r The retrieval model that determines what is a match
     *  @return True if the query matches, otherwise false.
     */
    public boolean docIteratorHasMatch (RetrievalModel r) {
        return this.docIteratorHasMatchMin (r);
    }

    /**
     *  Get a score for the document that docIteratorHasMatch matched.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */
    public double getScore (RetrievalModel r) throws IOException {
        if (r instanceof RetrievalModelBM25) {
            return this.getScoreBM25 (r);
        } else {
            throw new IllegalArgumentException
                    (r.getClass().getName() + " doesn't support the SUM operator.");
        }
    }

    public double getDefaultScore (RetrievalModel r, int id) throws IOException {
        return 0.0;
    }

    /**
     *  getScore for the BM25 retrieval model.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */
    private double getScoreBM25 (RetrievalModel r) throws IOException {
        double score = 0;
        int id = this.docIteratorGetMatch();
        for (int i = 0; i < args.size(); i++) {
            QrySop q_i = (QrySop) this.args.get(i);
            if (q_i.docIteratorHasMatch(r)) {
                if (q_i.docIteratorGetMatch() == id) {
                    score += q_i.getScore(r);
                }
            }
        }
        return score;
    }

}



