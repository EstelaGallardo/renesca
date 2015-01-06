package renesca.graph

import renesca.json.Value

sealed trait GraphChange {
  val timestamp:Long = System.nanoTime
}
case class NodeSetProperty(nodeId:Long, key:String, value:Value) extends GraphChange
case class NodeRemoveProperty(nodeId:Long, key:String) extends GraphChange
case class NodeSetLabel(nodeId:Long, label:Label) extends GraphChange
case class NodeRemoveLabel(nodeId:Long, label:Label) extends GraphChange
case class NodeDelete(nodeId:Long) extends GraphChange
case class RelationSetProperty(relationId:Long, key:String, value:Value) extends GraphChange
case class RelationRemoveProperty(relationId:Long, key:String) extends GraphChange
case class RelationDelete(relationId:Long) extends GraphChange


