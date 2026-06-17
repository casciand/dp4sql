package CustomDP4SQL.Interface

import CustomDP4SQL.Calcite.RelAlgebraMapper
import CustomDP4SQL.Common.SchemaMapper
import CustomDP4SQL.DataModel.Schema.{Datatype, Relation, Schema}
import CustomDP4SQL.Inference.{ActionCalculator, NoiseCalculator, SensitivityCalculator}

import java.io.{File, PrintWriter}

object QueryRunner {
  private val schemaMapper: SchemaMapper = SchemaMapper()

  /**
   * Entry point.
   *
   * @param args Command-line arguments: [0] Config YAML path, [1] Results folder path
   */
  def main(args: Array[String]): Unit = {
    val configPath = args(0)
    val resultsPath = args(1)
    val config = schemaMapper.yamlToConfig(configPath)
    val schema = schemaMapper.yamlToSchema(config.schema_path)

    val relationNames = schema.relations.map(_.name)
    val queryNames = config.queries.keys.toList.sortBy(name => name.toInt)

    queryNames.foreach(query => {
      println(s"q$query")
      val sensitivityWriter = new PrintWriter(new File(s"$resultsPath/q${query}_sensitivity.csv"))
      val noiseWriter = new PrintWriter(new File(s"$resultsPath/q${query}_noise.csv"))
      val sql = config.queries(query)
      val schemaPlus = RelAlgebraMapper.createCalciteSchema(schema, config)
      val rootNode = RelAlgebraMapper.sqlToRelNode(sql, schemaPlus)
      val sensitivityCalculator = SensitivityCalculator(schema)
      val noiseCalculator = NoiseCalculator()

      try {
        config.privacy_models.toList.sorted.foreach((name, path) => {
          val privacyModel = schemaMapper.yamlToPrivacyModel(path)
          val stabilityCalculator = ActionCalculator(config, privacyModel)
          val relNodeActionMap = stabilityCalculator.deriveActionsForTree(rootNode)
          val sensitivity = sensitivityCalculator.computeSensitivity(rootNode, relNodeActionMap)

          println(s"[$name] Sensitivity: ${sensitivity.mkString(",")}")
          sensitivityWriter.println(name + "," + sensitivity.mkString(","))
          noiseWriter.print(name + ",")
          noiseWriter.println((1 to 50)
            .map(_ => noiseCalculator.computeNoise(sensitivity).mkString(","))
            .mkString(","))
        })

        sensitivityWriter.close()
        noiseWriter.close()
      } catch {
        case _: Throwable => println("ERROR")
      }

      println()
    })

  }
}
