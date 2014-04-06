package edu.umn.cs.recsys.uu;

import it.unimi.dsi.fastutil.longs.LongSet;
import org.grouplens.lenskit.basic.AbstractItemScorer;
import org.grouplens.lenskit.data.dao.ItemEventDAO;
import org.grouplens.lenskit.data.dao.UserEventDAO;
import org.grouplens.lenskit.data.event.Rating;
import org.grouplens.lenskit.data.history.History;
import org.grouplens.lenskit.data.history.RatingVectorUserHistorySummarizer;
import org.grouplens.lenskit.data.history.UserHistory;
import org.grouplens.lenskit.vectors.MutableSparseVector;
import org.grouplens.lenskit.vectors.SparseVector;
import org.grouplens.lenskit.vectors.VectorEntry;
import org.grouplens.lenskit.vectors.similarity.CosineVectorSimilarity;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.Collections;
import java.util.SortedMap;
import java.util.TreeMap;



/*
* Algorithm is as follows ( refined as contribution comes along )
Get all users
Get all users who have rated the item
Compute the mean centered rating for the users who have rated the item ( item rating - mean rating )
Compute the cosine similarity of these users obtained in Step 3
Sort the list
For the first 30 users do the following steps from 6 to 9 ( In this case , do not consider the user for which the scoring is to be done )
Compute their mean rating
Use their rating for the item
Subtract Item rating from mean rating
Multiply the result in Step 8 with cosine similarity of the user vector
Sum the results
Sum the absolute Value of the cosine similarities of the 30 users
Divide step 10 by Step 11
Add the mean rating of the User with Step 12.
You may omit Step 1  and go to Step 2 directly using ( thanks Kevin Li)
LongSet possibleNeighbors = itemDao.getUsersForItem(itemID);
Hints notes
1024,77,4.3848,Memento (2000)
1024,268,2.8646,Batman (1989) are provided below.
* */

/**
 * User-user item scorer.
 *
 * @author <a href="http://www.grouplens.org">GroupLens Research</a>
 */
public class SimpleUserUserItemScorer extends AbstractItemScorer {
    private final UserEventDAO userDao;
    private final ItemEventDAO itemDao;

    @Inject
    public SimpleUserUserItemScorer(UserEventDAO udao, ItemEventDAO idao) {
        userDao = udao;
        itemDao = idao;
    }


    @Override
    public void score(long user, @Nonnull MutableSparseVector scores) {
        SparseVector myUserVector = getUserRatingVector(user);

        double myMeanRantings = myUserVector.mean();


        myUserVector = getMeanCenteredVector(myUserVector.mutableCopy());

        // TODO Score items for this user using user-user collaborative filtering

        // This is the loop structure to iterate over items to score
        for (VectorEntry myItemsToBeRated : scores.fast(VectorEntry.State.EITHER)) {

            //get a set of users who already rated this item
            LongSet neighboursForThisItem = itemDao.getUsersForItem(myItemsToBeRated.getKey());

//            Tree map to keep them sorted to get the top 30 neighbours quickly
            SortedMap<Double, Double> top30NeighSimi = new TreeMap<Double, Double>(Collections.reverseOrder());


//            iterate over the above users set
            for (Long eachNeighbour : neighboursForThisItem) {

//              get the user history as sparce vector
                SparseVector neighbourHistory = getUserRatingVector(eachNeighbour);

                neighbourHistory = getMeanCenteredVector(neighbourHistory.mutableCopy());

                double neighbourMeanCenteredRating = neighbourHistory.get(myItemsToBeRated.getKey());

                double cosineSimilarityBwMeAndNeighbour = new CosineVectorSimilarity().similarity(myUserVector, neighbourHistory);

                top30NeighSimi.put(cosineSimilarityBwMeAndNeighbour, neighbourMeanCenteredRating);

            }

            // removing  self correlation from the vector

            top30NeighSimi.remove((Double) 1.0);

            double sumOfCosineSim_ratings = 0; // numerator

            double vectorCosineSimTop30 = 0;  // denominator

            int counter = 0;

//            iterate over the top 30 and compute the above two i.e. sumOfCosineSim_ratings and vectorCosineSimTop30

            for (Double entry : top30NeighSimi.keySet()) {
                if (counter == 30)
                    break;

                sumOfCosineSim_ratings += (entry * top30NeighSimi.get(entry));

                vectorCosineSimTop30 += Math.abs(entry);

                counter++;
            }

            double predictionForThisItem = myMeanRantings + (sumOfCosineSim_ratings / vectorCosineSimTop30);

            scores.set(myItemsToBeRated.getKey(), predictionForThisItem);
        }
    }

    /**
     * Get a user's rating vector.
     *
     * @param user The user ID.
     * @return The rating vector.
     */
    private SparseVector getUserRatingVector(long user) {
        UserHistory<Rating> history = userDao.getEventsForUser(user, Rating.class);
        if (history == null) {
            history = History.forUser(user);
        }
        return RatingVectorUserHistorySummarizer.makeRatingVector(history);
    }

    //     takes a mutable sparce vector and returns its mean centered sparce vector in immutable form
    private SparseVector getMeanCenteredVector(MutableSparseVector userVector) {

        double userMean = userVector.mean();

        for (VectorEntry eachItem : userVector.fast()) {
            userVector.set(eachItem.getKey(), eachItem.getValue() - userMean);
        }

        return userVector.immutable();
    }
}
