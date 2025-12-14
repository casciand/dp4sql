package CustomDP4SQL.Visitor

import CustomDP4SQL.PrivacyModel.Action.{AddDelAction, PlausibleDeniabilityAction, RepAction}
import CustomDP4SQL.Representation.{Edge, RefConstr}
import org.apache.calcite.adapter.enumerable.EnumerableTableScan
import org.apache.calcite.rel.core.{Filter, Join, Project}
import org.apache.calcite.rel.logical.LogicalAggregate
import org.apache.calcite.rel.{RelNode, RelVisitor}
import org.apache.calcite.rex.{RexCall, RexInputRef, RexNode}
import org.apache.calcite.sql.SqlKind

import scala.collection.mutable
import scala.jdk.CollectionConverters.CollectionHasAsScala

class ActionVisitor(private val _baseRelationActionMap: mutable.Map[String, PlausibleDeniabilityAction],
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

  private def extractPredicateAttrs(node: Filter, condition: RexNode): Set[String] = {
    condition match {
      case input: RexInputRef => Set(node.getRowType.getFieldNames.get(input.getIndex))
      case call: RexCall =>
        call.getOperands.asScala.flatMap(operand => extractPredicateAttrs(node, operand)).toSet
      case _ => Set.empty
    }
  }

  private def deriveSelectAction(node: Filter): PlausibleDeniabilityAction = {
    val childAction = _relNodeActionMap(node.getInput(0))
    childAction match {
      case addDelAction: AddDelAction => addDelAction
      case repAction: RepAction =>
        val predicateAttrs = extractPredicateAttrs(node, node.getCondition)
        if (repAction.attributes.intersect(predicateAttrs).isEmpty) {
          repAction
        } else {
          AddDelAction(repAction.replace, repAction.replace)
        }
    }
  }

  private def deriveProjectAction(node: Project): PlausibleDeniabilityAction = {
    val childAction = _relNodeActionMap(node.getInput(0))
    val projectionAttrs = node.getRowType.getFieldNames.asScala.toSet

    childAction match {
      case addDelAction: AddDelAction => addDelAction
      case repAction: RepAction =>
        if (repAction.attributes.intersect(projectionAttrs).nonEmpty) {
          RepAction(repAction.replace, repAction.attributes.intersect(projectionAttrs))
        } else RepAction(0, Set.empty)
    }
  }

  private def deriveAggregateAction(node: LogicalAggregate): PlausibleDeniabilityAction = {
    val childAction = _relNodeActionMap(node.getInput(0))
    val aggregationFunctions = node.getAggCallList.asScala.toList

    if (aggregationFunctions.isEmpty) {
      RepAction(0, Set.empty)
    } else if (aggregationFunctions.size > 1) {
      throw Error("More than one intermediate aggregation.")
    } else if (aggregationFunctions.last.getAggregation.kind != SqlKind.COUNT) {
      childAction
      // throw Error(s"Unsupported intermediate aggregation: ${aggregationFunctions.last.getAggregation.kind}.")
    } else {
      childAction match {
        case addDelAction: AddDelAction => RepAction(addDelAction.add + addDelAction.delete, Set(node.getRowType.getFieldNames.asScala.last))
        case repAction: RepAction =>
          if (repAction.attributes.intersect(node.getRowType.getFieldNames.asScala.dropRight(1).toSet).isEmpty) {
            RepAction(0, Set.empty)
          } else {
            RepAction(2 * repAction.replace, Set(node.getRowType.getFieldNames.asScala.last))
          }
      }
    }
  }

  private def deriveJoinAction(node: Join): PlausibleDeniabilityAction = {
    var leftIndex = 0
    var rightIndex = 1
    var leftAction = _relNodeActionMap(node.getInput(leftIndex))
    var rightAction = _relNodeActionMap(node.getInput(rightIndex))

    val joinKeys = extractJoinKeys(node, node.getCondition)
    var leftJoinKeys = joinKeys.map((left, _) => left).toSet
    var rightJoinKeys = joinKeys.map((_, right) => right).toSet
    var leftMaxFreq = leftJoinKeys.map(key => _relNodeMaxFreqMap(node.getInput(0))(key)).min
    var rightMaxFreq = rightJoinKeys.map(key => _relNodeMaxFreqMap(node.getInput(1))(key)).min
    var leftIsTable = node.getInput(leftIndex).isInstanceOf[EnumerableTableScan]
    var rightIsTable = node.getInput(rightIndex).isInstanceOf[EnumerableTableScan]

    // If there is an AddDel action, then place it on the left
    if (rightAction.isInstanceOf[AddDelAction]) {
      leftIndex = 1
      rightIndex = 0

      val tempAction = leftAction
      leftAction = rightAction
      rightAction = tempAction

      val tempJoinKeys = leftJoinKeys
      leftJoinKeys = rightJoinKeys
      rightJoinKeys = tempJoinKeys

      val tempMaxFreq = leftMaxFreq
      leftMaxFreq = rightMaxFreq
      rightMaxFreq = tempMaxFreq

      val tempIsTable = leftIsTable
      leftIsTable = rightIsTable
      rightIsTable = tempIsTable
    }

    // Derive the action
    leftAction match {
      case leftAddDel: AddDelAction =>
        // T-Key1
        if (leftIsTable && _refConstraints.contains(RefConstr(leftJoinKeys.toList.last, rightJoinKeys.toList.last))) {
          leftAddDel
        } else {
          rightAction match {
            // T-Join1
            case rightAddDel: AddDelAction =>
              if (rightIsTable && _refConstraints.contains(RefConstr(rightJoinKeys.toList.last, leftJoinKeys.toList.last))) {
                rightAddDel
              } else {
                AddDelAction(leftAddDel.add * rightMaxFreq + rightAddDel.add * leftMaxFreq,
                  leftAddDel.delete * rightMaxFreq + rightAddDel.delete * leftMaxFreq)
              }
            case rightRep: RepAction =>
              // T-Key2
//              if (_refConstraints.contains(RefConstr(rightJoinKeys.toList.last, leftJoinKeys.toList.last))
//                && rightRep.attributes.contains(rightJoinKeys.toList.last) && leftAddDel.add == 0) {
//                RepAction(rightRep.replace, rightRep.attributes.union(node.getInput(leftIndex).getRowType.getFieldNames.asScala.toSet))
//              // T-Join2
//              } else {
              if (rightIsTable && _refConstraints.contains(RefConstr(rightJoinKeys.toList.last, leftJoinKeys.toList.last))) {
                RepAction(rightRep.replace, rightRep.attributes.union(node.getInput(leftIndex).getRowType.getFieldNames.asScala.toSet))
              } else {
                AddDelAction(leftAddDel.add * rightMaxFreq + rightRep.replace * leftMaxFreq,
                  leftAddDel.delete * rightMaxFreq + rightRep.replace * leftMaxFreq)
              }
            //              }
          }
        }
      case leftRep: RepAction =>
        rightAction match {
          case rightRep: RepAction =>
            // T-Key3
//            if (_refConstraints.contains(RefConstr(leftJoinKeys.toList.last, rightJoinKeys.toList.last))) {
//              RepAction(leftRep.replace, leftRep.attributes.union(rightRep.attributes))
//            } else
//              if (_refConstraints.contains(RefConstr(rightJoinKeys.toList.last, leftJoinKeys.toList.last))) {
//              RepAction(rightRep.replace, rightRep.attributes.union(leftRep.attributes))
//            } else {
            if (rightRep.replace == 0 && rightRep.attributes.isEmpty && leftRep.replace == 0 && leftRep.attributes.isEmpty) {
              rightRep
            } else if (rightIsTable && !leftRep.attributes.contains(leftJoinKeys.toList.last) && _refConstraints.contains(RefConstr(rightJoinKeys.toList.last, leftJoinKeys.toList.last))) {
              RepAction(rightRep.replace, rightRep.attributes.union(leftRep.attributes))
//            } else if (rightIsTable && _refConstraints.contains(RefConstr(rightJoinKeys.toList.last, leftJoinKeys.toList.last))) {
//              rightRep
            } else if (rightIsTable && _refConstraints.contains(RefConstr(rightJoinKeys.toList.last, leftJoinKeys.toList.last))) {
              RepAction(rightRep.replace, rightRep.attributes.union(node.getInput(leftIndex).getRowType.getFieldNames.asScala.toSet))
            } else if (leftIsTable && !rightRep.attributes.contains(rightJoinKeys.toList.last) && _refConstraints.contains(RefConstr(leftJoinKeys.toList.last, rightJoinKeys.toList.last))) {
              RepAction(leftRep.replace, leftRep.attributes.union(rightRep.attributes))
            } else if (leftIsTable && _refConstraints.contains(RefConstr(leftJoinKeys.toList.last, rightJoinKeys.toList.last))) {
              RepAction(leftRep.replace, leftRep.attributes.union(node.getInput(rightIndex).getRowType.getFieldNames.asScala.toSet))
            }
//            } else if (leftIsTable && _refConstraints.contains(RefConstr(leftJoinKeys.toList.last, rightJoinKeys.toList.last))) {
//              leftRep
//            }
              // T-Join3
            else if (!leftRep.attributes.contains(leftJoinKeys.toList.last) && !rightRep.attributes.contains(rightJoinKeys.toList.last)) {
                RepAction(leftRep.replace * rightMaxFreq + rightRep.replace * leftMaxFreq,
                  leftRep.attributes.union(rightRep.attributes))
              // T-Join4
              } else {
                AddDelAction(leftRep.replace * rightMaxFreq + rightRep.replace * leftMaxFreq,
                  leftRep.replace * rightMaxFreq + rightRep.replace * leftMaxFreq)
              }
            }
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
