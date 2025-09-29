package CustomDP4SQL.DataModel.PrivacyModel

case class RelationPrivacyModel(name: String, global_entity: Boolean, privacy_policy: PrivacyPolicy, attributes: List[AttributePrivacyModel]) {
  def getAttributePrivacyModel(attributeName: String): Option[AttributePrivacyModel] = {
    attributes.find(_.name == attributeName)
  }
}
