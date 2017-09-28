package ws.vinta.albedo

import org.apache.spark.sql.SparkSession
import ws.vinta.albedo.evaluators.RankingEvaluator
import ws.vinta.albedo.evaluators.RankingEvaluator._
import ws.vinta.albedo.schemas._
import ws.vinta.albedo.utils.DatasetUtils._

object RandomRecommender {
  def main(args: Array[String]): Unit = {
    implicit val spark: SparkSession = SparkSession
      .builder()
      .appName("RandomRecommender")
      .getOrCreate()

    import spark.implicits._

    // Load Data

    val rawUserInfoDS = loadUserInfoDS()
    rawUserInfoDS.cache()

    val rawRepoInfoDS = loadRepoInfoDS()
    rawRepoInfoDS.cache()

    val rawRepoStarringDS = loadRepoStarringDS()
    rawRepoStarringDS.cache()

    // Make Recommendations

    val k = 15

    val randomRepoDF = loadPopularRepoDF()
      .where($"stargazers_count".between(100, 5000))
      .orderBy($"stargazers_count".desc)
      .sample(withReplacement = false, 0.001, 42)
      .limit(k)
    randomRepoDF.cache()

    val userRandomRepoDF = rawUserInfoDS.select($"user_id")
      .crossJoin(randomRepoDF)
      .as[UserPopularRepo]

    // Evaluate the Model

    val userActualItemsDS = rawRepoStarringDS
      .transform(intoUserActualItems($"user_id", $"repo_id", $"starred_at", k))
      .as[UserItems]

    val userPredictedItemsDS = userRandomRepoDF
      .transform(intoUserPredictedItems($"user_id", $"repo_id", $"stargazers_count".desc))
      .as[UserItems]

    val rankingEvaluator = new RankingEvaluator(userActualItemsDS)
      .setMetricName("ndcg@k")
      .setK(k)
      .setUserCol("user_id")
      .setItemsCol("items")
    val metric = rankingEvaluator.evaluate(userPredictedItemsDS)
    println(s"${rankingEvaluator.getMetricName} = $metric")
    // NDCG@k = 0.00023420792326071322

    spark.stop()
  }
}