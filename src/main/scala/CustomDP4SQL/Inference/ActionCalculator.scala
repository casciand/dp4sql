package CustomDP4SQL.Inference

import CustomDP4SQL.Common.SchemaMapper
import CustomDP4SQL.DataModel.Config.Config
import CustomDP4SQL.DataModel.PrivacyModel.{PrivacyPolicy, SchemaPrivacyModel}
import CustomDP4SQL.PrivacyModel.Action.{AddDelAction, PlausibleDeniabilityAction, PubAction, RepAction}
import CustomDP4SQL.Representation.{Edge, GraphBuilder, RefConstr}
import CustomDP4SQL.Visitor.{ActionVisitor, MaxFrequencyVisitor, PrivateSQLVisitor}
import org.apache.calcite.rel.RelNode

import scala.collection.mutable

class ActionCalculator(config: Config, privacyModel: SchemaPrivacyModel) {
  private val schemaMapper: SchemaMapper = SchemaMapper()
  private val schema = schemaMapper.yamlToSchema(config.schema_path)
  
  private val baseRelationMaxFreqMap: mutable.Map[String, mutable.Map[String, Int]] = mutable.Map[String, mutable.Map[String, Int]]()
  private val baseRelationActionMap: mutable.Map[String, PlausibleDeniabilityAction] = mutable.Map[String, PlausibleDeniabilityAction]()
  private var indegreeMap = mutable.Map[String, Int]()
  private var globalEntity = ""

  var _dataDependencyGraph: mutable.Map[String, Set[Edge]] = mutable.Map[String, Set[Edge]]()
  var _referentialIntegrityGraph: mutable.Map[String, Set[Edge]] = mutable.Map[String, Set[Edge]]()
  var _refConstraints: mutable.Set[RefConstr] = mutable.Set[RefConstr]()


  private def buildBaseRelationMaxFreqMap(): Unit = {
    schema.relations.foreach(relation => {
      baseRelationMaxFreqMap += relation.name -> mutable.Map[String, Int]()
      relation.attributes.foreach(schemaCol => {
        val maxFreq = privacyModel.getRelationPrivacyModel(relation.name).get.getAttributePrivacyModel(schemaCol.name).get.max_frequency
        baseRelationMaxFreqMap(relation.name) += (schemaCol.name.toLowerCase -> maxFreq)
      })
    })
  }

  private def deriveAllBaseRelationActions(): Unit = {
    val graphBuilder = GraphBuilder()
    graphBuilder.buildGraphs(schema)

    _dataDependencyGraph = graphBuilder.dataDependencyGraph
    _referentialIntegrityGraph = graphBuilder.referentialIntegrityGraph
    _refConstraints = graphBuilder.refConstraints
    indegreeMap = graphBuilder.indegreeMap

    // Topological traversal of data dependency graph
    val queue = mutable.Queue[String]()

    // Enqueue base relations with no foreign keys
    _dataDependencyGraph.keys.foreach(table => {
      if (indegreeMap(table) == 0) {
        queue.enqueue(table)
      }
    })

    // Process queue
    while (queue.nonEmpty) {
      val table = queue.dequeue()
      val calciteTable = privacyModel.getRelationPrivacyModel(table).get
      val policy = calciteTable.privacy_policy
      val isGlobalEntity = calciteTable.global_entity

      // Derive action
      deriveBaseRelationAction(table, policy, isGlobalEntity)

      // Remove node and update indegrees
      _dataDependencyGraph(table).foreach(foreignKey => {
        indegreeMap(foreignKey.toTable) -= 1
        if (indegreeMap(foreignKey.toTable) == 0) {
          queue.enqueue(foreignKey.toTable)
        }
      })
    }
  }

  private def getMaximumOwnership(globalEntity: String, relation: String): Int = {
    if (relation == globalEntity) {
      1
    } else {
      var sum = 0

      _referentialIntegrityGraph(relation).foreach(edge => {
        val maxFrequency = baseRelationMaxFreqMap(relation)(edge.name)
        sum += getMaximumOwnership(globalEntity, edge.toTable) * maxFrequency
      })

      sum
    }
  }

  private def deriveBaseRelationAction(table: String, policy: PrivacyPolicy, isGlobalEntity: Boolean): Unit = {
    var action: PlausibleDeniabilityAction = PubAction()

    if (isGlobalEntity) {
      globalEntity = table
      action = policy.name match {
        case "DEL" => AddDelAction(0, 1)
        case "REP" => RepAction(1, policy.attributes.get)
        case "PUB" => RepAction(0, Set.empty)
        case other => throw Error(s"Encountered invalid policy '$other' for global entity table '$table''")
      }
    } else {
      action = policy.name match {
        case "DEL" => AddDelAction(0, getMaximumOwnership(globalEntity, table))
        case "REP" => RepAction(getMaximumOwnership(globalEntity, table), policy.attributes.get)
        case "PUB" => RepAction(0, Set.empty)
        case other => throw Error(s"Encountered invalid policy '$other' for non-entity table '$table''")
      }
    }

    // Coerce Add(0) x Del(0) and Rep(0) to Pub for subsequent inference
//    action = action match {
//      case addDelAction: AddDelAction if addDelAction.add == 0 && addDelAction.delete == 0 => PubAction()
//      case repAction: RepAction if repAction.replace == 0 => PubAction()
//      case _ => action
//    }

    baseRelationActionMap += table -> action
  }

  def deriveActionsForTree(node: RelNode, usePrivateSql: Boolean = false): mutable.Map[RelNode, PlausibleDeniabilityAction] = {
    buildBaseRelationMaxFreqMap()
    deriveAllBaseRelationActions()

    val maxFrequencyVisitor = MaxFrequencyVisitor(privacyModel)
    maxFrequencyVisitor.visit(node, 0, null)
    val relNodeMaxFreqMap = maxFrequencyVisitor.relNodeMaxFreqMap

    if (usePrivateSql) {
      val actionVisitor = PrivateSQLVisitor(baseRelationActionMap, relNodeMaxFreqMap, _dataDependencyGraph, _refConstraints)
      actionVisitor.visit(node, 0, null)
      actionVisitor.relNodeActionMap
    } else {
      val actionVisitor = ActionVisitor(baseRelationActionMap, relNodeMaxFreqMap, _dataDependencyGraph, _refConstraints)
      actionVisitor.visit(node, 0, null)
      actionVisitor.relNodeActionMap
    }
  }
}
