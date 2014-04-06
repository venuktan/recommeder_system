import edu.umn.cs.recsys.PopularityItemScorer
import edu.umn.cs.recsys.TagEntropyMetric
import edu.umn.cs.recsys.TagVocabulary
import edu.umn.cs.recsys.dao.CSVItemTagDAO
import edu.umn.cs.recsys.dao.ItemTagDAO
import edu.umn.cs.recsys.dao.TagFile
import edu.umn.cs.recsys.dao.TitleFile
import org.grouplens.lenskit.ItemRecommender
import org.grouplens.lenskit.ItemScorer
import org.grouplens.lenskit.RatingPredictor
import org.grouplens.lenskit.baseline.GlobalMeanRatingItemScorer
import org.grouplens.lenskit.baseline.ItemMeanRatingItemScorer
import org.grouplens.lenskit.baseline.UserMeanBaseline
import org.grouplens.lenskit.baseline.UserMeanItemScorer
import org.grouplens.lenskit.data.dao.ItemDAO
import org.grouplens.lenskit.eval.data.crossfold.RandomOrder
import org.grouplens.lenskit.eval.metrics.predict.CoveragePredictMetric
import org.grouplens.lenskit.eval.metrics.predict.NDCGPredictMetric
import org.grouplens.lenskit.eval.metrics.predict.RMSEPredictMetric
import org.grouplens.lenskit.eval.metrics.topn.ItemSelectors

// common configuration to make tags available
// needed for both some algorithms and for metrics
// this defines a variable containing a Groovy closure, if you care about that kind of thing
tagConfig = {
    bind ItemDAO to CSVItemTagDAO
    set TagFile to new File("${project.config.dataDir}/movie-tags.csv")
    set TitleFile to new File("${project.config.dataDir}/movie-titles.csv")
    // need tag vocab & item DAO to be roots for diversity metric to use them
    config.addRoot ItemTagDAO
    config.addRoot TagVocabulary
}

// Run a train-test evaluation
trainTest {
    dataset crossfold("RecSysMOOC") {
        source csvfile("MOOCRatings") {
            file "${project.config.dataDir}/ratings.csv"
            delimiter ","
            domain {
                minimum 0.5
                maximum 5.0
                precision 0.5
            }
        }
        test "target/crossfold-5/test.%d.csv"
        train "target/crossfold-5/train.%d.csv"

        // hold out 5 random items from each user
        order RandomOrder
        holdout 5

        // split users into 5 sets
        partitions 5

    }

    // Three different types of output for analysis.
    output "${project.config.analysisDir}/eval-results.csv"
    userOutput "${project.config.analysisDir}/eval-user.csv"

    metric CoveragePredictMetric
    metric RMSEPredictMetric
    metric NDCGPredictMetric

    // Compute nDCG trying to recommend lists of 10 from all items
    // This suffers from similar problems as the unary ratings case!
    metric topNnDCG {
        candidates ItemSelectors.allItems()
        exclude ItemSelectors.trainingItems()
        listSize 10
    }
    // measure the entropy of the top 10 items
    metric new TagEntropyMetric(10)

    algorithm("GlobalMean") {
        include tagConfig
        // score items by the global mean
        bind ItemScorer to GlobalMeanRatingItemScorer
        // recommendation is meaningless for this algorithm
        bind ItemRecommender to null
    }
    algorithm("Popular") {
        include tagConfig
        // score items by their popularity
        bind ItemScorer to PopularityItemScorer
        // rating prediction is meaningless for this algorithm
        bind RatingPredictor to null
    }
    algorithm("ItemMean") {
        include tagConfig
        // score items by their mean rating
        bind ItemScorer to ItemMeanRatingItemScorer
    }
    algorithm("PersMean") {
        include tagConfig
        bind ItemScorer to UserMeanItemScorer
        bind(UserMeanBaseline, ItemScorer) to ItemMeanRatingItemScorer
    }


}

for (nnbrs in [5, 10, 15, 20, 25, 30, 40, 50, 75, 100]) {
    algorithm("UserUser") {
        include tagConfig
        // Attributes let you specify additional properties of the algorithm.
        // They go in the output file, so you can do things like plot accuracy by neighborhood size
        attributes["NNbrs"] = nnbrs
        // use the user-user rating predictor
        bind ItemScorer to UserUserItemScorer
        set NeighborhoodSize to nnbrs
        bind VectorSimilarity to PearsonCorrelation
    }

    algorithm("UserUserNorm") {
        include tagConfig
        // Attributes let you specify additional properties of the algorithm.
        // They go in the output file, so you can do things like plot accuracy by neighborhood size
        attributes["NNbrs"] = nnbrs
        // use the user-user rating predictor
        bind ItemScorer to UserUserItemScorer
        set NeighborhoodSize to nnbrs
        bind VectorNormalizer to MeanCenteringVectorNormalizer
        bind VectorSimilarity to PearsonCorrelation
    }

    algorithm("Lucene") {
        attributes["NNbrs"] = nnbrs
        include tagConfig
        bind ItemScorer to ItemItemScorer
        bind ItemItemModel to LuceneItemItemModel
        set NeighborhoodSize to nnbrs
        // consider using all 100 movies as neighbors
        set ModelSize to 100
    }

//        algorithm("LuceneNorm") {
//            attributes["NNbrs"] = nnbrs
//            include tagConfig
//            bind ItemScorer to ItemItemScorer
//            bind ItemItemModel to LuceneItemItemModel
//            set NeighborhoodSize to nnbrs
//            // consider using all 100 movies as neighbors
//            set ModelSize to 100
//            bind UserVectorNormalizer to BaselineSubtractingUserVectorNormalizer
//            within(UserVectorNormalizer) {
//                bind(BaselineScorer, ItemScorer) to ItemMeanRatingItemScorer
//            }
//        }

}

