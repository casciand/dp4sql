package CustomDP4SQL.Interface

import CustomDP4SQL.Calcite.RelAlgebraMapper
import CustomDP4SQL.Common.SchemaMapper
import CustomDP4SQL.DataModel.Schema.{Datatype, Relation, Schema}
import CustomDP4SQL.Inference.{ActionCalculator, SensitivityCalculator}
import org.apache.calcite.plan.RelOptUtil
import org.apache.calcite.sql.SqlExplainLevel

object CommandLineInterface {
  private val schemaMapper: SchemaMapper = SchemaMapper()

  private def printDatatype(datatype: Datatype): Unit = {
    val fieldNames = datatype.productElementNames
    for ((field, i) <- fieldNames.zipWithIndex) {
      val value = datatype.productElement(i)

      value match {
        case some: Some[Any] => print(s"$field=${some.get}, ")
        case none: None.type =>
        case concrete => print(s"$field=$concrete, ")
      }
    }

    println()
  }

  private def printRelation(relation: Relation): Unit = {
    println(relation.name)
    println(s"identifier: ${relation.identifier}")
    relation.attributes.foreach(attribute => {
      print(s"${attribute.name}: ")
      printDatatype(attribute.datatype)
    })
  }

  private def printSchema(schema: Schema): Unit = {
    schema.relations.foreach(relation => {
      printRelation(relation)
      println()
    })
  }

  private def showWelcome(): Unit = {
    println()
    println("Welcome to CustomDP4SQL.")
  }

  private def showMenu(): Unit = {
    println("\nOptions:")
    println("1. Show Schema")
    println("2. Show Attributes for Relation")
    println("3. Show Relational Algebra Tree for Query")
    println("4. Derive Plausible Deniability Action for Query")
    println("5. Derive Laplacian Noise for Query")
    println("6. Exit\n")
    print("Select Option: ")
  }

  /**
   * CLI entry point.
   *
   * @param args Command-line arguments: [0] Config YAML path
   */
  def main(args: Array[String]): Unit = {
    // Load config and schema
    val configPath = args(0)
    val config = schemaMapper.yamlToConfig(configPath)
    val schema = schemaMapper.yamlToSchema(config.schema_path)

    val relationNames = schema.relations.map(_.name)
    val queryNames = config.queries.keys.toList.sortBy(name => name.toInt)
    var running = true

    // showWelcome()

    while (running) {
      showMenu()

      scala.io.StdIn.readLine().toIntOption.getOrElse(-1) match {
        case 1 =>
          println("\nSchema Definition:\n")
          printSchema(schema)
        case 2 =>
          println("\nRelations:")
          val relations = relationNames.zipWithIndex.toList.sortBy((_, i) => i)
          relations.foreach { case (name, i) => println(s"${i + 1}. $name") }
          print("\nSelect Relation: ")
          val input = scala.io.StdIn.readLine().toIntOption.getOrElse(0) - 1
          println()

          if (input >= 0 && input < relationNames.size) {
            val relation = relations.find((_, i) => i == input)
            println()
            printRelation(schema.relations.find(_.name == relation.get._1).get)
            println()
          } else {
            println("Invalid table selection.")
          }
        case 3 =>
          println("\nQueries:")
          queryNames.zipWithIndex.foreach { case (name, i) => println(s"${i + 1}. q$name") }
          print("\nSelect Query: ")
          val input = scala.io.StdIn.readLine().toIntOption.getOrElse(0) - 1
          println()

          if (input >= 0 && input < queryNames.length) {
            val sql = config.queries(queryNames(input))
            val schemaPlus = RelAlgebraMapper.createCalciteSchema(schema, config)
            val rootNode = RelAlgebraMapper.sqlToRelNode(sql, schemaPlus)
            val treeString = RelAlgebraMapper.processRelTreeWithPlaceholders(rootNode)

            println("\nRelational Algebra Tree:\n")
            println(treeString)
          } else  {
            println("Invalid query selection.")
          }
        case 4 =>
          println("\nQueries:")
          queryNames.zipWithIndex.foreach { case (name, i) => println(s"${i + 1}. q$name") }
          print("\nSelect Query: ")
          val input = scala.io.StdIn.readLine().toIntOption.getOrElse(0) - 1
          println()

          if (input >= 0 && input < queryNames.length) {
            val sql = config.queries(queryNames(input))
            val schemaPlus = RelAlgebraMapper.createCalciteSchema(schema, config)
            val rootNode = RelAlgebraMapper.sqlToRelNode(sql, schemaPlus)

            config.privacy_models.foreach((name, path) => {
              val privacyModel = schemaMapper.yamlToPrivacyModel(path)
              val stabilityCalculator = ActionCalculator(config, privacyModel)
              val relNodeActionMap = stabilityCalculator.deriveActionsForTree(rootNode)
              println(s"[$name] Action: ${relNodeActionMap(rootNode)}")
            })
          } else  {
            println("Invalid query selection.")
          }
        case 5 =>
          println("\nQueries:")
          queryNames.zipWithIndex.foreach { case (name, i) => println(s"${i + 1}. q$name") }
          print("\nSelect Query: ")
          val input = scala.io.StdIn.readLine().toIntOption.getOrElse(0) - 1
          println()

          if (input >= 0 && input < queryNames.length) {
            val sql = config.queries(queryNames(input))
            val schemaPlus = RelAlgebraMapper.createCalciteSchema(schema, config)
            val rootNode = RelAlgebraMapper.sqlToRelNode(sql, schemaPlus)
            val sensitivityCalculator = SensitivityCalculator()

            config.privacy_models.foreach((name, path) => {
              val privacyModel = schemaMapper.yamlToPrivacyModel(path)
              val stabilityCalculator = ActionCalculator(config, privacyModel)
              val relNodeActionMap = stabilityCalculator.deriveActionsForTree(rootNode)
              val sensitivity = sensitivityCalculator.computeSensitivity(rootNode, relNodeActionMap(rootNode))
              println(s"[$name] Sensitivity: ${sensitivity}")
            })
          } else  {
            println("Invalid query selection.")
          }
        case 6 =>
          running = false
        case -1 =>
          println("Invalid option.")
      }
    }
  }
}
