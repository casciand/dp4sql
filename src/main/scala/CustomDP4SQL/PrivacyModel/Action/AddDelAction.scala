package CustomDP4SQL.PrivacyModel.Action

class AddDelAction(var add: Int, var delete: Int) extends PlausibleDeniabilityAction {
  override def toString: String = s"Add($add) x Del($delete)"
}
