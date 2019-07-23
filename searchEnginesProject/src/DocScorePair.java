import javax.print.Doc;
import java.util.Comparator;

public class DocScorePair implements Comparator<DocScorePair>{
    public int internalId;
    public double score;

    public DocScorePair() {

    }

    public DocScorePair(int internalId, double score) {
        this.internalId = internalId;
        this.score = score;
    }

    public int getInternalId() {
        return this.internalId;
    }

    public double getDocScore() {
        return this.score;
    }

    @Override
    public int compare(DocScorePair p1, DocScorePair p2) {
        if (p1.score != p2.score) {
            return Double.compare(p2.score, p1.score);
        } else {
            return Integer.compare(p1.internalId, p2.internalId);
        }
    }

    @Override
    public String toString() {
        return internalId + " " + score;
    }
}
