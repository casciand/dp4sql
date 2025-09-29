package CustomDP4SQL.PrivacyModel.Action

class RepAction(var replace: Int, var attributes: Set[String]) extends PlausibleDeniabilityAction {
  override def toString: String = s"Rep($replace), ${attributes.toString()}"
}
