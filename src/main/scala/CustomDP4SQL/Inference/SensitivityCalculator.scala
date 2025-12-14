package CustomDP4SQL.Inference

import CustomDP4SQL.Common.SchemaMapper
import CustomDP4SQL.DataModel.Config.Config
import CustomDP4SQL.DataModel.Schema.Schema
import CustomDP4SQL.PrivacyModel.Action.{AddDelAction, PlausibleDeniabilityAction, RepAction}
import org.apache.calcite.rel.RelNode
import org.apache.calcite.rel.core.{Aggregate, Project}
import org.apache.calcite.rel.logical.LogicalSort
import org.apache.calcite.sql.SqlKind

import scala.collection.mutable
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.math.{max, abs}

class SensitivityCalculator(schema: Schema) {
  private def getCountSensitivity(groupingAttrs: Set[String], action: PlausibleDeniabilityAction): Int = {
    action match {
      case action: AddDelAction => max(action.add, action.delete)
      case action: RepAction =>
        if (action.attributes.intersect(groupingAttrs).isEmpty) {
          0
        } else {
          2 * action.replace
        }
    }
  }

  private def getSumSensitivity(groupingAttrs: Set[String], sumAttr: String, lower: Int, upper: Int, action: PlausibleDeniabilityAction): Int = {
    action match {
      case addDelAction: AddDelAction => max(abs(upper * addDelAction.add - lower * addDelAction.delete), abs(lower * addDelAction.add - upper * addDelAction.delete))
      case repAction: RepAction =>
        if (repAction.attributes.intersect(groupingAttrs).isEmpty) {
          if (repAction.attributes.contains(sumAttr)) {
            repAction.replace * abs(upper - lower)
          } else {
            0
          }
        } else {
          repAction.replace * max(abs(upper), abs(lower))
        }
    }
  }

  def computeSensitivity(rootNode: RelNode, relNodeActionMap: mutable.Map[RelNode, PlausibleDeniabilityAction]): List[Int] = {
    // Assumption: Root node is a GROUP BY operation
    var root = rootNode
    if (root.isInstanceOf[LogicalSort]) {
      root = root.getInput(0)
    }
    if (root.isInstanceOf[Project]) {
      root = root.getInput(0)
    }
    if (!root.isInstanceOf[Aggregate]) {
      throw Exception("Expected root node of expression to be an aggregation.")
    }

    val aggregate = root.asInstanceOf[Aggregate]
    val aggregationFunctions = aggregate.getAggCallList.asScala.toList
    val child = aggregate.getInput
    val childAction = relNodeActionMap(child)

    // Based on the action, compute sensitivity for each aggregation
    aggregationFunctions.map(func => {
      val funcKind = func.getAggregation.kind
      val groupingAttrs = aggregate.getGroupSet.asList().asScala.map(i => aggregate.getRowType.getFieldNames.get(i)).toSet
      funcKind match {
        case SqlKind.COUNT => getCountSensitivity(groupingAttrs, childAction)
        case SqlKind.SUM =>
          val sumAttr = child.getRowType.getFieldNames.get(func.getArgList.asScala.last)
          var upper, lower = 0

          schema.relations.foreach(relation =>
            if (relation.attributes.exists(attr => attr.name == sumAttr)) {
              upper = relation.attributes.find(attr => attr.name == sumAttr).get.datatype.upper.get
              lower = relation.attributes.find(attr => attr.name == sumAttr).get.datatype.lower.get
            }
          )

          getSumSensitivity(groupingAttrs, sumAttr, lower, upper, childAction)
        case otherKind => throw Exception(s"Unsupported aggregation function: ${otherKind}.")
      }
    })
  }
}
