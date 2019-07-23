/**
 *  An object that stores parameters for the BM25
 *  retrieval model (there are none) and indicates to the query
 *  operators how the query should be evaluated.
 */
public class RetrievalModelBM25 extends RetrievalModel {
    protected double k_1;
    protected double b;
    protected double k_3;

    public String defaultQrySopName () {
        return new String ("#sum");
    }

    /**
     * Constructor of RetrievalModelBM25
     * @param k_1 parameter 1
     * @param b   parameter 2
     * @param k_3 parameter 3
     */
    public RetrievalModelBM25(double k_1, double b, double k_3) {
        this.k_1 = k_1;
        this.b = b;
        this.k_3 = k_3;
    }
}
