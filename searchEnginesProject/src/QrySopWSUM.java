
import java.io.*;

/**
 *  The OR operator for all retrieval models.
 */
public class QrySopWSUM extends QrySopW {

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
        if (r instanceof RetrievalModelIndri) {
            return this.getScoreIndri(r);
        } else {
            throw new IllegalArgumentException
                    (r.getClass().getName() + " doesn't support the WSUM operator.");
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
        double total_score = 0.0;
        double weightSum = this.getWeightSum();
        int total_size = this.args.size();

        for (int i = 0; i < total_size; i++) {
            double temp_score;
            QrySop q_i = (QrySop) this.args.get(i);
            if (q_i.docIteratorHasMatch(r) && q_i.docIteratorGetMatch() == id) {
                temp_score = q_i.getScore(r);
            } else {
                temp_score = q_i.getDefaultScore(r, id);
            }
            double weight = this.getWeight(i);
            total_score += temp_score * weight / weightSum;
        }
        return total_score;
    }

    public double getDefaultScore(RetrievalModel r, int id) throws IOException {
        double weightSum = this.getWeightSum();
        double total_score = 0;
        int total_size = this.args.size();

        for (int i = 0; i < total_size; i++) {
            QrySop q_i = (QrySop) this.args.get(i);
            double weight = this.getWeight(i);
            total_score += q_i.getDefaultScore(r, id) * weight / weightSum;
        }
        return total_score;
    }
}

