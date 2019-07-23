import java.io.*;
import java.util.ArrayList;
import java.util.List;

public abstract class QrySopW extends QrySop{
    private List<Double> weight_list = new ArrayList<>();

    public double getWeightSum(){
        double weightSum = 0;
        for (Double weight: weight_list) {
            weightSum += weight;
        }
        return weightSum;
    }

    public void addWeight(double weight) {
        this.weight_list.add(weight);
    }

    public double getWeight(int index) {
        return weight_list.get(index);
    }

    /**
     *  Get a score for the document that docIteratorHasMatch matched.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */
    public abstract double getScore(RetrievalModel r) throws IOException;

    /**
     *  Add getDefaultScore for Indri retrieval method, for those Qry that doesn't have a match.
     * @param r The retrieval model that determines how scores are calculated.
     * @param docid The docid that the Qry doesn't match.
     * @return  The document's default score.
     * @throws IOException Error accessing the Lucene index
     */
    public abstract double getDefaultScore(RetrievalModel r, int docid) throws IOException;

    /**
     *  Initialize the query operator (and its arguments), including any
     *  internal iterators.  If the query operator is of type QryIop, it
     *  is fully evaluated, and the results are stored in an internal
     *  inverted list that may be accessed via the internal iterator.
     *  @param r A retrieval model that guides initialization
     *  @throws IOException Error accessing the Lucene index.
     */
    public void initialize(RetrievalModel r) throws IOException{
        for (Qry q_i: this.args) {
            q_i.initialize(r);
        }
    }
}
