package CustomDP4SQL.Visitor

import CustomDP4SQL.PrivacyModel.Action.{AddDelAction, PlausibleDeniabilityAction, PubAction, RepAction}
import CustomDP4SQL.Representation.{Edge, RefConstr}
import org.apache.calcite.interpreter.AggregateNode
import org.apache.calcite.rel.core.{Filter, Join, Project}
import org.apache.calcite.rel.logical.LogicalAggregate
import org.apache.calcite.rel.{RelNode, RelVisitor}
import org.apache.calcite.rex.{RexCall, RexInputRef, RexNode}
import org.apache.calcite.sql.SqlKind

import scala.collection.mutable
import scala.jdk.CollectionConverters.CollectionHasAsScala

class PrivateSQLVisitor(private val _baseRelationActionMap: mutable.Map[String, PlausibleDeniabilityAction],
                    private val _relNodeMaxFreqMap: mutable.Map[RelNode, mutable.Map[String, Int]],
                    private val _dataDependencyGraph: mutable.Map[String, Set[Edge]],
                    private val _refConstraints: mutable.Set[RefConstr]
                   ) extends RelVisitor {
  private val _relNodeActionMap: mutable.Map[RelNode, PlausibleDeniabilityAction] = mutable.Map[RelNode, PlausibleDeniabilityAction]()

  def relNodeActionMap: mutable.Map[RelNode, PlausibleDeniabilityAction] = _relNodeActionMap

  def extractJoinKeys(node: Join, condition: RexNode): List[(String, String)] = {
    condition match {
      case call: RexCall if call.getKind == SqlKind.EQUALS =>
        val operands = call.getOperands.asScala.toList
        operands match {
          case List(left: RexInputRef, right: RexInputRef) =>
            val fieldNames = node.getRowType.getFieldNames
            fieldNames.forEach(_.replaceAll("\\d+", ""))
            List((fieldNames.get(left.getIndex), fieldNames.get(right.getIndex)))
          case _ => Nil
        }
      case call: RexCall if call.getKind == SqlKind.AND =>
        // Flatten AND of multiple EQUALS
        val operands = call.getOperands.asScala.toList
        operands match {
          case List(left: RexCall, right: RexCall) =>
            extractJoinKeys(node, left) ::: extractJoinKeys(node, right)
          case _ => Nil
        }
      case _ => Nil
    }
  }

  private def deriveSelectAction(node: Filter): PlausibleDeniabilityAction = {
    val childAction = _relNodeActionMap(node.getInput(0))
    childAction
  }

  private def deriveProjectAction(node: Project): PlausibleDeniabilityAction = {
    val childAction = _relNodeActionMap(node.getInput(0))
    childAction
  }

  private def deriveAggregateAction(node: LogicalAggregate): PlausibleDeniabilityAction = {
    val childAction = _relNodeActionMap(node.getInput(0)).asInstanceOf[AddDelAction]

    if (node.getAggCallList.asScala.toList.contains(SqlKind.COUNT)) {
      AddDelAction(0, 2 * childAction.delete)
    } else {
      childAction
    }
  }

  private def deriveJoinAction(node: Join): PlausibleDeniabilityAction = {
    val leftAction = _relNodeActionMap(node.getInput(0)).asInstanceOf[AddDelAction]
    val rightAction = _relNodeActionMap(node.getInput(1)).asInstanceOf[AddDelAction]

    val joinKeys = extractJoinKeys(node, node.getCondition)
    val leftJoinKeys = joinKeys.map((left, _) => left).toSet
    val rightJoinKeys = joinKeys.map((_, right) => right).toSet
    val leftMaxFreq = leftJoinKeys.map(key => _relNodeMaxFreqMap(node.getInput(0))(key)).min
    val rightMaxFreq = rightJoinKeys.map(key => _relNodeMaxFreqMap(node.getInput(1))(key)).min

    // Join on key
    if (_refConstraints.contains(RefConstr(leftJoinKeys.toList.last, rightJoinKeys.toList.last))) {
      AddDelAction(0, rightAction.delete * leftMaxFreq + leftAction.delete)
    } else if (_refConstraints.contains(RefConstr(rightJoinKeys.toList.last, leftJoinKeys.toList.last))) {
      AddDelAction(0, leftAction.delete * rightMaxFreq + rightAction.delete)
    // General case
    } else {
      AddDelAction(0, leftAction.delete * rightMaxFreq + rightAction.delete * leftMaxFreq + leftAction.delete * rightAction.delete)
    }
  }

  override def visit(node: RelNode, ordinal: Int, parent: RelNode): Unit = {
    // Base case
    if (node.getInputs.isEmpty) {
      val tableName = node.getTable.getQualifiedName.get(0)
      relNodeActionMap(node) = _baseRelationActionMap(tableName)
    } else {
      // Visit children
      node.childrenAccept(this)

      // Derive action for current node
      relNodeActionMap(node) = node match {
        case filterNode: Filter => deriveSelectAction(filterNode)
        case projectNode: Project => deriveProjectAction(projectNode)
        case joinNode: Join => deriveJoinAction(joinNode)
        case aggregateNode: LogicalAggregate => deriveAggregateAction(aggregateNode)
        // Default to child action
        case _ => relNodeActionMap(node.getInput(0))
      }
    }
  }
}

