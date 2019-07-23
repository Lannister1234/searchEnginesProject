
import java.io.*;

/**
 *  The OR operator for all retrieval models.
 */
public class QrySopWAND extends QrySopW {

    /**
     *  Indicates whether the query has a match.
     *  @param r The retrieval model that determines what is a match
     *  @return True if the query matches, otherwise false.
     */
    public boolean docIteratorHasMatch (RetrievalModel r) {
        if (r instanceof RetrievalModelIndri) {
            return this.docIteratorHasMatchMin(r);
        }
        return this.docIteratorHasMatchAll(r);
    }

    /**
     *  Get a score for the document that docIteratorHasMatch matched.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */
    public double getScore (RetrievalModel r) throws IOException {
        if (r instanceof RetrievalModelIndri) {
            return this.getScoreIndri(r);
        } else {
            throw new IllegalArgumentException
                    (r.getClass().getName() + " doesn't support the AND operator.");
        }
    }

    /**
     *  getScore for the Indri retrieval model.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */

    private double getScoreIndri (RetrievalModel r) throws IOException {
        if (!this.docIteratorHasMatchCache()) {
            return 0.0;
        } else {
            int id = this.docIteratorGetMatch();
            double score = 1.0;
            double weightSum = this.getWeightSum();
            int total_size = this.args.size();

            for (int i = 0; i < total_size; i++) {
                QrySop q_i = (QrySop) this.args.get(i);
                double weight = this.getWeight(i);
                double temp_score;
                if (q_i.docIteratorHasMatch(r) && q_i.docIteratorGetMatch() == id) {
                    temp_score = Math.pow(q_i.getScore(r), weight/weightSum);
                } else {
                    temp_score = Math.pow(q_i.getDefaultScore(r, id), weight/weightSum);
                }
                score *= temp_score;
            }
            return score;
        }
    }

    public double getDefaultScore(RetrievalModel r, int id) throws IOException {
        double weightSum = this.getWeightSum();
        double score = 1.0;
        for (int i = 0; i < this.args.size(); i++) {
            QrySop q_i = (QrySop) this.args.get(i);
            double weight = this.getWeight(i);
            score *= Math.pow(q_i.getDefaultScore(r, id), weight / weightSum);
        }
        return score;
    }
}

