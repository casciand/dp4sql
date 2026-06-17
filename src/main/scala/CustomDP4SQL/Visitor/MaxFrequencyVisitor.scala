package CustomDP4SQL.Visitor

import CustomDP4SQL.DataModel.PrivacyModel.SchemaPrivacyModel
import org.apache.calcite.rel.core.{Filter, Join, Project}
import org.apache.calcite.rel.{RelNode, RelVisitor}
import org.apache.calcite.rex.{RexCall, RexInputRef, RexNode}
import org.apache.calcite.sql.SqlKind

import scala.collection.mutable
import scala.jdk.CollectionConverters.CollectionHasAsScala

class MaxFrequencyVisitor(schemaConstraints: SchemaPrivacyModel) extends RelVisitor {
  val _relNodeMaxFreqMap: mutable.Map[RelNode, mutable.Map[String, Int]] = mutable.Map[RelNode, mutable.Map[String, Int]]()

  def relNodeMaxFreqMap: mutable.Map[RelNode, mutable.Map[String, Int]] = _relNodeMaxFreqMap

  def extractJoinKeys(node: Join, condition: RexNode): List[(String, String)] = {
    condition match {
      case call: RexCall if call.getKind == SqlKind.EQUALS =>
        val operands = call.getOperands.asScala.toList
        operands match {
          case List(left: RexInputRef, right: RexInputRef) =>
            val names = node.getRowType.getFieldNames.asScala.toList
            val fieldNames = names
            List((fieldNames(left.getIndex), fieldNames(right.getIndex)))
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

  private def deriveSelectMaxFreq(node: Filter, attr: String): Int = {
    _relNodeMaxFreqMap(node.getInput(0)).getOrElse(attr, -1)
  }

  private def deriveProjectMaxFreq(node: Project, attr: String): Int = {
    _relNodeMaxFreqMap(node.getInput(0)).getOrElse(attr, 1)
  }

  private def deriveJoinMaxFreq(node: Join, attr: String): Int = {
    val leftInput = node.getInput(0)
    val rightInput = node.getInput(1)
    val joinKeys = extractJoinKeys(node, node.getCondition)

    // Find join key with the smallest mmf
    val leftJoinKey = joinKeys.minBy { case (key, _) => _relNodeMaxFreqMap(leftInput)(key) }._1
    val rightJoinKey = joinKeys.minBy { case (_, key) => _relNodeMaxFreqMap(rightInput)(key) }._2

    if (leftInput.getRowType.getFieldNames.contains(attr)) {
      _relNodeMaxFreqMap(leftInput)(attr) * _relNodeMaxFreqMap(rightInput)(rightJoinKey)
    } else {
      _relNodeMaxFreqMap(rightInput)(attr) * _relNodeMaxFreqMap(leftInput)(leftJoinKey)
    }
  }

  override def visit(node: RelNode, ordinal: Int, parent: RelNode): Unit = {
    _relNodeMaxFreqMap += (node -> mutable.Map[String, Int]())

    // Base case
    if (node.getInputs.isEmpty) {
      val tableName = node.getTable.getQualifiedName.get(0)
      node.getRowType.getFieldNames.forEach(attr => {
        val maxFreq = schemaConstraints.getRelationPrivacyModel(tableName).get.getAttributePrivacyModel(attr).get.max_frequency
        _relNodeMaxFreqMap(node) += (attr.toLowerCase -> maxFreq)
      })
    } else {
      // Visit children
      node.childrenAccept(this)

      // Derive mmf for each attribute of current node
      for (attr <- node.getRowType.getFieldNames.asScala) {
        val maxFreq = node match {
          case filter: Filter => deriveSelectMaxFreq(filter, attr.toLowerCase)
          case project: Project => deriveProjectMaxFreq(project, attr.toLowerCase)
          case join: Join => deriveJoinMaxFreq(join, attr.toLowerCase)
          // Default to child mmf
          case _ => _relNodeMaxFreqMap(node.getInput(0)).getOrElse(attr, 1)
        }

        _relNodeMaxFreqMap(node) += (attr.toLowerCase -> maxFreq)
      }
    }
  }
}
