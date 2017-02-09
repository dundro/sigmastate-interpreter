package sigmastate


sealed trait BooleanConstantProposition extends SigmaStateProposition {
  val value: Boolean
}

object BooleanConstantProposition {
  def fromBoolean(b: Boolean) = b match {
    case true => TrueProposition
    case false => FalseProposition
  }
}


case object TrueProposition extends BooleanConstantProposition {
  override val value = true
  override lazy val bytes: Array[Byte] = ???
}

case object FalseProposition extends BooleanConstantProposition {
  override val value = false
  override lazy val bytes: Array[Byte] = ???
}