package fr.acinq.bitcoin

import java.io.InputStreamReader

import org.json4s.DefaultFormats
import org.json4s.jackson.JsonMethods
import org.junit.runner.RunWith
import org.scalatest.FlatSpec
import org.scalatest.junit.JUnitRunner

object MnemonicCodeSpec {
  case class TestVectors(english: Array[Array[String]])
}

@RunWith(classOf[JUnitRunner])
class MnemonicCodeSpec extends FlatSpec {
  import MnemonicCode._
  import MnemonicCodeSpec._

  "MnemonicCode" should "pass reference tests" in {
    implicit val format = DefaultFormats

    val stream = classOf[MnemonicCodeSpec].getResourceAsStream("/bip39_vectors.json")
    val vectors = JsonMethods.parse(new InputStreamReader(stream)).extract[TestVectors]
    vectors.english.map(_ match {
      case Array(raw, mnemonics, seed) =>
        assert(toMnemonics(fromHexString(raw)).mkString(" ") === mnemonics)
        assert(toHexString(toSeed(toMnemonics(fromHexString(raw)), "TREZOR")) === seed)
    })
  }
}
