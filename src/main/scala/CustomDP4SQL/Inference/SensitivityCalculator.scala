package CustomDP4SQL.Inference

import CustomDP4SQL.PrivacyModel.Action.{AddDelAction, PlausibleDeniabilityAction, RepAction}
import org.apache.calcite.rel.RelNode
import org.apache.calcite.rel.core.{Aggregate, Project}
import org.apache.calcite.sql.SqlKind

import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.math.abs

class SensitivityCalculator {
  private val supportedAggregations = Set(SqlKind.COUNT, SqlKind.SUM)

  private def getCountSensitivity(groupingAttrs: Set[String], action: PlausibleDeniabilityAction): Int = {
    action match {
      case action: AddDelAction => abs(action.add + action.delete)
      case action: RepAction =>
        if (action.attributes.intersect(groupingAttrs).isEmpty) {
          0
        } else {
          2 * action.replace
        }
      case _ => 0
    }
  }

  private def getSumSensitivity(groupingAttrs: Set[String], action: PlausibleDeniabilityAction): Int = {
    0
  }

  def computeSensitivity(rootNode: RelNode, action: PlausibleDeniabilityAction): List[Int] = {
    // Assumption: Root node is a GROUP BY operation
    var root = rootNode
    if (rootNode.isInstanceOf[Project]) {
      root = rootNode.getInput(0)
    }
    if (!root.isInstanceOf[Aggregate]) {
      throw Exception("Expected root node of expression to be an aggregation.")
    }

    val aggregate = root.asInstanceOf[Aggregate]
    val aggregationFunctions = aggregate.getAggCallList.asScala.toList

    // Ensure all aggregation functions are supported
    aggregationFunctions.foreach(func => {
      val funcKind = func.getAggregation.kind
      if (!supportedAggregations.contains(funcKind)) {
        throw Exception(s"Aggregation [${funcKind}] is not supported.")
      }
    })

    // Based on the action, compute sensitivity for each aggregation
    aggregationFunctions.map(func => {
      val funcKind = func.getAggregation.kind
      val groupingAttrs = aggregate.getGroupSet.asSet()
      funcKind match {
        case SqlKind.COUNT => getCountSensitivity(Set.empty, action)
        case SqlKind.SUM => getSumSensitivity(Set.empty, action)
        case otherKind => throw Exception(s"Aggregation [${otherKind}] is not supported.")
      }
    })
  }
}
