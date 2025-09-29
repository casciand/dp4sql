package CustomDP4SQL.Calcite

import CustomDP4SQL.DataModel.Config.Config
import CustomDP4SQL.DataModel.Schema.Attribute
import org.apache.calcite.rel.`type`.{RelDataType, RelDataTypeFactory}
import org.apache.calcite.schema.impl.AbstractTable
import org.apache.calcite.sql.`type`.SqlTypeName

import scala.jdk.CollectionConverters.SeqHasAsJava

/**
 * Custom Calcite table implementation.
 *
 * @param attributes List of columns defining the table structure
 */
class CalciteTable(private var attributes: Set[Attribute], private val config: Config) extends AbstractTable {
  override def getRowType(typeFactory: RelDataTypeFactory): RelDataType = {
    val types = attributes.toList.map { col =>
      val sqlTypeName = SqlTypeName.valueOf(
        config.type_mappings.getOrElse(col.datatype.name.toLowerCase, config.type_mappings("default"))
      )
      val relType = typeFactory.createSqlType(sqlTypeName)
      typeFactory.createTypeWithNullability(relType, col.datatype.nullable)
    }
    
    val names = attributes.toList.map(_.name)
    typeFactory.createStructType(types.asJava, names.asJava)
  }

  def updateAttributes(newAttributes: Set[Attribute]): Unit = {
    attributes = newAttributes
  }
}
