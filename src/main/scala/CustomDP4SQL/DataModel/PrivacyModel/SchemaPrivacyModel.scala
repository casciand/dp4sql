package CustomDP4SQL.DataModel.PrivacyModel

case class SchemaPrivacyModel(relations: List[RelationPrivacyModel]) {
  def getRelationPrivacyModel(relationName: String): Option[RelationPrivacyModel] = {
    relations.find(_.name == relationName)
  }
}
