package CustomDP4SQL.Representation

/**
 * Edge representation for Data Dependency and Referential Integrity graphs.
 */
case class Edge(name: String, fromTable: String, toTable: String)
