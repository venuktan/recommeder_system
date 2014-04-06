package org.grouplens.mooc.cbf;

import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.grouplens.lenskit.core.Transient;
import org.grouplens.lenskit.vectors.MutableSparseVector;
import org.grouplens.lenskit.vectors.SparseVector;
import org.grouplens.lenskit.vectors.VectorEntry;
import org.grouplens.mooc.cbf.dao.ItemTagDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builder for computing {@linkplain TFIDFModel TF-IDF models} from item tag data.  Each item is
 * represented by a normalized TF-IDF vector.
 *
 * @author <a href="http://www.grouplens.org">GroupLens Research</a>
 */
public class TFIDFModelBuilder implements Provider<TFIDFModel> {
    private final ItemTagDAO dao;

    private static final Logger logger = LoggerFactory.getLogger(TFIDFModelBuilder.class);


    /**
     * Construct a model builder.  The {@link Inject} annotation on this constructor tells LensKit
     * that it can be used to build the model builder.
     *
     * @param dao The item-tag DAO.  This is where the builder will get access to items and their
     *            tags.
     *            <p>{@link Transient} means that the provider promises that the DAO is no longer
     *            needed once the object is built (that is, the model will not contain a reference
     *            to the DAO).  This allows LensKit to configure your recommender components
     *            properly.  It's up to you to keep this promise.</p>
     */
    @Inject
    public TFIDFModelBuilder(@Transient ItemTagDAO dao) {
        this.dao = dao;
    }

    /**
     * This method is where the model should actually be computed.
     *
     * @return The TF-IDF model (a model of item tag vectors).
     */
    @Override
    public TFIDFModel get() {
        // Build a map of tags to numeric IDs.  This lets you convert tags (which are strings)
        // into long IDs that you can use as keys in a tag vector.
        Map<String, Long> tagIds = buildTagIdMap();

        // Create a vector to accumulate document frequencies for the IDF computation
        MutableSparseVector docFreq = MutableSparseVector.create(tagIds.values());
        docFreq.fill(0);

        // We now proceed in 2 stages. First, we build a TF vector for each item.
        // While we do this, we also build the DF vector.
        // We will then apply the IDF to each TF vector and normalize it to a unit vector.

        // Create a map to store the item TF vectors.
        Map<Long, MutableSparseVector> itemVectors = Maps.newHashMap();

        // Create a work vector to accumulate each item's tag vector.
        // This vector will be re-used for each item.
        MutableSparseVector work = MutableSparseVector.create(tagIds.values());

        // Iterate over the items to compute each item's vector.
        LongSet items = dao.getItemIds();
        for (long item : items) {
            // Reset the work vector for this item's tags.
            work.clear();
            // Now the vector is empty (all keys are 'unset').

            // TODO Populate the work vector with the number of times each tag is applied to this item.

            List<String> itemTagsList = dao.getItemTags(item);

            Set<String> itemTagSet = new HashSet<String>(itemTagsList);

            for (String eachItemTag : itemTagsList) {

                if (work.containsKey(tagIds.get(eachItemTag))) {

                    work.set(tagIds.get(eachItemTag), work.get(tagIds.get(eachItemTag)) + 1);
//                    logger.info("working on TF{}", tagIds.get(eachItemTag));

                } else {
                    work.set(tagIds.get(eachItemTag), 1);
                }
                // TODO Increment the document frequency vector once for each unique tag on the item.
                if (docFreq.containsKey(tagIds.get(eachItemTag))) {

                    docFreq.set(tagIds.get(eachItemTag), docFreq.get(tagIds.get(eachItemTag)) + 1);
                } else {
                    docFreq.set(tagIds.get(eachItemTag), 1);
                }


            }


            // Save a shrunk copy of the vector (only storing tags that apply to this item) in
            // our map, we'll add IDF and normalize later.
            itemVectors.put(item, work.shrinkDomain());
            // work is ready to be reset and re-used for the next item
        }

        // Now we've seen all the items, so we have each item's TF vector and a global vector
        // of document frequencies.
        // Invert and log the document frequency.  We can do this in-place.
        double numOfMovies = dao.getItemIds().size();
        for (VectorEntry e : docFreq.fast()) {

            // TODO Update this document frequency entry to be a log-IDF value
            double iDF = Math.log(numOfMovies / e.getValue());
            docFreq.set(e.getKey(), iDF);
        }

        // Now docFreq is a log-IDF vector.
        // So we can use it to apply IDF to each item vector to put it in the final model.
        // Create a map to store the final model data.
        Map<Long, SparseVector> modelData = Maps.newHashMap();
        for (Map.Entry<Long, MutableSparseVector> entry : itemVectors.entrySet()) {
            MutableSparseVector tv = entry.getValue();
            // TODO Convert this vector to a TF-IDF vector

            for (VectorEntry eachTag : tv.fast()) {

                tv.set(eachTag.getKey(), eachTag.getValue() * docFreq.get(eachTag.getKey()));
            }
            // TODO Normalize the TF-IDF vector to be a unit vector

            double lengthOfMovieVector = tv.norm();

//            System.out.println(lengthOfMovieVector+" \t"+1/lengthOfMovieVector);

            tv.multiply(1 / lengthOfMovieVector);

            // HINT The method tv.norm() will give you the Euclidian length of the vector
//            Once you have the TFIDF vector, you could perform the unit normalization in one step using the multiply method provided for sparse vectors.
//            The syntax goes like tv.multiply(d) where d is of type double.  For unit normalization you need to multiply by 1/tv.norm()

            // Store a frozen (immutable) version of the vector in the model data.
            modelData.put(entry.getKey(), tv.freeze());
        }

        // we technically don't need the IDF vector anymore, so long as we have no new tags
        return new TFIDFModel(tagIds, modelData);

    }

    /**
     * Build a mapping of tags to numeric IDs.
     *
     * @return A mapping from tags to IDs.
     */
    private Map<String, Long> buildTagIdMap() {
        // Get the universe of all tags
        Set<String> tags = dao.getTagVocabulary();
        // Allocate our new tag map
        Map<String, Long> tagIds = Maps.newHashMap();

        for (String tag : tags) {
            // Map each tag to a new number.
            tagIds.put(tag, tagIds.size() + 1L);
        }
        return tagIds;
    }


}
