package CustomDP4SQL.Common

import CustomDP4SQL.DataModel.Config.Config
import CustomDP4SQL.DataModel.PrivacyModel.SchemaPrivacyModel
import CustomDP4SQL.DataModel.Schema.Schema
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.scala.DefaultScalaModule

import java.io.FileInputStream

/**
 * Maps YAML files into Scala objects.
 */
class SchemaMapper {
  /**
   * Loads config from a YAML file.
   *
   * @param filePath Path to the YAML file containing config.
   * @return Config object representing the loaded config.
   */
  def yamlToConfig(filePath: String): Config = {
    val mapper = new ObjectMapper(new YAMLFactory())
    mapper.registerModule(DefaultScalaModule)
    mapper.readValue(new FileInputStream(filePath), classOf[Config])
  }

  /**
   * Loads schema definition from a YAML file.
   *
   * @param filePath Path to the YAML file containing schema definition.
   * @return SchemaDefinition object representing the loaded schema.
   */
  def yamlToSchema(filePath: String): Schema = {
    val mapper = new ObjectMapper(new YAMLFactory())
    mapper.registerModule(DefaultScalaModule)
    mapper.readValue(new FileInputStream(filePath), classOf[Schema])
  }

  /**
   * Loads schema constraints from a YAML file.
   *
   * @param filePath Path to the YAML file containing schema constraints.
   * @return Map[String, Any] representing the loaded constraints.
   */
  def yamlToPrivacyModel(filePath: String): SchemaPrivacyModel = {
    val mapper = new ObjectMapper(new YAMLFactory())
    mapper.registerModule(DefaultScalaModule)
    mapper.readValue(new FileInputStream(filePath), classOf[SchemaPrivacyModel])
  }
}
