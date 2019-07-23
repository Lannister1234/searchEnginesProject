import java.io.*;
import java.util.*;

/**
 *  The NEAR operator for all retrieval models.
 */
public class QryIopNear extends QryIop {
    private int dist;

    /**
     * QryIopNear Constructor
     * @param dist
     */

    public QryIopNear(int dist) {
        this.dist = dist;
    }

    /**
     *  Evaluate the query operator; the result is an internal inverted
     *  list that may be accessed via the internal iterators.
     *  @throws IOException Error accessing the Lucene index.
     */
    protected void evaluate () throws IOException {

        //  Create an empty inverted list.  If there are no query arguments,
        //  that's the final result.

        this.invertedList = new InvList (this.getField());

        if (args.size () == 0) {
            return;
        }

        if (!this.docIteratorHasMatchAll(null)) {
            return;
        }
        //  Each pass of the loop adds 1 document to result inverted list
        //  until all of the argument inverted lists are depleted.
        while (this.docIteratorHasMatchAll((null))) {
            // Advance all doc iterators until they point to the same document
            QryIop prev = ((QryIop)args.get(0));
            int minId = prev.docIteratorGetMatch();

            if (minId == Qry.INVALID_DOCID) {
                break;
            }

            // get location info of this doc
            List<Integer> prev_list = prev.docIteratorGetMatchPosting().positions;
            List<Integer> prev_locs = new ArrayList<Integer>(prev_list);
            List<Integer> curr_locs = new ArrayList<>();

            // greedy algorithm on each two inverted lists
            int index = 1;
            while (index < this.args.size()) {
                // set two pointers for two inverted lists
                int prev_pointer = 0, curr_pointer = 0;
                List<Integer> temp = new ArrayList<>();

                // get location list of curr Qry
                QryIop curr = (QryIop)(this.args.get(index));
                List<Integer> curr_list = curr.docIteratorGetMatchPosting().positions;
                curr_locs = new ArrayList<Integer>(curr_list);

                while (true) {
                    if (prev_pointer >= prev_locs.size()){
                        break;
                    }
                    if (curr_pointer >= curr_locs.size()) {
                        break;
                    }

                    // get locations in two location lists
                    int prev_loc = prev_locs.get(prev_pointer);
                    int curr_loc = curr_locs.get(curr_pointer);

                    // calculate the distance between two locations
                    // advance pointers
                    int dist = curr_loc - prev_loc;
                    if (dist < 0) {
                        curr_pointer++;
                        continue;
                    } else if (dist > this.dist) {
                        prev_pointer++;
                    } else { // match distance requirement
                        prev_pointer++;
                        curr_pointer++;
                        // add the temp list
                        temp.add(curr_loc);
                    }
                }
                // save the temp result into prev_locs and continue
                prev_locs = temp;
                index++;
                if (temp.isEmpty()){
                    break;
                }
            }

            // move document pointers larger than minId
            this.args.get(0).docIteratorAdvancePast(minId);

            // append each result to the new inverted list
            if (!prev_locs.isEmpty()) {
                List<Integer> minId_list = new ArrayList<>(prev_locs);
                this.invertedList.appendPosting(minId, minId_list);
            }
        }

    }
}
