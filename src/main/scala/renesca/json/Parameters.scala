package renesca.json

import renesca.NonBacktickName
import renesca.json.ParameterValue.ParameterMap

import scala.collection.mutable

case class PropertyKey(name:String) extends NonBacktickName {
  override def equals(other: Any): Boolean = other match {
    case that: PropertyKey => this.name == that.name
    case that: String => name == that
    case _ => false
  }
  override def hashCode = name.hashCode
}

sealed trait ParameterValue
sealed trait SoleParameterValue extends ParameterValue
sealed trait PropertyValue extends ParameterValue

final case class LongPropertyValue(value:Long) extends PropertyValue {
  override def equals(other: Any): Boolean = other match {
    case that: LongPropertyValue => this.value == that.value
    case that: Int => value == that
    case that: Long => value == that
    case _ => false
  }
  override def hashCode = value.hashCode
}

final case class DoublePropertyValue(value:Double) extends PropertyValue {
  override def equals(other: Any): Boolean = other match {
    case that: DoublePropertyValue => this.value == that.value
    case that: Double => value == that
    case _ => false
  }
  override def hashCode = value.hashCode
}
case class StringPropertyValue(value:String) extends PropertyValue {
  override def equals(other: Any): Boolean = other match {
    case that: StringPropertyValue => this.value == that.value
    case that: String => value == that
    case _ => false
  }
  override def hashCode = value.hashCode
}
case class BooleanPropertyValue(value:Boolean) extends PropertyValue {
  override def equals(other: Any): Boolean = other match {
    case that: BooleanPropertyValue => this.value == that.value
    case that: Boolean => value == that
    case _ => false
  }
  override def hashCode = value.hashCode
}
case class ArrayPropertyValue(value:Seq[PropertyValue]) extends PropertyValue {
  //TODO: forbid nesting of propertyvalues
  override def equals(other: Any): Boolean = other match {
    case that: ArrayPropertyValue => this.value == that.value
    case that: Seq[_] => value.sameElements(that)
    case _ => false
  }
  override def hashCode = value.hashCode
}

case class ArrayParameterValue(value:Seq[ParameterValue]) extends SoleParameterValue {
  override def equals(other: Any): Boolean = other match {
    case that: ArrayParameterValue => this.value == that.value
    case that: Seq[_] => value.sameElements(that)
    case _ => false
  }
  override def hashCode = value.hashCode
}
case class MapParameterValue(value:ParameterMap) extends SoleParameterValue {
  override def equals(other: Any): Boolean = other match {
    case that: MapParameterValue => this.value == that.value
    case that: Map[_,_] => value.sameElements(that)
    case _ => false
  }
  override def hashCode = value.hashCode
}

object PropertyKey {
  implicit def StringMapToPropertyKeyMap(key: String)= new AnyRef {
    def ->(x: PropertyValue) = (PropertyKey(key),x)
    def ->(x: SoleParameterValue) = (PropertyKey(key),x)
  }
}

object PropertyValue {
  type PropertyMap = Map[PropertyKey, PropertyValue]
  type MutablePropertyMap = mutable.Map[PropertyKey, PropertyValue]

  implicit def primitiveToPropertyValue(x: Long): PropertyValue = LongPropertyValue(x)
  implicit def primitiveToPropertyValue(x: Int): PropertyValue = LongPropertyValue(x)
  implicit def primitiveToPropertyValue(x: Double): PropertyValue = DoublePropertyValue(x)
  implicit def primitiveToPropertyValue(x: String): PropertyValue = StringPropertyValue(x)
  implicit def primitiveToPropertyValue(x: Boolean): PropertyValue = BooleanPropertyValue(x)

  implicit def SeqLongToPropertyValue(xs: Seq[Long]): PropertyValue = ArrayPropertyValue(xs map LongPropertyValue)
  implicit def SeqIntToPropertyValue(xs: Seq[Int]): PropertyValue = ArrayPropertyValue(xs.map(x => LongPropertyValue(x.toLong)))
  implicit def SeqDoubleToPropertyValue(xs: Seq[Double]): PropertyValue = ArrayPropertyValue(xs map DoublePropertyValue)
  implicit def SeqStringToPropertyValue(xs: Seq[String]): PropertyValue = ArrayPropertyValue(xs map StringPropertyValue)
  implicit def SeqBooleanToPropertyValue(xs: Seq[Boolean]): PropertyValue = ArrayPropertyValue(xs map BooleanPropertyValue)

  implicit def propertyValueToPrimitive(x:LongPropertyValue):Long = x.value
  implicit def propertyValueToPrimitive(x:DoublePropertyValue):Double = x.value
  implicit def propertyValueToPrimitive(x:StringPropertyValue):String = x.value
  implicit def propertyValueToPrimitive(x:BooleanPropertyValue):Boolean = x.value

  implicit def primitiveToPropertyKey(x:String):PropertyKey = PropertyKey(x)
  implicit def propertyKeyToPrimitive(x:PropertyKey):String = x.name

  //TODO: ArrayPropertyValue to Array
}

object ParameterValue {
  type ParameterMap = Map[PropertyKey,ParameterValue]

  implicit def ParameterMapToMapParameterValue(map:ParameterMap):MapParameterValue = MapParameterValue(map)
  implicit def MapParameterValueToParameterMap(map:MapParameterValue):ParameterMap = map.value
}


