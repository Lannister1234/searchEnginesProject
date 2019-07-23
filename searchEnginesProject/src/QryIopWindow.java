import java.io.IOException;
import java.util.*;

public class QryIopWindow extends QryIop {
    private int distance;

    public QryIopWindow(int distance) {
        this.distance = distance;
    }

    /**
     * Evaluate the query operator; the result is an internal inverted
     * list that may be accessed via the internal iterators.
     *
     * @throws IOException Error accessing the Lucene index.
     */
    protected void evaluate() throws IOException {

        //  Create an empty inverted list.  If there are no query arguments,
        //  that's the final result.
        this.invertedList = new InvList(this.getField());

        if (args.size() == 0) {
            return;
        }

        //  each while loop search for next document that every term matches
        while (this.docIteratorHasMatchAll((null))) {
            // get current document id
            int docId = this.args.get(0).docIteratorGetMatch();
            // check id is valid
            if (docId == Qry.INVALID_DOCID) {
                break;
            }
            int total_size = this.args.size();
            // create a position list for this document
            List<Integer> positions = new ArrayList<>();

            // create array which stores position of pointers for each inverted list
            int[] pointers = new int[total_size];

            while (true) {
                int[] res = findMinMaxPos(pointers);
                int flag = res[0];
                int maxPos = res[1];
                int minPos = res[2];
                int minIndex = res[3];
                if (flag < 0) break;

                int distance = maxPos - minPos + 1;

                // case 1: add max position and advance all pointers
                if (distance <= this.distance) {
                    for (int i = 0; i < total_size; i++) {
                        pointers[i]++;
                    }
                    positions.add(maxPos);
                } else { // case 2: only advance minIndex pointer
                    pointers[minIndex]++;
                }
            }
            // add docId and positions list if not empty
            if (!positions.isEmpty()) {
                this.invertedList.appendPosting(docId, positions);
            }
            // loop to next doc
            this.args.get(0).docIteratorAdvancePast(docId);
        }

    }

    /**
     * helper function, result is the minimum value, maximum value and minimum value index
     * @param pointers
     * @return int[]
     */

    private int[] findMinMaxPos(int[] pointers) {
        int maxPos = 0;
        int minPos = Integer.MAX_VALUE;
        int minIndex = 0;
        int end_flag = 0;
        int res[] = new int[4];
        // get min position, max position, min index
        for (int i = 0; i < this.args.size(); i++) {
            QryIop curr = ((QryIop) this.args.get(i));
            int index = pointers[i];
            List<Integer> currPosList = new ArrayList<>(curr.docIteratorGetMatchPosting().positions);
            int listSize = currPosList.size();

            // check if index out of boundary, set a end flag
            if (index >= listSize) {
                end_flag = -1;
                break;
            }
            int currPos = currPosList.get(index);

            if (minPos <= currPos && maxPos >= currPos) {
                continue;
            }
            // update minPos, minIndex and maxPos
            if (minPos > currPos) {
                minPos = currPos;
                minIndex = i;
            }
            if (maxPos < currPos) {
                maxPos = currPos;
            }
        }
        // set return value
        res[0] = end_flag;
        res[1] = maxPos;
        res[2] = minPos;
        res[3] = minIndex;
        return res;
    }
}
