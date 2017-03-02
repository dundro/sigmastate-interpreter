package scapi.sigma.rework

import java.math.BigInteger
import java.security.SecureRandom

import edu.biu.scapi.primitives.dlog.{DlogGroup, ECElementSendableData, GroupElement}
import org.bouncycastle.util.BigIntegers

import scala.concurrent.Future
import scala.util.Try


object DLogProtocol {

  class DLogSigmaProtocol extends SigmaProtocol[DLogSigmaProtocol] {
    override type A = FirstDLogProverMessage
    override type Z = SecondDLogProverMessage
  }

  case class DLogCommonInput(dlogGroup: DlogGroup, h: GroupElement, override val soundness: Int)
    extends SigmaProtocolCommonInput[DLogSigmaProtocol]

  case class DLogProverInput(w: BigInteger) extends SigmaProtocolPrivateInput[DLogSigmaProtocol]

  case class FirstDLogProverMessage(ecData: ECElementSendableData) extends FirstProverMessage[DLogSigmaProtocol] {
    override def bytes: Array[Byte] = {
      val x = ecData.getX.toByteArray
      val y = ecData.getY.toByteArray

      Array(x.size.toByte, y.size.toByte) ++ x ++ y
    }
  }

  case class SecondDLogProverMessage(z: BigInt) extends SecondProverMessage[DLogSigmaProtocol] {
    override def bytes: Array[Byte] = z.toByteArray
  }

  class DLogProver(override val publicInput: DLogCommonInput, override val privateInput: DLogProverInput)
    extends Prover[DLogSigmaProtocol, DLogCommonInput, DLogProverInput] {

    lazy val group = publicInput.dlogGroup

    var rOpt: Option[BigInteger] = None

    override def firstMessage: FirstDLogProverMessage = {
      val qMinusOne = group.getOrder.subtract(BigInteger.ONE)
      val r = BigIntegers.createRandomInRange(BigInteger.ZERO, qMinusOne, new SecureRandom)
      rOpt = Some(r)
      val a = group.exponentiate(group.getGenerator, r)
      FirstDLogProverMessage(a.generateSendableData().asInstanceOf[ECElementSendableData])
    }

    override def secondMessage(challenge: Challenge): SecondDLogProverMessage = {
      val q: BigInteger = group.getOrder
      val e: BigInteger = new BigInteger(1, challenge.bytes)
      val ew: BigInteger = e.multiply(privateInput.w).mod(q)
      val z: BigInteger = rOpt.get.add(ew).mod(q)
      rOpt = None
      SecondDLogProverMessage(z)
    }
  }

  case class DLogActorProver(override val publicInput: DLogCommonInput, override val privateInput: DLogProverInput)
    extends DLogProver(publicInput, privateInput) with ActorProver[DLogSigmaProtocol, DLogCommonInput, DLogProverInput]

  case class DLogTranscript(override val x: DLogCommonInput,
                            override val a: FirstDLogProverMessage,
                            override val e: Challenge,
                            override val z: SecondDLogProverMessage)
    extends SigmaProtocolTranscript[DLogSigmaProtocol, DLogCommonInput] {


    override lazy val accepted: Boolean = Try {
      assert(x.dlogGroup.isMember(x.h))
      val aElem = x.dlogGroup.reconstructElement(true, a.ecData)
      val left = x.dlogGroup.exponentiate(x.dlogGroup.getGenerator, z.z.bigInteger)
      val hToe = x.dlogGroup.exponentiate(x.h, BigInt(1, e.bytes).bigInteger)
      val right = x.dlogGroup.multiplyGroupElements(aElem, hToe)

      left == right
    }.getOrElse(false)
  }

  abstract class DLogVerifier[DP <: DLogProver](override val publicInput: DLogCommonInput, override val prover: DP)
    extends Verifier[DLogSigmaProtocol, DLogCommonInput] {

    override type P = DP
    override type ST = DLogTranscript
  }

  case class DLogActorVerifier(override val publicInput: DLogCommonInput, override val prover: DLogActorProver)
    extends DLogVerifier[DLogActorProver](publicInput, prover) {

    override def transcript: Future[Option[DLogTranscript]] = ???
  }
}