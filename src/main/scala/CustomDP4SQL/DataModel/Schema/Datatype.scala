package CustomDP4SQL.DataModel.Schema

case class Datatype(
                     name: String, 
                     nullable: Boolean, 
                     unique: Boolean, 
                     values: Option[Set[String]], 
                     max_length: Option[Int], 
                     upper: Option[Int], 
                     lower: Option[Int], 
                     scale: Option[Int], 
                     precision: Option[Int],
                     to_relation: Option[String], 
                     to_attribute: Option[String], 
                     onto: Option[Boolean]
                   )
