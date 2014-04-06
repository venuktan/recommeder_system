package org.grouplens.mooc.cbf;

import org.grouplens.lenskit.basic.AbstractItemScorer;
import org.grouplens.lenskit.data.dao.UserEventDAO;
import org.grouplens.lenskit.data.event.Rating;
import org.grouplens.lenskit.data.history.History;
import org.grouplens.lenskit.data.history.RatingVectorUserHistorySummarizer;
import org.grouplens.lenskit.data.history.UserHistory;
import org.grouplens.lenskit.vectors.MutableSparseVector;
import org.grouplens.lenskit.vectors.SparseVector;
import org.grouplens.lenskit.vectors.VectorEntry;

import javax.annotation.Nonnull;
import javax.inject.Inject;

/**
 * @author <a href="http://www.grouplens.org">GroupLens Research</a>
 */
public class TFIDFItemScorer extends AbstractItemScorer {
    public static UserEventDAO userEvents;
    private final TFIDFModel model;

    /**
     * Construct a new item scorer.  LensKit's dependency injector will call this constructor and
     * provide the appropriate parameters.
     *
     * @param dao The user-event DAO, so we can fetch a user's ratings when scoring items for them.
     * @param m   The precomputed model containing the item tag vectors.
     */
    @Inject
    public TFIDFItemScorer(UserEventDAO dao, TFIDFModel m) {
        this.userEvents = dao;
        model = m;
    }

    /**
     * Generate item scores personalized for a particular user.  For the TFIDF scorer, this will
     * prepare a user profile and compare it to item tag vectors to produce the score.
     *
     * @param user   The user to score for.
     * @param output The output vector.  The contract of this method is that the caller creates a
     *               vector whose possible keys are all items that should be scored; this method
     *               fills in the scores.
     */
    @Override
    public void score(long user, @Nonnull MutableSparseVector output) {
        // Get the user's profile, which is a vector with their 'like' for each tag
        SparseVector userVector = makeUserVector(user);

        // Loop over each item requested and score it.
        // The *domain* of the output vector is the items that we are to score.
        for (VectorEntry e : output.fast(VectorEntry.State.EITHER)) {
            // Score the item represented by 'e'.
            // Get the item vector for this item
            SparseVector iv = model.getItemVector(e.getKey());
            // TODO Compute the cosine of this item and the user's profile, store it in the output vector
            // TODO And remove this exception to say you've implemented it

//            cos(u,i)=u⃗ ⋅i⃗ ∥u⃗ ∥2∥i⃗ ∥2=∑tutit∑tu2t‾‾‾‾‾‾√∑ti2t‾‾‾‾‾√

            double similarity = userVector.dot(iv) / (userVector.norm() * iv.norm());
            double score = userVector.mean() + similarity;

            output.set(e, score);
//            throw new UnsupportedOperationException("stub implementation");

        }
    }

    private SparseVector makeUserVector(long user) {

//        TODO Get the item's vector and add it to the user's profile

        UserHistory<Rating> history = userEvents.getEventsForUser(user, Rating.class);
        if (history == null) {
            history = History.forUser(user);
        }
        // The profile is accumulated, return it.
        // It is good practice to return a frozen vector.
        return RatingVectorUserHistorySummarizer.makeRatingVector(history);
    }
}
