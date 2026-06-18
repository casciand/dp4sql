package CustomDP4SQL.Validation

import CustomDP4SQL.Common.{Constants, SchemaMapper}
import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory

import scala.jdk.CollectionConverters.IteratorHasAsScala
import scala.util.{Failure, Success, Try}

object SchemaValidator {
  private val mapper = new ObjectMapper(new YAMLFactory())
  private val schemaMapper: SchemaMapper = SchemaMapper()

  case class ForeignKeyInfo(fromTable: String, fromColumn: String, toTable: String, toColumn: String, isUnique: Boolean, isOnto: Boolean)

  case class ColumnInfo(name: String, dataType: String)

  case class TableInfo(name: String, columns: List[ColumnInfo], primaryKey: List[String])

  def validateSchemaFile(filePath: String): Try[Unit] = {
    for {
      rootNode <- Try(mapper.readTree(new java.io.File(filePath)))
      _ <- validateSchema(rootNode)
    } yield ()
  }

  private def validateSchema(rootNode: JsonNode): Try[Unit] = {
    if (!rootNode.has("tables") || !rootNode.get("tables").isArray) {
      return Failure(new IllegalArgumentException("Root should have a 'databases' array"))
    }

    val foreignKeys = collection.mutable.ListBuffer[ForeignKeyInfo]()
    val tables = collection.mutable.ListBuffer[TableInfo]()
    val collectTablesResult = validateTables(rootNode.get("tables"), foreignKeys, tables)
    collectTablesResult.flatMap(_ => validateForeignKeyGraph(foreignKeys.toList, tables.toList))
  }

  private def validateTables(tablesNode: JsonNode, foreignKeys: collection.mutable.ListBuffer[ForeignKeyInfo], tables: collection.mutable.ListBuffer[TableInfo]): Try[Unit] = {
    tablesNode.elements().asScala.foldLeft[Try[Unit]](Success(())) { (accTry, tableNode) =>
      accTry.flatMap(_ => validateTable(tableNode, foreignKeys, tables))
    }
  }

  private def validateTable(tableNode: JsonNode, foreignKeys: collection.mutable.ListBuffer[ForeignKeyInfo], tables: collection.mutable.ListBuffer[TableInfo]): Try[Unit] = {
    for {
      _ <- validateRequiredField(tableNode, "name", _.isTextual)
      tableName = tableNode.get("name").asText()
      _ <- validateRequiredField(tableNode, "primary_key", _.isArray)
      primaryKey <- validatePrimaryKey(tableNode)
      _ <- validateRequiredField(tableNode, "columns", _.isArray)
      columns <- validateColumns(tableNode.get("columns"), tableName, foreignKeys, tables)
      _ = tables += TableInfo(tableName, columns, primaryKey)
    } yield ()
  }

  private def validatePrimaryKey(tableNode: JsonNode): Try[List[String]] = {
    Try {
      val primaryKeyNode = tableNode.get("primary_key")
      val primaryKey = primaryKeyNode.elements().asScala.map(_.asText()).toList
      if (primaryKey.isEmpty) {
        throw new IllegalArgumentException(s"Primary key must not be empty for table ${tableNode.get("table").asText()}")
      }
      primaryKey
    }
  }

  private def validateColumns(columnsNode: JsonNode, tableName: String, foreignKeys: collection.mutable.ListBuffer[ForeignKeyInfo], tables: collection.mutable.ListBuffer[TableInfo]): Try[List[ColumnInfo]] = {
    columnsNode.elements().asScala.foldLeft[Try[List[ColumnInfo]]](Success(List.empty)) { (accTry, columnNode) =>
      accTry.flatMap { acc =>
        for {
          _ <- validateRequiredField(columnNode, "name", _.isTextual)
          columnName = columnNode.get("name").asText()
          _ <- validateRequiredField(columnNode, "datatype", _.isTextual)
          dataType = columnNode.get("datatype").asText()
          _ <- validateRequiredField(columnNode, "constraints", _.isObject)
          _ <- validateDataType(dataType, columnNode.get("constraints"), tableName, columnName, foreignKeys, tables)
        } yield acc :+ ColumnInfo(columnName, dataType)
      }
    }
  }

  private def validateDataType(dataType: String, typeParamsNode: JsonNode, tableName: String, columnName: String, foreignKeys: collection.mutable.ListBuffer[ForeignKeyInfo], tables: collection.mutable.ListBuffer[TableInfo]): Try[Unit] = {
    dataType.toLowerCase match {
      case "string" => validateStringType(typeParamsNode, tableName, columnName)
      case "int" => validateIntType(typeParamsNode, tableName, columnName)
      case "decimal" => validateDecimalType(typeParamsNode, tableName, columnName)
      case "categorical" => validateCategoricalType(typeParamsNode, tableName, columnName)
      case "datetime" => validateDatetimeType(typeParamsNode, tableName, columnName)
      //maybe moving foreign key rerefence to a separate function
      case "foreign key" => validateForeignKeyType(typeParamsNode, tableName, columnName, foreignKeys, tables)
      case _ => Failure(new IllegalArgumentException(s"Invalid data type: $dataType for column $columnName in table $tableName"))
    }
  }

  private def validateStringType(typeParams: JsonNode, tableName: String, columnName: String): Try[Unit] = {
    for {
      _ <- validateRequiredField(typeParams, "max_length", node => node.isInt && node.asInt() > 0)
      _ <- validateOptionalField(typeParams, "nullable", _.isBoolean)
      _ <- validateOptionalField(typeParams, "unique", _.isBoolean)
    } yield ()
  }

  private def validateIntType(typeParams: JsonNode, tableName: String, columnName: String): Try[Unit] = {
    for {
      _ <- validateOptionalField(typeParams, "lower", _.isInt)
      _ <- validateOptionalField(typeParams, "upper", _.isInt)
      _ <- validateOptionalField(typeParams, "nullable", _.isBoolean)
      _ <- validateOptionalField(typeParams, "unique", _.isBoolean)
      _ <- validateIntBounds(typeParams)
    } yield ()
  }

  private def validateDecimalType(typeParams: JsonNode, tableName: String, columnName: String): Try[Unit] = {
    for {
      _ <- validateRequiredField(typeParams, "precision", node => node.isInt && node.asInt() > 0)
      _ <- validateRequiredField(typeParams, "scale", node => node.isInt && node.asInt() >= 0)
      _ <- validateOptionalField(typeParams, "lower", _.isNumber)
      _ <- validateOptionalField(typeParams, "upper", _.isNumber)
      _ <- validateOptionalField(typeParams, "nullable", _.isBoolean)
      _ <- validateOptionalField(typeParams, "unique", _.isBoolean)
      _ <- validateDecimalBounds(typeParams)
    } yield ()
  }

  private def validateCategoricalType(typeParams: JsonNode, tableName: String, columnName: String): Try[Unit] = {
    for {
      _ <- validateRequiredField(typeParams, "values", node => node.isArray && node.size() > 0)
      _ <- validateOptionalField(typeParams, "nullable", _.isBoolean)
    } yield ()
  }

  private def validateDatetimeType(typeParams: JsonNode, tableName: String, columnName: String): Try[Unit] = {
    Success(())
  }

  private def validateForeignKeyType(typeParamsNode: JsonNode, tableName: String, columnName: String, foreignKeys: collection.mutable.ListBuffer[ForeignKeyInfo], tables: collection.mutable.ListBuffer[TableInfo]): Try[Unit] = {
    for {
      _ <- validateRequiredField(typeParamsNode, "table", _.isTextual)
      toTable = typeParamsNode.get("table").asText()
      _ <- validateRequiredField(typeParamsNode, "column", _.isTextual)
      toColumn = typeParamsNode.get("column").asText()
      _ <- validateRequiredField(typeParamsNode, "Onto", _.isBoolean)
      isOnto = typeParamsNode.get("Onto").asBoolean()
      _ <- validateRequiredField(typeParamsNode, "Unique", _.isBoolean)
      isUnique = typeParamsNode.get("Unique").asBoolean()
      _ <- if (isOnto && !isUnique) {
        Failure(new IllegalArgumentException(s"Onto foreign key must also be unique for column $columnName in table $tableName"))
      } else Success(())
      _ = foreignKeys += ForeignKeyInfo(tableName, columnName, toTable, toColumn, isUnique, isOnto)
    } yield ()
  }

  private def validateRequiredField(node: JsonNode, fieldName: String, validator: JsonNode => Boolean): Try[Unit] = {
    if (node.has(fieldName) && validator(node.get(fieldName))) {
      Success(())
    } else {
      Failure(new IllegalArgumentException(s"Missing or invalid required field: $fieldName"))
    }
  }

  private def validateOptionalField(node: JsonNode, fieldName: String, validator: JsonNode => Boolean): Try[Unit] = {
    if (!node.has(fieldName) || validator(node.get(fieldName))) {
      Success(())
    } else {
      Failure(new IllegalArgumentException(s"Invalid optional field: $fieldName"))
    }
  }

  private def validateIntBounds(typeParams: JsonNode): Try[Unit] = {
    if (typeParams.has("lower") && typeParams.has("upper")) {
      val lower = typeParams.get("lower").asInt()
      val upper = typeParams.get("upper").asInt()
      if (lower <= upper) {
        Success(())
      } else {
        Failure(new IllegalArgumentException("Lower bound must be less than or equal to upper bound"))
      }
    } else {
      Success(())
    }
  }

  private def validateDecimalBounds(typeParams: JsonNode): Try[Unit] = {
    if (typeParams.has("lower") && typeParams.has("upper")) {
      val lower = typeParams.get("lower").asDouble()
      val upper = typeParams.get("upper").asDouble()
      if (lower <= upper) {
        Success(())
      } else {
        Failure(new IllegalArgumentException("Lower bound must be less than or equal to upper bound"))
      }
    } else {
      Success(())
    }
  }

  private def validateForeignKeyGraph(foreignKeys: List[ForeignKeyInfo], tables: List[TableInfo]): Try[Unit] = {
    // Create the graph representation of the foreign key dependencies at the table level
    val graph = foreignKeys.foldLeft(Map.empty[String, Set[String]]) { (acc, fk) =>
      val key = fk.fromTable
      val value = fk.toTable
      acc + (key -> (acc.getOrElse(key, Set.empty) + value))
    }

    // Helper function to detect cycles at the table level
    def hasCycle(node: String, visited: Set[String], stack: Set[String]): Boolean = {
      if (stack.contains(node)) {
        // Node is already in the recursion stack, indicating a cycle
        true
      } else if (visited.contains(node)) {
        // Node has already been visited, no need to visit again
        false
      } else {
        // Mark the node as visited and add it to the recursion stack
        val newVisited = visited + node
        val newStack = stack + node
        // Recursively visit all neighbors at the table level
        graph.getOrElse(node, Set.empty).exists(neighbor => hasCycle(neighbor, newVisited, newStack))
      }
    }

    val foreignKeyValidation = foreignKeys.foldLeft[Try[Unit]](Success(())) { (accTry, fk) =>
      accTry.flatMap(_ => validateForeignKeyReference(fk.fromTable, fk.fromColumn, fk.toTable, fk.toColumn, fk.isUnique, fk.isOnto, foreignKeys, tables))
    }
    if (foreignKeyValidation.isFailure) {
      throw foreignKeyValidation.failed.get
    }

    // Check if any table in the graph is part of a cycle
    if (graph.keys.exists(node => hasCycle(node, Set.empty, Set.empty))) {
      Failure(new IllegalArgumentException("Foreign key graph contains cycles at the table level"))
    } else {
      Success(())
    }
  }

  private def validateForeignKeyReference(fromTable: String, fromColumn: String, toTable: String, toColumn: String, isUnique: Boolean, isOnto: Boolean, foreignKeys: List[ForeignKeyInfo], tables: List[TableInfo]): Try[Unit] = {
    tables.find(_.name == toTable) match {
      case Some(targetTable) =>
        if (!targetTable.primaryKey.contains(toColumn)) {
          Failure(new IllegalArgumentException(s"Foreign key must reference a primary key column, but $toColumn is not part of the primary key in table $toTable"))
        } else {
          Success(())
        }
      case None =>
        Failure(new IllegalArgumentException(s"Foreign key reference to non-existent table $toTable"))
    }
  }

  private def validateDatabase(dbNode: JsonNode, foreignKeys: collection.mutable.ListBuffer[ForeignKeyInfo], tables: collection.mutable.ListBuffer[TableInfo]): Try[Unit] = {
    for {
      _ <- validateRequiredField(dbNode, "tables", _.isArray)
      _ <- validateTables(dbNode.get("tables"), foreignKeys, tables)
    } yield ()
  }

  /**
   * Runs the validation.
   *
   * @param args Command line arguments. Expects path to configuration file as first argument.
   */
  def main(args: Array[String]): Unit = {
    val configPath = if (args.length > 0) args(0) else Constants.tpchConfigPath
    val config = schemaMapper.yamlToConfig(configPath)
    val filePath = config.schema_path

    validateSchemaFile(filePath) match {
      case Success(_) => println("Schema passed validation.")
      case Failure(error) => println(s"Schema validation failed: ${error.getMessage}")
    }
  }
}