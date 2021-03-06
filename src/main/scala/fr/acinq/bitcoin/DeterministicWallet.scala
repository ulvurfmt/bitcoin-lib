package fr.acinq.bitcoin

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import Protocol._
import org.bouncycastle.crypto.digests.SHA512Digest
import org.bouncycastle.crypto.macs.HMac
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.math.ec.ECPoint

/**
 * see https://github.com/bitcoin/bips/blob/master/bip-0032.mediawiki
 */
object DeterministicWallet {
  case class KeyPath(path: Seq[Long]) {
    def lastChildNumber: Long = if (path.isEmpty) 0L else path.last
    def derive(number: Long) = KeyPath(path :+ number)
    
    override def toString = path.map(KeyPath.childNumberToString).foldLeft("m")(_ + "/" + _)
  }

  object KeyPath {
    def childNumberToString(childNumber: Long) = if (isHardened(childNumber)) ((childNumber - hardenedKeyIndex).toString + "h") else childNumber.toString
  }

  implicit def keypath2longseq(input: KeyPath): Seq[Long] = input.path

  implicit def longseq2keypath(input: Seq[Long]): KeyPath = KeyPath(input)

  val hardenedKeyIndex = 0x80000000L

  def hardened(index: Long): Long = hardenedKeyIndex + index

  def isHardened(index: Long): Boolean = index >= hardenedKeyIndex

  case class ExtendedPrivateKey(secretkey: BinaryData, chaincode: BinaryData, depth: Int, path: KeyPath, parent: Long) {
    require(secretkey.length == 32)
    require(chaincode.length == 32)
  }

  case class ExtendedPublicKey(publickey: BinaryData, chaincode: BinaryData, depth: Int, path: KeyPath, parent: Long) {
    require(publickey.length == 33)
    require(chaincode.length == 32)
  }

  def encode(input: ExtendedPrivateKey, testnet: Boolean): String = {
    val out = new ByteArrayOutputStream()
    writeUInt32BigEndian(if (testnet) tprv else xprv, out)
    writeUInt8(input.depth, out)
    writeUInt32BigEndian(input.parent, out)
    writeUInt32BigEndian(input.path.lastChildNumber, out)
    out.write(input.chaincode)
    out.write(0)
    out.write(input.secretkey)
    val buffer = out.toByteArray
    val checksum = Crypto.hash256(buffer).take(4)
    Base58.encode(buffer ++ checksum)
  }

  def encode(input: ExtendedPublicKey, testnet: Boolean): String = {
    val out = new ByteArrayOutputStream()
    writeUInt32BigEndian(if (testnet) tpub else xpub, out)
    writeUInt8(input.depth, out)
    writeUInt32BigEndian(input.parent, out)
    writeUInt32BigEndian(input.path.lastChildNumber, out)
    out.write(input.chaincode)
    out.write(input.publickey)
    val buffer = out.toByteArray
    val checksum = Crypto.hash256(buffer).take(4)
    Base58.encode(buffer ++ checksum)
  }

  /**
   *
   * @param seed random seed
   * @return a "master" private key
   */
  def generate(seed: Seq[Byte]): ExtendedPrivateKey = {
    val I = Crypto.hmac512("Bitcoin seed".getBytes("UTF-8"), seed)
    val IL = I.take(32)
    val IR = I.takeRight(32)
    ExtendedPrivateKey(IL, IR, depth = 0, path = List.empty[Long], parent = 0L)
  }

  /**
   *
   * @param input extended private key
   * @return the public key for this private key
   */
  def publicKey(input: ExtendedPrivateKey) : ExtendedPublicKey = {
    // add an extra 1 to make sure the returned public key will be encoded
    // in compressed format as per specs.
    val pub = Crypto.publicKeyFromPrivateKey(input.secretkey.data :+ 1.toByte)
    ExtendedPublicKey(pub, input.chaincode, depth = input.depth, path = input.path, parent = input.parent)
  }

  /**
   *
   * @param input extended public key
   * @return the fingerprint for this public key
   */
  def fingerprint(input: ExtendedPublicKey): Long = uint32(Crypto.hash160(input.publickey).take(4).reverse)

  /**
   *
   * @param input extended private key
   * @return the fingerprint for this private key (which is based on the corresponding public key)
   */
  def fingerprint(input: ExtendedPrivateKey): Long = fingerprint(publicKey(input))

  /**
   *
   * @param parent extended private key
   * @param index index of the child key
   * @return the derived private key at the specified index
   */
  def derivePrivateKey(parent: ExtendedPrivateKey, index: Long): ExtendedPrivateKey = {
    val I = if (isHardened(index)) {
      val buffer = 0.toByte +: parent.secretkey.data
      Crypto.hmac512(parent.chaincode, buffer ++ writeUInt32BigEndian(index))
    } else {
      val pub = publicKey(parent).publickey
      Crypto.hmac512(parent.chaincode, pub.data ++ writeUInt32BigEndian(index))
    }
    val IL = I.take(32)
    val IR = I.takeRight(32)
    val key = new BigInteger(1, IL).add(new BigInteger(1, parent.secretkey)).mod(Crypto.curve.getN) // Crypto.curve should not be used like this...
    val buffer = key.toByteArray.dropWhile(_ == 0) // BigInteger.toByteArray may add a leading 0x00
    val buffer1 = buffer.length match {
      case 32 => buffer
      case length if length < 32 => Array.fill(32 - length)(0.toByte) ++ buffer
      case _ => throw new RuntimeException("cannot derive private key")
    }
    ExtendedPrivateKey(buffer1, chaincode = IR, depth = parent.depth + 1, path = parent.path.derive(index), parent = fingerprint(parent))
  }

  /**
   *
   * @param parent extended public key
   * @param index index of the child key
   * @return the derived public key at the specified index
   */
  def derivePublicKey(parent: ExtendedPublicKey, index: Long) : ExtendedPublicKey = {
    require(!isHardened(index), "Cannot derive public keys from public hardened keys")

    val I = Crypto.hmac512(parent.chaincode, parent.publickey.data ++ writeUInt32BigEndian(index))
    val IL = I.take(32)
    val IR = I.takeRight(32)
    val p = new BigInteger(1, IL)
    if (p.compareTo(Crypto.curve.getN) == 1) {
      throw new RuntimeException("cannot generated child public key")
    }
    val Ki = Crypto.point(p).add(Crypto.curve.getCurve.decodePoint(parent.publickey))
    if (Ki.isInfinity) {
      throw new RuntimeException("cannot generated child public key")
    }
    val buffer = Ki.getEncoded(true)
    ExtendedPublicKey(buffer, chaincode = IR, depth = parent.depth + 1, path = parent.path.derive(index), parent = fingerprint(parent))
  }

  def derivePrivateKey(parent: ExtendedPrivateKey, chain: Seq[Long]): ExtendedPrivateKey = chain.foldLeft(parent)(derivePrivateKey)

  def derivePublicKey(parent: ExtendedPublicKey, chain: Seq[Long]): ExtendedPublicKey = chain.foldLeft(parent)(derivePublicKey)

  // mainnet
  val xprv = 0x0488ade4
  val xpub = 0x0488b21e

  // testnet
  val tprv = 0x04358394
  val tpub = 0x043587cf

  // segnet
  val sprv = 0x05358394
  val spub = 0x053587cf
}

