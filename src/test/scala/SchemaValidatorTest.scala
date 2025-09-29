import CustomDP4SQL.Validation.SchemaValidator
import org.junit.Assert.*
import org.junit.Test

import java.io.File
import scala.util.{Failure, Success}

class SchemaValidatorTest {

  @Test
  def testValidateCorrectSchema(): Unit = {
    val tempFile = File.createTempFile("test-schema", ".yaml")
    tempFile.deleteOnExit()

    val correctSchema =
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
        |          unique: true
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
        |      - name: content
        |        type: string
        |        type_params:
        |          max_length: 1000
        |""".stripMargin

    java.nio.file.Files.write(tempFile.toPath, correctSchema.getBytes)

    SchemaValidator.validateSchemaFile(tempFile.getPath) match {
      case Success(_) => assertTrue(true) // Test passes
      case Failure(e) => fail(s"Validation failed for correct schema: ${e.getMessage}")
    }
  }



  @Test
  def testValidateIncorrectSchema(): Unit = {
    val tempFile = File.createTempFile("test-schema-incorrect", ".yaml")
    tempFile.deleteOnExit()

    val incorrectSchema =
      """
        |tables:
        |  - table: users
        |    primary_key: []  # Empty primary key, which is not allowed
        |    columns:
        |      - name: id
        |        type: int
        |        type_params:
        |          lower: 1
        |          unique: true
        |""".stripMargin

    java.nio.file.Files.write(tempFile.toPath, incorrectSchema.getBytes)

    SchemaValidator.validateSchemaFile(tempFile.getPath) match {
      case Success(_) => fail("Validation should fail for incorrect schema")
      case Failure(_) => assertTrue(true) // Test passes
    }
  }

  @Test
  def testDetectCyclesInForeignKeys(): Unit = {
    val tempFile = File.createTempFile("test-schema-cycle", ".yaml")
    tempFile.deleteOnExit()

    val cycleSchema =
      """
        |tables:
        |  - table: table_a
        |    primary_key: [id]
        |    columns:
        |      - name: id
        |        type: int
        |        type_params:
        |          lower: 1
        |          unique: true
        |      - name: b_id
        |        type: foreign key
        |        type_params:
        |          table: table_b
        |          column: id
        |          Onto: false
        |          Unique: false
        |  - table: table_b
        |    primary_key: [id]
        |    columns:
        |      - name: id
        |        type: int
        |        type_params:
        |          lower: 1
        |          unique: true
        |      - name: a_id
        |        type: foreign key
        |        type_params:
        |          table: table_a
        |          column: id
        |          Onto: false
        |          Unique: false
        |""".stripMargin

    java.nio.file.Files.write(tempFile.toPath, cycleSchema.getBytes)

    SchemaValidator.validateSchemaFile(tempFile.getPath) match {
      case Success(_) => fail("Validation should fail for schema with cycles")
      case Failure(_) => assertTrue(true) // Test passes
    }
  }

  @Test
  def testForeignKeyToNonExistentTableAndColumn(): Unit = {
    val tempFile = File.createTempFile("test-schema-nonexistent-fk", ".yaml")
    tempFile.deleteOnExit()

    val schemaWithNonExistentFK =
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
        |          unique: true
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
        |          table: non_existent_table
        |          column: non_existent_column
        |          Onto: false
        |          Unique: false
        |      - name: content
        |        type: string
        |        type_params:
        |          max_length: 1000
        |""".stripMargin

    java.nio.file.Files.write(tempFile.toPath, schemaWithNonExistentFK.getBytes)

    SchemaValidator.validateSchemaFile(tempFile.getPath) match {
      case Success(_) => fail("Validation should fail for schema with foreign key referencing non-existent table and column")
      case Failure(e) =>
        assertTrue(e.getMessage.contains("non_existent_table") || e.getMessage.contains("non_existent_column"))
    }
  }

  @Test
  def testValidateSchemaWithVariousColumnTypes(): Unit = {
    val tempFile = File.createTempFile("test-schema-various-types", ".yaml")
    tempFile.deleteOnExit()

    val schemaWithVariousTypes =
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
        |          unique: true
        |      - name: created_at
        |        type: timestamp
        |        type_params:
        |          format: "yyyy-MM-dd HH:mm:ss"
        |      - name: is_active
        |        type: boolean
        |""".stripMargin

    java.nio.file.Files.write(tempFile.toPath, schemaWithVariousTypes.getBytes)

    SchemaValidator.validateSchemaFile(tempFile.getPath) match {
      case Success(_) => assertTrue(true) // Test passes
      case Failure(e) => fail(s"Validation failed for schema with various column types: ${e.getMessage}")
    }
  }

  @Test
  def testValidateSchemaWithUnsupportedColumnType(): Unit = {
    val tempFile = File.createTempFile("test-schema-unsupported-type", ".yaml")
    tempFile.deleteOnExit()

    val schemaWithUnsupportedType =
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
        |      - name: unsupported_column
        |        type: unsupported_type
        |""".stripMargin

    java.nio.file.Files.write(tempFile.toPath, schemaWithUnsupportedType.getBytes)

    SchemaValidator.validateSchemaFile(tempFile.getPath) match {
      case Success(_) => fail("Validation should fail for schema with unsupported column type")
      case Failure(_) => assertTrue(true) // Test passes
    }
  }

  @Test
  def testValidateSchemaWithComplexTypeParams(): Unit = {
    val tempFile = File.createTempFile("test-schema-complex-params", ".yaml")
    tempFile.deleteOnExit()

    val schemaWithComplexParams =
      """
        |tables:
        |  - table: users
        |    primary_key: [id]
        |    columns:
        |      - name: id
        |        type: int
        |        type_params:
        |          lower: 1
        |          upper: 1000
        |          unique: true
        |      - name: username
        |        type: string
        |        type_params:
        |          max_length: 50
        |          min_length: 3
        |          unique: true
        |      - name: email
        |        type: string
        |        type_params:
        |          pattern: "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$"
        |""".stripMargin

    java.nio.file.Files.write(tempFile.toPath, schemaWithComplexParams.getBytes)

    SchemaValidator.validateSchemaFile(tempFile.getPath) match {
      case Success(_) => assertTrue(true) // Test passes
      case Failure(e) => fail(s"Validation failed for schema with complex type parameters: ${e.getMessage}")
    }
  }
}
  def testValidateIncorrectSchema(): Unit = {
    val tempFile = File.createTempFile("test-schema-incorrect", ".yaml")
    tempFile.deleteOnExit()

    val incorrectSchema =
      """
        |tables:
        |  - table: users
        |    primary_key: []  # Empty primary key, which is not allowed
        |    columns:
        |      - name: id
        |        type: int
        |        type_params:
        |          lower: 1
        |          unique: true
        |""".stripMargin

    java.nio.file.Files.write(tempFile.toPath, incorrectSchema.getBytes)

    SchemaValidator.validateSchemaFile(tempFile.getPath) match {
      case Success(_) => fail("Validation should fail for incorrect schema")
      case Failure(_) => assertTrue(true) // Test passes
    }
  }

  @Test
  def testDetectCyclesInForeignKeys(): Unit = {
    val tempFile = File.createTempFile("test-schema-cycle", ".yaml")
    tempFile.deleteOnExit()

    val cycleSchema =
      """
        |tables:
        |  - table: table_a
        |    primary_key: [id]
        |    columns:
        |      - name: id
        |        type: int
        |        type_params:
        |          lower: 1
        |          unique: true
        |      - name: b_id
        |        type: foreign key
        |        type_params:
        |          table: table_b
        |          column: id
        |          Onto: false
        |          Unique: false
        |  - table: table_b
        |    primary_key: [id]
        |    columns:
        |      - name: id
        |        type: int
        |        type_params:
        |          lower: 1
        |          unique: true
        |      - name: a_id
        |        type: foreign key
        |        type_params:
        |          table: table_a
        |          column: id
        |          Onto: false
        |          Unique: false
        |""".stripMargin

    java.nio.file.Files.write(tempFile.toPath, cycleSchema.getBytes)

    SchemaValidator.validateSchemaFile(tempFile.getPath) match {
      case Success(_) => fail("Validation should fail for schema with cycles")
      case Failure(_) => assertTrue(true) // Test passes
    }
  }

  @Test
  def testForeignKeyToNonExistentTableAndColumn(): Unit = {
    val tempFile = File.createTempFile("test-schema-nonexistent-fk", ".yaml")
    tempFile.deleteOnExit()

    val schemaWithNonExistentFK =
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
        |          unique: true
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
        |          table: non_existent_table
        |          column: non_existent_column
        |          Onto: false
        |          Unique: false
        |      - name: content
        |        type: string
        |        type_params:
        |          max_length: 1000
        |""".stripMargin

    java.nio.file.Files.write(tempFile.toPath, schemaWithNonExistentFK.getBytes)

    SchemaValidator.validateSchemaFile(tempFile.getPath) match {
      case Success(_) => fail("Validation should fail for schema with foreign key referencing non-existent table and column")
      case Failure(e) =>
        assertTrue(e.getMessage.contains("non_existent_table") || e.getMessage.contains("non_existent_column"))
    }
}
// type check 