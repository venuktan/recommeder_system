package edu.umn.cs.recsys.ii;

import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import org.grouplens.lenskit.collections.LongUtils;
import org.grouplens.lenskit.core.Transient;
import org.grouplens.lenskit.cursors.Cursor;
import org.grouplens.lenskit.data.dao.ItemDAO;
import org.grouplens.lenskit.data.dao.UserEventDAO;
import org.grouplens.lenskit.data.event.Event;
import org.grouplens.lenskit.data.history.RatingVectorUserHistorySummarizer;
import org.grouplens.lenskit.data.history.UserHistory;
import org.grouplens.lenskit.scored.ScoredId;
import org.grouplens.lenskit.util.TopNScoredItemAccumulator;
import org.grouplens.lenskit.vectors.ImmutableSparseVector;
import org.grouplens.lenskit.vectors.MutableSparseVector;
import org.grouplens.lenskit.vectors.VectorEntry;
import org.grouplens.lenskit.vectors.similarity.CosineVectorSimilarity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author <a href="http://www.grouplens.org">GroupLens Research</a>
 */
public class SimpleItemItemModelBuilder implements Provider<SimpleItemItemModel> {
    private final ItemDAO itemDao;
    private final UserEventDAO userEventDao;
    private static final Logger logger = LoggerFactory.getLogger(SimpleItemItemModelBuilder.class);
    ;

    @Inject
    public SimpleItemItemModelBuilder(@Transient ItemDAO idao,
                                      @Transient UserEventDAO uedao) {
        itemDao = idao;
        userEventDao = uedao;
    }

//    1024:77 1024:268 1024:462 1024:393 1024:36955 2048:77 2048:36955 2048:788


    @Override
    public SimpleItemItemModel get() {
        // Get the transposed rating matrix
        // This gives us a map of item IDs to those items' rating vectors
        Map<Long, ImmutableSparseVector> itemVectors = getItemVectors();

        // Get all items - you might find this useful
        LongSortedSet items = LongUtils.packedSet(itemVectors.keySet());

        // Map items to vectors of item similarities
//        Map<Long,MutableSparseVector> itemSimilarities = new HashMap<Long, MutableSparseVector>();
        Map<Long, List<ScoredId>> neighbours = new HashMap<Long, List<ScoredId>>();

        // TODO FIXED Compute the similarities between each pair of items

        CosineVectorSimilarity similarityObject = new CosineVectorSimilarity();

        for (long itemId : items) {
            TopNScoredItemAccumulator accumulator = new TopNScoredItemAccumulator(items.size() - 1);
            for (long otherItemId : items) {
                if (itemId != otherItemId) {
                    double sim = similarityObject.similarity(itemVectors.get(itemId), itemVectors.get(otherItemId));
                    if (sim >= 0) {
                        accumulator.put(otherItemId, sim);
                    }
                }
            }
            List<ScoredId> similarities = accumulator.finish();
            neighbours.put(itemId, similarities);

        }

        // It will need to be in a map of longs to lists of Scored IDs to store in the model
//        return new SimpleItemItemModel(Collections.EMPTY_MAP);
        return new SimpleItemItemModel(neighbours);
    }

    /**
     * Load the data into memory, indexed by item.
     *
     * @return A map from item IDs to item rating vectors. Each vector contains users' ratings for
     * the item, keyed by user ID.
     */
    public Map<Long, ImmutableSparseVector> getItemVectors() {
        // set up storage for building each item's rating vector
        LongSet items = itemDao.getItemIds();
        // map items to maps from users to ratings
        Map<Long, Map<Long, Double>> itemData = new HashMap<Long, Map<Long, Double>>();
        for (long item : items) {
            itemData.put(item, new HashMap<Long, Double>());
        }
        // itemData should now contain a map to accumulate the ratings of each item

        // stream over all user events
        Cursor<UserHistory<Event>> stream = userEventDao.streamEventsByUser();
        try {
            for (UserHistory<Event> evt : stream) {
                MutableSparseVector vector = RatingVectorUserHistorySummarizer.makeRatingVector(evt).mutableCopy();
                // vector is now the user's rating vector
                // TODO Normalize this vector and store the ratings in the item data
                double userMean = vector.mean();

                vector.add(-userMean);
                for (VectorEntry eachItem : vector.fast()) {

                    itemData.get(eachItem.getKey()).put(evt.getUserId(), eachItem.getValue());
                }

            }
        } finally {
            stream.close();
        }

        // This loop converts our temporary item storage to a map of item vectors
        Map<Long, ImmutableSparseVector> itemVectors = new HashMap<Long, ImmutableSparseVector>();
        for (Map.Entry<Long, Map<Long, Double>> entry : itemData.entrySet()) {
            MutableSparseVector vec = MutableSparseVector.create(entry.getValue());
            itemVectors.put(entry.getKey(), vec.immutable());
        }
        return itemVectors;
    }
}
