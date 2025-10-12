package CustomDP4SQL.Representation

import CustomDP4SQL.DataModel.Schema.Schema

import scala.collection.mutable

/**
 * Builds the Data Dependency and Referential Integrity graphs for the passed schema.
 */
class GraphBuilder {
  private val _dataDependencyGraph = mutable.Map[String, Set[Edge]]()
  private val _referentialIntegrityGraph = mutable.Map[String, Set[Edge]]()
  private val _refConstraints: mutable.Set[RefConstr] = mutable.Set[RefConstr]()
  private val _indegreeMap = mutable.Map[String, Int]()

  def dataDependencyGraph: mutable.Map[String, Set[Edge]] = _dataDependencyGraph

  def referentialIntegrityGraph: mutable.Map[String, Set[Edge]] = _referentialIntegrityGraph
  
  def refConstraints: mutable.Set[RefConstr] = _refConstraints
  
  def indegreeMap: mutable.Map[String, Int] = _indegreeMap
  
  def buildGraphs(schema: Schema): Unit = {
    _dataDependencyGraph.clear()
    _referentialIntegrityGraph.clear()
    _indegreeMap.clear()
    
    val tableNames = Set.from(schema.relations.map(table => table.name))
    tableNames.foreach(name => {
      _dataDependencyGraph += name -> Set[Edge]()
      _referentialIntegrityGraph += name -> Set[Edge]()
    })

    schema.relations.foreach(table => {
      _indegreeMap += (table.name -> 0)
      table.attributes.foreach(col =>
      if (col.datatype.name.toLowerCase.equals("foreign_key")) {
        val toRelation = col.datatype.to_relation.get
        _dataDependencyGraph(toRelation) += Edge(col.name, toRelation, table.name)
        _referentialIntegrityGraph(table.name) += Edge(col.name, table.name, toRelation)
        _refConstraints.add(RefConstr(col.name, col.datatype.to_attribute.get))
        _indegreeMap(table.name) += 1
        }
      )
    })
  }
}
