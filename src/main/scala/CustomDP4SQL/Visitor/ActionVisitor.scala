package CustomDP4SQL.Visitor

import CustomDP4SQL.PrivacyModel.Action.{AddDelAction, PlausibleDeniabilityAction, PubAction, RepAction}
import CustomDP4SQL.Representation.{Edge, RefConstr}
import org.apache.calcite.rel.core.{Filter, Join, Project}
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

  private def deriveSelectAction(node: Filter): PlausibleDeniabilityAction = {
    val childAction = _relNodeActionMap(node.getInput(0))
    childAction
  }

  private def deriveProjectAction(node: Project): PlausibleDeniabilityAction = {
    val childAction = _relNodeActionMap(node.getInput(0))
    val projectionAttributes = node.getRowType.getFieldNames.asScala.toSet

    childAction match {
      case action: RepAction =>
        if (action.attributes.intersect(projectionAttributes).nonEmpty) {
          RepAction(action.replace, action.attributes.intersect(projectionAttributes))
        } else RepAction(0, Set.empty)
      case _ => childAction
    }
  }

  private def deriveJoinAction(node: Join): PlausibleDeniabilityAction = {
    val leftAction = _relNodeActionMap(node.getInput(0))
    val rightAction = _relNodeActionMap(node.getInput(1))

    val joinKeys = extractJoinKeys(node, node.getCondition)
    val leftJoinKeys = joinKeys.map((left, _) => left).toSet
    val rightJoinKeys = joinKeys.map((_, right) => right).toSet
    val leftMaxFreq = leftJoinKeys.map(key => _relNodeMaxFreqMap(node.getInput(0))(key)).min
    val rightMaxFreq = rightJoinKeys.map(key => _relNodeMaxFreqMap(node.getInput(1))(key)).min

    // T-Join1
//    if (leftAction.isInstanceOf[PubAction] && rightAction.isInstanceOf[PubAction]) {
//      return PubAction()
//    }

    // T-Join2
//    if (leftAction.isInstanceOf[AddDelAction] && rightAction.isInstanceOf[PubAction]) {
//      val addDel = leftAction.asInstanceOf[AddDelAction]
//      return AddDelAction(addDel.add * rightMaxFreq, addDel.delete * rightMaxFreq)
//    }

//    if (rightAction.isInstanceOf[AddDelAction] && leftAction.isInstanceOf[PubAction]) {
//      val addDel = rightAction.asInstanceOf[AddDelAction]
//      return AddDelAction(addDel.add * leftMaxFreq, addDel.delete * leftMaxFreq)
//    }

    // T-Join3, T-Join4
//    if (leftAction.isInstanceOf[RepAction] && rightAction.isInstanceOf[PubAction]) {
//      val rep = leftAction.asInstanceOf[RepAction]
//
//      // T-Join3
//      if (rep.attributes.intersect(leftJoinKeys).isEmpty) {
//        return RepAction(rep.replace * rightMaxFreq, rep.attributes)
//      // T-Join4
//      } else {
//        return AddDelAction(rep.replace * rightMaxFreq, rep.replace * rightMaxFreq)
//      }
//    }

//    if (rightAction.isInstanceOf[RepAction] && leftAction.isInstanceOf[PubAction]) {
//      val rep = rightAction.asInstanceOf[RepAction]
//
//      // T-Join3
//      if (rep.attributes.intersect(rightJoinKeys).isEmpty) {
//        return RepAction(rep.replace * leftMaxFreq, rep.attributes)
//      // T-Join4
//      } else {
//        return AddDelAction(rep.replace * leftMaxFreq, rep.replace * leftMaxFreq)
//      }
//    }

    // T-Join-Key
    if (_refConstraints.contains(RefConstr(leftJoinKeys.toList.last, rightJoinKeys.toList.last))) {
      if (leftAction.isInstanceOf[AddDelAction]) {
        return leftAction
      }

      if (leftAction.isInstanceOf[RepAction] && rightAction.isInstanceOf[AddDelAction]) {
        if (rightAction.asInstanceOf[AddDelAction].delete > 0 && !leftAction.asInstanceOf[RepAction].attributes.contains(leftJoinKeys.toList.last)) {
          throw Exception(s"${leftJoinKeys.toList.last} cannot be public.")
        }

        return leftAction
      }

      if (leftAction.isInstanceOf[RepAction] && rightAction.isInstanceOf[RepAction]) {
        return RepAction(leftAction.asInstanceOf[RepAction].replace, leftAction.asInstanceOf[RepAction].attributes.union(rightAction.asInstanceOf[RepAction].attributes))
      }
    }

    if (_refConstraints.contains(RefConstr(rightJoinKeys.toList.last, leftJoinKeys.toList.last))) {
      if (rightAction.isInstanceOf[AddDelAction]) {
        return rightAction
      }

      if (rightAction.isInstanceOf[RepAction] && leftAction.isInstanceOf[AddDelAction]) {
        if (leftAction.asInstanceOf[AddDelAction].delete > 0 && !rightAction.asInstanceOf[RepAction].attributes.contains(rightJoinKeys.toList.last)) {
          throw Exception(s"${rightJoinKeys.toList.last} cannot be public.")
        }

        return rightAction
      }

      if (rightAction.isInstanceOf[RepAction] && leftAction.isInstanceOf[RepAction]) {
        return RepAction(rightAction.asInstanceOf[RepAction].replace, rightAction.asInstanceOf[RepAction].attributes.union(leftAction.asInstanceOf[RepAction].attributes))
      }
    }

    // T-Join5
    if (leftAction.isInstanceOf[AddDelAction] && rightAction.isInstanceOf[AddDelAction]) {
      val leftAddDel = leftAction.asInstanceOf[AddDelAction]
      val rightAddDel = rightAction.asInstanceOf[AddDelAction]
      return AddDelAction(leftAddDel.add * rightMaxFreq + rightAddDel.add * leftMaxFreq, leftAddDel.delete * rightMaxFreq + rightAddDel.delete * leftMaxFreq)
    }

    // T-Join6
    if (leftAction.isInstanceOf[AddDelAction] && rightAction.isInstanceOf[RepAction]) {
      val addDel = leftAction.asInstanceOf[AddDelAction]
      val rep = rightAction.asInstanceOf[RepAction]
      return AddDelAction(addDel.add * rightMaxFreq + rep.replace * leftMaxFreq, addDel.delete * rightMaxFreq + rep.replace * leftMaxFreq)
    }

    if (rightAction.isInstanceOf[AddDelAction] && leftAction.isInstanceOf[RepAction]) {
      val addDel = rightAction.asInstanceOf[AddDelAction]
      val rep = leftAction.asInstanceOf[RepAction]
      return AddDelAction(addDel.add * leftMaxFreq + rep.replace * rightMaxFreq, addDel.delete * leftMaxFreq + rep.replace * rightMaxFreq)
    }

    // T-Join7, T-Join8, T-Join9
    if (leftAction.isInstanceOf[RepAction] && rightAction.isInstanceOf[RepAction]) {
      val leftRep = leftAction.asInstanceOf[RepAction]
      val rightRep = rightAction.asInstanceOf[RepAction]

      // T-Join7
      if (leftRep.attributes.intersect(leftJoinKeys).isEmpty && rightRep.attributes.intersect(rightJoinKeys).isEmpty) {
        return RepAction(leftRep.replace * rightMaxFreq + rightRep.replace * leftMaxFreq, leftRep.attributes.union(rightRep.attributes))
      // T-Join8
      } else {
        return AddDelAction(leftRep.replace * rightMaxFreq + rightRep.replace * leftMaxFreq, leftRep.replace * rightMaxFreq + rightRep.replace * leftMaxFreq)
      }
    }

    // Default to Pub, although cases should be exhaustive
    throw Exception("No matching Join rule")
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
        // Default to child action
        case _ => relNodeActionMap(node.getInput(0))
      }
    }
  }
}
