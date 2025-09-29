package CustomDP4SQL.Inference

import CustomDP4SQL.Common.SchemaMapper
import CustomDP4SQL.DataModel.Config.Config
import CustomDP4SQL.DataModel.PrivacyModel.{PrivacyPolicy, SchemaPrivacyModel}
import CustomDP4SQL.PrivacyModel.Action.{AddDelAction, PlausibleDeniabilityAction, PubAction, RepAction}
import CustomDP4SQL.Representation.{Edge, GraphBuilder}
import CustomDP4SQL.Visitor.{ActionVisitor, MaxFrequencyVisitor}
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
    } else if (_referentialIntegrityGraph(relation).isEmpty) {
      0
    } else {
      var sum = 0

      _referentialIntegrityGraph(relation).foreach(edge => {
        val maxFrequency = baseRelationMaxFreqMap(relation)(edge.name)
        sum += getMaximumOwnership(globalEntity, edge.toTable) * maxFrequency
      })

      sum
    }
  }

  private def toPub(table: String): PubAction = {
    _referentialIntegrityGraph(table).foreach(foreignKey => {
      val childAction = baseRelationActionMap(foreignKey.toTable)
      val childNode = privacyModel.getRelationPrivacyModel(foreignKey.toTable).get

      childAction match {
        case addDel: AddDelAction =>
          if (addDel.delete > 0)
          throw Error(s"'$table' cannot have PUB policy when '${foreignKey.toTable}' has action $childAction")
        case rep: RepAction =>
          val childPrimaryKey = schema.relations.find(_.name == foreignKey.toTable).get.identifier
          val childAttributes = childNode.privacy_policy.attributes.get
          
          if (childAttributes.intersect(childPrimaryKey).nonEmpty) {
            throw Error(s"'$table' cannot have PUB policy when '${foreignKey.toTable}' has action $childAction")
          }
        case _ =>
      }
    })

    PubAction()
  }

  private def toAddDel(table: String): AddDelAction = {
    val finalAction = AddDelAction(0, 0)

    _referentialIntegrityGraph(table).foreach(foreignKey => {
      val childAction = baseRelationActionMap(foreignKey.toTable)
      val childNode = privacyModel.getRelationPrivacyModel(foreignKey.toTable).get
      val maxFreq = baseRelationMaxFreqMap(table)(foreignKey.name)

      childAction match {
        case addDel: AddDelAction =>
          finalAction.add += addDel.add * maxFreq
          finalAction.delete += addDel.delete * maxFreq
        case rep: RepAction =>
          val childPrimaryKey = schema.relations.find(_.name == foreignKey.toTable).get.identifier
          val childAttributes = childNode.privacy_policy.attributes.get
          
          if (childAttributes.intersect(childPrimaryKey).nonEmpty) {
            finalAction.add += rep.replace * maxFreq
            finalAction.delete += rep.replace * maxFreq
          }
        case _ =>
      }
    })

    finalAction
  }

  private def toRep(table: String, policyAttributes: Set[String]): RepAction = {
    val finalAction = RepAction(getMaximumOwnership(globalEntity, table), policyAttributes)

    _referentialIntegrityGraph(table).foreach(foreignKey => {
      val childAction = baseRelationActionMap(foreignKey.toTable)
      val childNode = privacyModel.getRelationPrivacyModel(foreignKey.toTable).get
      val maxFreq = baseRelationMaxFreqMap(table)(foreignKey.name)

      childAction match {
        case addDel: AddDelAction =>
          if (addDel.delete > 0 && !policyAttributes.contains(foreignKey.name)) {
            throw Error(s"'$table' cannot have REP policy when '${foreignKey.toTable}' has action $childAction and '${foreignKey.name}' is not in the set of replaced attributes.")
          } else {
            finalAction.replace += addDel.delete * maxFreq
          }
        case rep: RepAction =>
          val childPrimaryKey = schema.relations.find(_.name == foreignKey.toTable).get.identifier
          val childAttributes = childNode.privacy_policy.attributes.get

          if (childAttributes.intersect(childPrimaryKey).nonEmpty) {
            if (!policyAttributes.contains(foreignKey.name)) {
              throw Error(s"'$table' cannot have REP policy when '${foreignKey.toTable}' has action $childAction and '${foreignKey.name}' is not in the set of replaced attributes.")
            } else {
              finalAction.replace += rep.replace * maxFreq
            }
          }
        case _ =>
      }
    })

    finalAction
  }

  private def deriveBaseRelationAction(table: String, policy: PrivacyPolicy, isGlobalEntity: Boolean): Unit = {
    var action: PlausibleDeniabilityAction = PubAction()

    if (isGlobalEntity) {
      globalEntity = table
      action = policy.name match {
        case "PUB" => PubAction()
        case "ADD" => AddDelAction(1, 0)
        case "DEL" => AddDelAction(0, 1)
        case "REP" => RepAction(1, policy.attributes.get)
        case other => throw Error(s"Encountered invalid policy '$other' for global entity table '$table''")
      }
    } else {
      action = policy.name match {
        case "PUB" => toPub(table)
        case "INH" => toAddDel(table)
        case "REP" => toRep(table, policy.attributes.get)
        case other => throw Error(s"Encountered invalid policy '$other' for non-entity table '$table''")
      }
    }

    // Coerce Add(0) x Del(0) and Rep(0) to Pub for subsequent inference
    action = action match {
      case addDelAction: AddDelAction if addDelAction.add == 0 && addDelAction.delete == 0 => PubAction()
      case repAction: RepAction if repAction.replace == 0 => PubAction()
      case _ => action
    }

    baseRelationActionMap += table -> action
  }

  def deriveActionsForTree(node: RelNode): mutable.Map[RelNode, PlausibleDeniabilityAction] = {
    buildBaseRelationMaxFreqMap()
    deriveAllBaseRelationActions()

    val maxFrequencyVisitor = MaxFrequencyVisitor(privacyModel)
    maxFrequencyVisitor.visit(node, 0, null)
    val relNodeMaxFreqMap = maxFrequencyVisitor.relNodeMaxFreqMap

    val actionVisitor = ActionVisitor(baseRelationActionMap, relNodeMaxFreqMap, _dataDependencyGraph)
    actionVisitor.visit(node, 0, null)
    actionVisitor.relNodeActionMap
  }
}
