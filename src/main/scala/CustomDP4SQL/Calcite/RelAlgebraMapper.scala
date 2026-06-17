package CustomDP4SQL.Calcite

import CustomDP4SQL.DataModel.Config.Config
import CustomDP4SQL.DataModel.Schema.{Attribute, Schema}
import org.apache.calcite.config.Lex
import org.apache.calcite.plan.RelOptUtil
import org.apache.calcite.plan.hep.{HepPlanner, HepProgramBuilder}
import org.apache.calcite.rel.core.{Filter, Join, Project, TableScan}
import org.apache.calcite.rel.logical.LogicalAggregate
import org.apache.calcite.rel.rel2sql.RelToSqlConverter
import org.apache.calcite.rel.rules.{FilterJoinRule, JoinPushTransitivePredicatesRule}
import org.apache.calcite.rel.{RelNode, RelRoot}
import org.apache.calcite.rex.{RexCall, RexInputRef, RexNode}
import org.apache.calcite.schema.SchemaPlus
import org.apache.calcite.sql.SqlNode
import org.apache.calcite.sql.dialect.{CalciteSqlDialect, HiveSqlDialect, MysqlSqlDialect, PostgresqlSqlDialect}
import org.apache.calcite.sql.parser.SqlParser
import org.apache.calcite.sql.pretty.SqlPrettyWriter
import org.apache.calcite.tools.{FrameworkConfig, Frameworks, Planner}

import scala.jdk.CollectionConverters.*

object RelAlgebraMapper {
  /**
   * Creates a Calcite schema from the given schema definition.
   *
   * @param schema Schema object containing tables and columns.
   * @return SchemaPlus object representing the Calcite schema.
   */
  def createCalciteSchema(schema: Schema, config: Config): SchemaPlus = {
    val rootSchema = Frameworks.createRootSchema(true)

    // Create all tables with their original column definitions
    val tableMap = schema.relations.map { table =>
      table.name -> new CalciteTable(table.attributes, config)
    }.toMap

    // Add all tables to the schema
    tableMap.foreach { case (tableName, calciteTable) =>
      rootSchema.add(tableName, calciteTable)
    }

    // Resolve foreign key references
    schema.relations.foreach { table =>
      val calciteTable = tableMap(table.name)
      val updatedColumns = table.attributes.map { column =>
        if (column.datatype.name.toLowerCase == "foreign_key") {
          val foreignKeyParams = column.datatype
          val referencedTable = foreignKeyParams.to_relation.get
          val referencedColumn = foreignKeyParams.to_attribute.get

          // Find the referenced column's type
          val referencedTableColumns = schema.relations.find(_.name == referencedTable).get.attributes
          val referencedColumnDef = referencedTableColumns.find(_.name == referencedColumn).get

          // Create a new column with the same type as the referenced column
          Attribute(column.name, referencedColumnDef.datatype)
        } else {
          column
        }
      }
      calciteTable.updateAttributes(updatedColumns)
    }

    rootSchema
  }

  /**
   * Converts a SQL query to a RelNode (Relational Algebra representation).
   *
   * @param sql        SQL query to convert
   * @param rootSchema Calcite schema to use for parsing and validation
   * @return RelNode representing the Relational Algebra of the input SQL
   */
  def sqlToRelNode(sql: String, rootSchema: SchemaPlus): RelNode = {
    val parserConfig = SqlParser.configBuilder()
      .setLex(Lex.MYSQL)
      .build()

    val config: FrameworkConfig = Frameworks.newConfigBuilder()
      .parserConfig(parserConfig)
      .defaultSchema(rootSchema)
      .build()

    val planner: Planner = Frameworks.getPlanner(config)
    val sqlNode: SqlNode = planner.parse(sql)
    val validatedSqlNode: SqlNode = planner.validate(sqlNode)
    val relRoot: RelRoot = planner.rel(validatedSqlNode)

    relRoot.rel
  }

  /**
   * Converts a RelNode (Relational Algebra representation) back to SQL for a specific dialect.
   *
   * @param rel     RelNode to convert
   * @param dialect Target SQL dialect (e.g., "mysql", "postgresql", "hive")
   * @return String representation of the SQL in the specified dialect
   */
  def relNodeToSql(rel: RelNode, dialect: String, config: Config): String = {
    val sqlDialect = config.sql_dialects.get(dialect.toLowerCase) match {
      case Some("MysqlSqlDialect.DEFAULT") => MysqlSqlDialect.DEFAULT
      case Some("PostgresqlSqlDialect.DEFAULT") => PostgresqlSqlDialect.DEFAULT
      case Some("HiveSqlDialect.DEFAULT") => HiveSqlDialect.DEFAULT
      case _ => CalciteSqlDialect.DEFAULT
    }

    val relToSqlConverter = new RelToSqlConverter(sqlDialect)
    val sqlNode: SqlNode = relToSqlConverter.visitChild(0, rel).asStatement()

    val writer = new SqlPrettyWriter(sqlDialect)
    writer.format(sqlNode)
  }

  /**
   * Replace placeholders (e.g., $0, $1) with actual field names from the rowType.
   *
   * @param expr       Expression node with fields to replace.
   * @param fieldNames List of field names.
   * @return
   */
  private def replacePlaceholdersWithFieldNames(expr: RexNode, fieldNames: List[String]): String = {
    expr match {
      // If it's a direct reference to a field, replace $n with the corresponding field name
      case inputRef: RexInputRef =>
        val index = inputRef.getIndex
        if (index < fieldNames.length) fieldNames(index) else s"$$$index"

      // For complex expressions, just return the string representation (no replacement needed)
      case call: RexCall =>
        val operator = call.getOperator.toString
        val operands = call.getOperands.asScala.map(operand => replacePlaceholdersWithFieldNames(operand, fieldNames)).mkString(", ")
        s"$operator($operands)"

      case otherExpr => otherExpr.toString
    }
  }

  /**
   * Print node details and replace placeholders with actual field names.
   *
   * @param rel         Rel node to process.
   * @param indentLevel Indent level for pretty printing.
   * @return
   */
  private def processNodeWithPlaceholders(rel: RelNode, indentLevel: Int): String = {
    val indent = "  " * indentLevel

    // Get the field names from the rowType of the current node
    val fieldNames = rel.getRowType.getFieldNames.asScala.toList

    rel match {
      // For LogicalProject nodes, replace placeholders in projections
      case project: Project =>
        val projectFields = project.getRowType.getFieldNames.asScala.mkString(", ")
        // val projectExpressions = project.getProjects.asScala.toList
        // val replacedPlaceholders = extractAndReplacePlaceholders(projectExpressions, fieldNames).mkString(", ")
        s"${indent}Project(attributes=[$projectFields])"

      // For LogicalJoin nodes, replace placeholders in the join condition
      case join: Join =>
        // val condition = join.getCondition.toString
        val replacedCondition = replacePlaceholdersWithFieldNames(join.getCondition, fieldNames)
        val joinType = join.getJoinType.toString
        s"${indent}Join(condition=[$replacedCondition], joinType=[$joinType])"

      // For LogicalFilter nodes, replace placeholders in the filter condition
      case filter: Filter =>
        // val condition = filter.getCondition.toString
        val replacedCondition = replacePlaceholdersWithFieldNames(filter.getCondition, fieldNames)
        s"${indent}Select(predicate=[$replacedCondition])"

      // For LogicalAggregate nodes, replace grouping placeholders
      case aggregate: LogicalAggregate =>
        val groupSet = aggregate.getGroupSet.asList().asScala.map(i => if (i < fieldNames.length) fieldNames(i) else s"$$$i").mkString(", ")
        val aggCalls = aggregate.getAggCallList.asScala.map(_.toString).mkString(", ")
        s"${indent}GroupBy(groupSet=[$groupSet], aggregates=[$aggCalls])"

      // For EnumerableTableScan nodes, print the table being scanned
      case scan: TableScan =>
        val tableName = scan.getTable.getQualifiedName.asScala.mkString(".")
        s"${indent}Table(name=$tableName)"

      // For other node types, just print the node type
      case _ =>
        s"${indent}${rel.getRelTypeName}"
    }
  }

  /**
   * Recursive function to process the relational algebra tree and return the full string.
   *
   * @param rel         Current node to process.
   * @param indentLevel Indent level for pretty printing.
   * @return
   */
  def processRelTreeWithPlaceholders(rel: RelNode, indentLevel: Int = 0): String = {
    // Process the current node and get the string representation
    val nodeString = processNodeWithPlaceholders(rel, indentLevel)

    // Recursively process and return the inputs (children) of the current node
    val inputStrings = rel.getInputs.asScala.map { input =>
      processRelTreeWithPlaceholders(input, indentLevel + 1)
    }.mkString("\n")

    // Combine the current node string with its inputs (children)
    if (inputStrings.nonEmpty) s"$nodeString\n$inputStrings" else nodeString
  }
}
