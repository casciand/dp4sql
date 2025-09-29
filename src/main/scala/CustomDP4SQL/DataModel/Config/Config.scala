package CustomDP4SQL.DataModel.Config

case class Config(type_mappings: Map[String, String], sql_dialects: Map[String, String], schema_path: String, privacy_models: Map[String, String], queries: Map[String, String])
