/**
 *  An object that stores parameters for the Indri
 *  retrieval model (there are none) and indicates to the query
 *  operators how the query should be evaluated.
 */
public class RetrievalModelIndri extends RetrievalModel {
    protected double mu;
    protected double lambda;

    public String defaultQrySopName () {
        return new String ("#and");
    }

    /**
     * Constructor of RetrievalModelIndri
     * @param mu     parameter 1
     * @param lambda parameter 2
     */
    public RetrievalModelIndri(double mu, double lambda) {
        this.mu = mu;
        this.lambda = lambda;
    }
}
