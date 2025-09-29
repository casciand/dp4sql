import CustomDP4SQL.Calcite.RelAlgebraMapper.*
import org.apache.calcite.plan.RelOptUtil
import org.apache.calcite.rel.RelNode
import org.apache.calcite.schema.SchemaPlus
import org.junit.Assert.*
import org.junit.{Before, Test}

import java.io.{File, PrintWriter}


class RelAlgebraMapperTest {

  var config: Config = _
  var schemaDefinition: SchemaDefinition = _
  var rootSchema: SchemaPlus = _

  @Before
  def setUp(): Unit = {
    // Create a temporary config file
    val configFile = File.createTempFile("test-config", ".yaml")
    configFile.deleteOnExit()
    val configContent =
      """
        |type_mappings:
        |  string: VARCHAR
        |  int: INTEGER
        |  default: VARCHAR
        |sql_dialects:
        |  mysql: MysqlSqlDialect.DEFAULT
        |  postgresql: PostgresqlSqlDialect.DEFAULT
        |  hive: HiveSqlDialect.DEFAULT
        |file_paths:
        |  yaml_schema: test-schema.yaml
        |queries:
        |  default_query: SELECT * FROM users
        |""".stripMargin
    new PrintWriter(configFile) { write(configContent); close() }

    // Create a temporary schema file
    val schemaFile = new File("test-schema.yaml")
    schemaFile.deleteOnExit()
    val schemaContent =
      """
        |tables:
        |  - table: users
        |    primary_key: [id]
        |    columns:
        |      - name: id
        |        type: int
        |        type_params:
        |          lower: 1
        |          unique: true
        |      - name: username
        |        type: string
        |        type_params:
        |          max_length: 50
        |  - table: posts
        |    primary_key: [id]
        |    columns:
        |      - name: id
        |        type: int
        |        type_params:
        |          lower: 1
        |          unique: true
        |      - name: user_id
        |        type: foreign key
        |        type_params:
        |          table: users
        |          column: id
        |          Onto: false
        |          Unique: false
        |""".stripMargin
    new PrintWriter(schemaFile) { write(schemaContent); close() }

    config = loadConfig(configFile.getPath)
    schemaDefinition = loadSchema(schemaFile.getPath)
    rootSchema = createCalciteSchema(schemaDefinition, config)
  }

  @Test
  def testLoadConfig(): Unit = {
    assertNotNull(config)
    assertEquals("VARCHAR", config.type_mappings("string"))
    assertEquals("INTEGER", config.type_mappings("int"))
    assertEquals("MysqlSqlDialect.DEFAULT", config.sql_dialects("mysql"))
  }

  @Test
  def testLoadYamlSchema(): Unit = {
    assertNotNull(schemaDefinition)
    assertEquals(2, schemaDefinition.tables.size)
    val usersTable = schemaDefinition.tables.find(_.table == "users").get
    assertEquals(2, usersTable.columns.size)
    assertEquals("id", usersTable.columns.head.name)
  }

  @Test
  def testCreateCalciteSchema(): Unit = {
    assertNotNull(rootSchema)
    assertTrue(rootSchema.getTableNames.contains("users"))
    assertTrue(rootSchema.getTableNames.contains("posts"))
  }

  @Test
  def testConvertToRelAlgebra(): Unit = {
    val sql = "SELECT * FROM users"
    val rel: RelNode = sqlToRelNode(sql, rootSchema)
    assertNotNull(rel)
    val relString = RelOptUtil.toString(rel)
    assertTrue(relString.contains("EnumerableTableScan(table=[[users]])"))
  }

  @Test
  def testRelToSql(): Unit = {
    val sql = "SELECT * FROM users"
    val rel: RelNode = sqlToRelNode(sql, rootSchema)
    val mysqlSql = relNodeToSql(rel, "mysql", config)
    assertTrue(mysqlSql.contains("SELECT *"))
    assertTrue(mysqlSql.contains("FROM `users`"))

    val hiveSql = relNodeToSql(rel, "hive", config)
    assertTrue(hiveSql.contains("SELECT *"))
    assertTrue(hiveSql.contains("FROM users"))
  }
  @Test
  def testMaxFreqK_FLEX_TableScan(): Unit = {
    // users table: id (int, unique), username (string)
    val usersTable = schemaDefinition.tables.find(_.table == "users").get
    // id column index = 0
    val sql = "SELECT id FROM users"
    val rel: RelNode = sqlToRelNode(sql, rootSchema)
    // Should return 1 + k for unique column (since type_params.unique = true, but no max_frequency)
    val freq = maxFreqK_FLEX(rel, 0, 2, schemaDefinition)
    assertEquals(3, freq)
  }

  @Test
  def testMaxFreqK_FLEX_Project(): Unit = {
    val sql = "SELECT username FROM users"
    val rel: RelNode = sqlToRelNode(sql, rootSchema)
    // Project node, should delegate to TableScan
    val freq = maxFreqK_FLEX(rel, 0, 1, schemaDefinition)
    assertEquals(2, freq)
  }

  @Test
  def testMaxFreqK_FLEX_Filter(): Unit = {
    val sql = "SELECT id FROM users WHERE id > 1"
    val rel: RelNode = sqlToRelNode(sql, rootSchema)
    // Filter node, should delegate to TableScan
    val freq = maxFreqK_FLEX(rel, 0, 0, schemaDefinition)
    assertEquals(1, freq)
  }

  @Test
  def testMaxFreqK_FLEX_Join(): Unit = {
    val sql =
      """
        |SELECT users.id, posts.id
        |FROM users
        |JOIN posts ON users.id = posts.user_id
        |""".stripMargin
    val rel: RelNode = sqlToRelNode(sql, rootSchema)
    // users.id is index 0, posts.id is index 2 (after join)
    val freqUsersId = maxFreqK_FLEX(rel, 0, 0, schemaDefinition)
    val freqPostsId = maxFreqK_FLEX(rel, 2, 0, schemaDefinition)
    // Should be >= 1 (since no max_frequency in schema, default to 1)
    assertTrue(freqUsersId >= 1)
    assertTrue(freqPostsId >= 1)
  }

  @Test
  def testMaxFreqK_FLEX_Aggregate(): Unit = {
    val sql = "SELECT COUNT(*) FROM users GROUP BY id"
    val rel: RelNode = sqlToRelNode(sql, rootSchema)
    // Aggregate node, should return -1 (undefined)
    val freq = maxFreqK_FLEX(rel, 0, 0, schemaDefinition)
    assertEquals(-1, freq)
  }

}
