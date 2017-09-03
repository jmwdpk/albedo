package ws.vinta.albedo.utils

import java.util.Properties
import org.apache.spark.sql.functions.lit
import org.apache.spark.sql.{AnalysisException, DataFrame, Dataset, SparkSession}
import ws.vinta.albedo.schemas.{RepoInfo, RepoStarring, UserInfo, UserRelation, PopularRepo}
import org.apache.spark.ml.feature.SQLTransformer

object DatasetUtils {
  private val dbUrl = "jdbc:mysql://127.0.0.1:3306/albedo?verifyServerCertificate=false&useSSL=false&rewriteBatchedStatements=true"
  private val props = new Properties()
  props.setProperty("driver", "com.mysql.jdbc.Driver")
  props.setProperty("user", "root")
  props.setProperty("password", "123")

  def loadUserInfo()(implicit spark: SparkSession): Dataset[UserInfo] = {
    import spark.implicits._

    val savePath = s"${Settings.dataDir}/${Settings.today}/userInfoDF.parquet"
    val df: DataFrame = try {
      spark.read.parquet(savePath)
    } catch {
      case e: AnalysisException => {
        if (e.getMessage().contains("Path does not exist")) {
          val df = spark.read.jdbc(dbUrl, "app_userinfo", props).withColumnRenamed("id", "user_id")
          df.write.parquet(savePath)
          df
        } else {
          throw e
        }
      }
    }

    df.as[UserInfo]
  }

  def loadUserRelation()(implicit spark: SparkSession): Dataset[UserRelation] = {
    import spark.implicits._

    val savePath = s"${Settings.dataDir}/${Settings.today}/userRelationDF.parquet"
    val df: DataFrame = try {
      spark.read.parquet(savePath)
    } catch {
      case e: AnalysisException => {
        if (e.getMessage().contains("Path does not exist")) {
          val df = spark.read.jdbc(dbUrl, "app_userrelation", props).select($"from_user_id", $"to_user_id", $"relation")
          df.write.parquet(savePath)
          df
        } else {
          throw e
        }
      }
    }

    df.as[UserRelation]
  }

  def loadRepoInfo()(implicit spark: SparkSession): Dataset[RepoInfo] = {
    import spark.implicits._

    val savePath = s"${Settings.dataDir}/${Settings.today}/repoInfoDF.parquet"
    val df: DataFrame = try {
      spark.read.parquet(savePath)
    } catch {
      case e: AnalysisException => {
        if (e.getMessage().contains("Path does not exist")) {
          val df = spark.read.jdbc(dbUrl, "app_repoinfo", props).withColumnRenamed("id", "repo_id")
          df.write.parquet(savePath)
          df
        } else {
          throw e
        }
      }
    }

    df.as[RepoInfo]
  }

  def loadRepoStarring()(implicit spark: SparkSession): Dataset[RepoStarring] = {
    import spark.implicits._

    val savePath = s"${Settings.dataDir}/${Settings.today}/repoStarringDF.parquet"
    val df: DataFrame = try {
      spark.read.parquet(savePath)
    } catch {
      case e: AnalysisException => {
        if (e.getMessage().contains("Path does not exist")) {
          val df = spark.read.jdbc(dbUrl, "app_repostarring", props)
            .select($"user_id", $"repo_id", $"starred_at")
            .withColumn("starring", lit(1.0))
          df.write.parquet(savePath)
          df
        } else {
          throw e
        }
      }
    }

    df.as[RepoStarring]
  }

  def loadPopularRepos()(implicit spark: SparkSession): Dataset[PopularRepo] = {
    import spark.implicits._

    val popularReposSQL = """
    SELECT repo_id, stargazers_count
    FROM __THIS__
    WHERE stargazers_count > 1000
    ORDER BY stargazers_count DESC
    """
    val popularReposBuilder = new SQLTransformer()
    popularReposBuilder.setStatement(popularReposSQL)

    val savePath = s"${Settings.dataDir}/${Settings.today}/popularReposDF.parquet"
    def df: DataFrame = try {
      spark.read.parquet(savePath)
    } catch {
      case e: AnalysisException => {
        if (e.getMessage().contains("Path does not exist")) {
          val rawRepoInfoDS = loadRepoInfo()
          val df = popularReposBuilder.transform(rawRepoInfoDS)
          df.write.parquet(savePath)
          df
        } else {
          throw e
        }
      }
    }

    df.as[PopularRepo]
  }
}