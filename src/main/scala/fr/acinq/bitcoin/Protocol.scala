package fr.acinq.bitcoin

import java.io._
import java.math.BigInteger
import java.net.{Inet4Address, Inet6Address, InetAddress}
import java.util

import com.typesafe.config.ConfigFactory
import fr.acinq.bitcoin.Script.Runner

import scala.collection.mutable.ArrayBuffer

/**
 * see https://en.bitcoin.it/wiki/Protocol_specification
 */

object BinaryData {
  def apply(hex: String): BinaryData = hex

  val empty: BinaryData = Seq.empty[Byte]
}

case class BinaryData(data: Seq[Byte]) {
  def length = data.length

  override def toString = toHexString(data)
}

object Protocol {
  /**
   * basic serialization functions
   */

  val PROTOCOL_VERSION = ConfigFactory.load().getLong("bitcoin-lib.protocol-version")

  def uint8(blob: Seq[Byte]) = blob(0) & 0xffl

  def uint8(input: InputStream): Long = input.read().toLong

  def writeUInt8(input: Long, out: OutputStream): Unit = out.write((input & 0xff).asInstanceOf[Int])

  def writeUInt8(input: Int, out: OutputStream): Unit = writeUInt8(input.toLong, out)

  def writeUInt8(input: Int): Array[Byte] = {
    val out = new ByteArrayOutputStream()
    writeUInt8(input, out)
    out.toByteArray
  }

  def uint16(a: Int, b: Int): Long = ((a & 0xffl) << 0) | ((b & 0xffl) << 8)

  def uint16BigEndian(a: Int, b: Int): Long = ((b & 0xffl) << 0) | ((a & 0xffl) << 8)

  def uint16(blob: Seq[Byte]): Long = uint16(blob(0), blob(1))

  def uint16BigEndian(blob: Array[Byte]): Long = uint16BigEndian(blob(0), blob(1))

  def uint16(input: InputStream): Long = uint16(input.read(), input.read())

  def uint16BigEndian(input: InputStream): Long = uint16BigEndian(input.read(), input.read())

  def writeUInt16(input: Long, out: OutputStream): Unit = {
    writeUInt8((input) & 0xff, out)
    writeUInt8((input >> 8) & 0xff, out)
  }

  def writeUInt16(input: Int, out: OutputStream): Unit = writeUInt16(input.toLong, out)

  def writeUInt16BigEndian(input: Long, out: OutputStream): Unit = {
    writeUInt8((input >> 8) & 0xff, out)
    writeUInt8((input) & 0xff, out)
  }

  def writeUInt16BigEndian(input: Long): Array[Byte] = {
    val out = new ByteArrayOutputStream(2)
    writeUInt16BigEndian(input, out)
    out.toByteArray
  }

  def writeUInt16BigEndian(input: Int): Array[Byte] = writeUInt16BigEndian(input.toLong)

  def uint32(a: Int, b: Int, c: Int, d: Int): Long = ((a & 0xffl) << 0) | ((b & 0xffl) << 8) | ((c & 0xffl) << 16) | ((d & 0xffl) << 24)

  def uint32(blob: Seq[Byte]): Long = uint32(blob(0), blob(1), blob(2), blob(3))

  def uint32(input: InputStream): Long = {
    val blob = new Array[Byte](4)
    input.read(blob)
    uint32(blob)
  }

  def writeUInt32(input: Long, out: OutputStream): Unit = {
    writeUInt8((input) & 0xff, out)
    writeUInt8((input >>> 8) & 0xff, out)
    writeUInt8((input >>> 16) & 0xff, out)
    writeUInt8((input >>> 24) & 0xff, out)
  }

  def writeUInt32(input: Int, out: OutputStream): Unit = writeUInt32(input.toLong, out)

  def writeUInt32(input: Int): Array[Byte] = {
    val out = new ByteArrayOutputStream()
    writeUInt32(input, out)
    out.toByteArray
  }

  def writeUInt32(input: Long): Array[Byte] = {
    val out = new ByteArrayOutputStream(4)
    writeUInt32(input, out)
    out.toByteArray
  }

  def writeUInt32BigEndian(input: Long, out: OutputStream): Unit = {
    writeUInt8((input >>> 24) & 0xff, out)
    writeUInt8((input >>> 16) & 0xff, out)
    writeUInt8((input >>> 8) & 0xff, out)
    writeUInt8((input) & 0xff, out)
  }

  def writeUInt32BigEndian(input: Long): Array[Byte] = {
    val out = new ByteArrayOutputStream(4)
    writeUInt32BigEndian(input, out)
    out.toByteArray
  }

  def writeUInt32BigEndian(input: Int): Array[Byte] = writeUInt32BigEndian(input.toLong)

  def uint64(a: Int, b: Int, c: Int, d: Int, e: Int, f: Int, g: Int, h: Int): Long = ((a & 0xffl) << 0) | ((b & 0xffl) << 8) | ((c & 0xffl) << 16) | ((d & 0xffl) << 24) | ((e & 0xffl) << 32) | ((f & 0xffl) << 40) | ((g & 0xffl) << 48) | ((h & 0xffl) << 56)

  def uint64(blob: Seq[Byte]): Long = uint64(blob(0), blob(1), blob(2), blob(3), blob(4), blob(5), blob(6), blob(7))

  def uint64(input: InputStream): Long = uint64(input.read(), input.read(), input.read(), input.read(), input.read(), input.read(), input.read(), input.read())

  def writeUInt64(input: Long, out: OutputStream): Unit = {
    writeUInt8((input) & 0xff, out)
    writeUInt8((input >>> 8) & 0xff, out)
    writeUInt8((input >>> 16) & 0xff, out)
    writeUInt8((input >>> 24) & 0xff, out)
    writeUInt8((input >>> 32) & 0xff, out)
    writeUInt8((input >>> 40) & 0xff, out)
    writeUInt8((input >>> 48) & 0xff, out)
    writeUInt8((input >>> 56) & 0xff, out)
  }

  def writeUInt64(input: Long): Array[Byte] = {
    val out = new ByteArrayOutputStream(8)
    writeUInt64(input, out)
    out.toByteArray
  }

  def writeUInt64(input: Int, out: OutputStream): Unit = writeUInt64(input.toLong, out)

  def varint(blob: Array[Byte]): Long = varint(new ByteArrayInputStream(blob))

  def varint(input: InputStream): Long = input.read() match {
    case value if value < 0xfd => value
    case 0xfd => uint16(input)
    case 0xfe => uint32(input)
    case 0xff => uint64(input)
  }

  def writeVarint(input: Int, out: OutputStream): Unit = writeVarint(input.toLong, out)

  def writeVarint(input: Long, out: OutputStream): Unit = {
    if (input < 0xfdL) writeUInt8(input, out)
    else if (input < 65535L) {
      writeUInt8(0xfdL, out)
      writeUInt16(input, out)
    }
    else if (input < 1048576L) {
      writeUInt8(0xfeL, out)
      writeUInt32(input, out)
    }
    else {
      writeUInt8(0xffL, out)
      writeUInt64(input, out)
    }
  }

  def bytes(input: InputStream, size: Long): Array[Byte] = bytes(input, size.toInt)

  def bytes(input: InputStream, size: Int): Array[Byte] = {
    val blob = new Array[Byte](size)
    if (size > 0) {
      val count = input.read(blob)
      if (count < size) throw new IOException("not enough data to read from")
    }
    blob
  }

  def varstring(input: InputStream): String = {
    val length = varint(input)
    new String(bytes(input, length), "UTF-8")
  }

  def writeVarstring(input: String, out: OutputStream) = {
    writeVarint(input.length, out)
    out.write(input.getBytes("UTF-8"))
  }

  def hash(input: InputStream): Array[Byte] = bytes(input, 32) // a hash is always 256 bits

  def script(input: InputStream): BinaryData = {
    val length = varint(input) // read size
    bytes(input, length.toInt) // read bytes
  }

  //def writeScript(input: BinaryData, out: OutputStream): Unit = writeScript(input.toArray, out)

  def writeScript(input: Array[Byte], out: OutputStream): Unit = {
    writeVarint(input.length.toLong, out)
    out.write(input)
  }

  implicit val txInSer = TxIn
  implicit val txOutSer = TxOut
  implicit val scriptWitnessSer = ScriptWitness
  implicit val txSer = Transaction
  implicit val networkAddressWithTimestampSer = NetworkAddressWithTimestamp
  implicit val inventoryVectorOutSer = InventoryVector

  def readCollection[T](input: InputStream, maxElement: Option[Int], protocolVersion: Long)(implicit ser: BtcMessage[T]) : Seq[T] =
    readCollection(input, ser.read, maxElement, protocolVersion)

  def readCollection[T](input: InputStream, protocolVersion: Long)(implicit ser: BtcMessage[T]): Seq[T] =
    readCollection(input, None, protocolVersion)(ser)

  def readCollection[T](input: InputStream, reader: (InputStream, Long) => T, maxElement: Option[Int], protocolVersion: Long) : Seq[T] = {
    val count = varint(input)
    maxElement.map(max => require(count <= max, "invalid length"))
    val items = ArrayBuffer.empty[T]
    for (i <- 1L to count) {
      items += reader(input, protocolVersion)
    }
    items.toSeq
  }

  def readCollection[T](input: InputStream, reader: (InputStream, Long) => T, protocolVersion: Long) : Seq[T] = readCollection(input, reader, None, protocolVersion)

  def writeCollection[T](seq: Seq[T], out: OutputStream, protocolVersion: Long)(implicit ser: BtcMessage[T]) : Unit = {
    writeVarint(seq.length, out)
    seq.map(t => ser.write(t, out, protocolVersion))
  }

  def writeCollection[T](seq: Seq[T], writer: (T, OutputStream, Long) => Unit, out: OutputStream, protocolVersion: Long) : Unit = {
    writeVarint(seq.length, out)
    seq.map(t => writer(t, out, protocolVersion))
  }
}

import Protocol._

trait BtcMessage[T] {
  /**
   * write a message to a stream
    *
    * @param t message
   * @param out output stream
   */
  def write(t: T, out: OutputStream, protocolVersion: Long): Unit

  def write(t:T, out: OutputStream): Unit = write(t, out, PROTOCOL_VERSION)

  /**
   * write a message to a byte array
    *
    * @param t message
   * @return a serialized message
   */
  def write(t: T, protocolVersion: Long): Array[Byte] = {
    val out = new ByteArrayOutputStream()
    write(t, out, protocolVersion)
    out.toByteArray
  }

  def write(t: T): Seq[Byte] = write(t, PROTOCOL_VERSION)

    /**
   * read a message from a stream
      *
      * @param in input stream
   * @return a deserialized message
   */
  def read(in: InputStream, protocolVersion: Long): T

  def read(in: InputStream): T = read(in, PROTOCOL_VERSION)

  /**
   * read a message from a byte array
    *
    * @param in serialized message
   * @return a deserialized message
   */
  def read(in: Seq[Byte], protocolVersion: Long): T = read(new ByteArrayInputStream(in.toArray), protocolVersion)

  def read(in: Seq[Byte]): T = read(in, PROTOCOL_VERSION)

  /**
   * read a message from a hex string
    *
    * @param in message binary data in hex format
   * @return a deserialized message of type T
   */
  def read(in: String, protocolVersion: Long): T = read(fromHexString(in), protocolVersion)

  def read(in: String): T = read(in, PROTOCOL_VERSION)

  def validate(t: T): Unit = {}
}

object BlockHeader extends BtcMessage[BlockHeader] {
  override def read(input: InputStream, protocolVersion: Long): BlockHeader = {
    val version = uint32(input)
    val hashPreviousBlock = hash(input)
    val hashMerkleRoot = hash(input)
    val time = uint32(input)
    val bits = uint32(input)
    val nonce = uint32(input)
    BlockHeader(version, hashPreviousBlock, hashMerkleRoot, time, bits, nonce)
  }

  override def write(input: BlockHeader, out: OutputStream, protocolVersion: Long) = {
    writeUInt32(input.version, out)
    out.write(input.hashPreviousBlock)
    out.write(input.hashMerkleRoot)
    writeUInt32(input.time, out)
    writeUInt32(input.bits, out)
    writeUInt32(input.nonce, out)
  }

  def getDifficulty(header: BlockHeader): BigInteger = {
    val nsize = header.bits >> 24
    val isneg = header.bits & 0x00800000
    val nword = header.bits & 0x007fffff
    val result = if (nsize <= 3)
      BigInteger.valueOf(nword).shiftRight(8 * (3 - nsize.toInt))
    else
      BigInteger.valueOf(nword).shiftLeft(8 * (nsize.toInt - 3))
    if (isneg != 0) result.negate() else result
  }
}

/**
 *
 * @param version Block version information, based upon the software version creating this block
 * @param hashPreviousBlock The hash value of the previous block this particular block references. Please not that
 *                          this hash is not reversed (as opposed to Block.hash)
 * @param hashMerkleRoot The reference to a Merkle tree collection which is a hash of all transactions related to this block
 * @param time A timestamp recording when this block was created (Will overflow in 2106[2])
 * @param bits The calculated difficulty target being used for this block
 * @param nonce The nonce used to generate this block… to allow variations of the header and compute different hashes
 */
case class BlockHeader(version: Long, hashPreviousBlock: BinaryData, hashMerkleRoot: BinaryData, time: Long, bits: Long, nonce: Long) {
  require(hashPreviousBlock.length == 32, "hashPreviousBlock must be 32 bytes")
  require(hashMerkleRoot.length == 32, "hashMerkleRoot must be 32 bytes")
  lazy val hash: BinaryData = Crypto.hash256(BlockHeader.write(this))
}

/**
 * see https://en.bitcoin.it/wiki/Protocol_specification#Merkle_Trees
 */
object MerkleTree {
  def computeRoot(tree: Seq[Seq[Byte]]): BinaryData = tree.length match {
    case 1 => tree(0)
    case n if n % 2 != 0 => computeRoot(tree :+ tree.last) // append last element again
    case _ => computeRoot(tree.grouped(2).map(a => Crypto.hash256(a(0) ++ a(1))).toSeq)
  }
}

object Block extends BtcMessage[Block] {
  override def read(input: InputStream, protocolVersion: Long): Block = {
    val raw = bytes(input, 80)
    val header = BlockHeader.read(raw)
    Block(header, readCollection[Transaction](input, protocolVersion))
  }

  override def write(input: Block, out: OutputStream, protocolVersion: Long) = {
    BlockHeader.write(input.header, out)
    writeCollection(input.tx, out, protocolVersion)
  }

  override def validate(input: Block): Unit = {
    BlockHeader.validate(input.header)
    require(util.Arrays.equals(input.header.hashMerkleRoot, MerkleTree.computeRoot(input.tx.map(_.hash.toSeq))), "invalid block:  merkle root mismatch")
    require(input.tx.map(_.txid).toSet.size == input.tx.size, "invalid block: duplicate transactions")
    input.tx.map(Transaction.validate)
  }

  // genesis blocks
  val LivenetGenesisBlock = {
    val script = OP_PUSHDATA(writeUInt32(486604799L)) :: OP_PUSHDATA(writeUInt8(4)) :: OP_PUSHDATA("The Times 03/Jan/2009 Chancellor on brink of second bailout for banks".getBytes("UTF-8")) :: Nil
    val scriptPubKey = OP_PUSHDATA("04678afdb0fe5548271967f1a67130b7105cd6a828e03909a67962e0ea1f61deb649f6bc3f4cef38c4f35504e51ec112de5c384df7ba0b8d578a4c702b6bf11d5f") :: OP_CHECKSIG :: Nil
    Block(
      BlockHeader(version = 1, hashPreviousBlock = Hash.Zeroes, hashMerkleRoot = "3ba3edfd7a7b12b27ac72c3e67768f617fc81bc3888a51323a9fb8aa4b1e5e4a", time = 1231006505, bits = 0x1d00ffff, nonce = 2083236893),
      List(
        Transaction(version = 1,
          txIn = List(TxIn.coinbase(script)),
          txOut = List(TxOut(amount = 50 btc, publicKeyScript = scriptPubKey)),
          lockTime = 0))
    )
  }

  val TestnetGenesisBlock = LivenetGenesisBlock.copy(header = LivenetGenesisBlock.header.copy(time = 1296688602, nonce = 414098458))

  val RegtestGenesisBlock = LivenetGenesisBlock.copy(header = LivenetGenesisBlock.header.copy(bits = 0x207fffffL, nonce = 2, time = 1296688602))

  val SegnetGenesisBlock = LivenetGenesisBlock.copy(header = LivenetGenesisBlock.header.copy(bits = 503447551, time = 1452831101, nonce = 0))

  /**
   * Proof of work: hash(block) <= target difficulty
    *
    * @param block
   * @return true if the input block validates its expected proof of work
   */
  def checkProofOfWork(block: Block): Boolean = {
    val (target, _, _) = decodeCompact(block.header.bits)
    val hash = new BigInteger(1, block.blockId.toArray)
    hash.compareTo(target) <= 0
  }
}

/**
 * Bitcoin block
  *
  * @param header block header
 * @param tx transactions
 */
case class Block(header: BlockHeader, tx: Seq[Transaction]) {
  lazy val hash = header.hash

  // hash is reversed here (same as tx id)
  lazy val blockId = BinaryData(hash.reverse)
}

object Message extends BtcMessage[Message] {
  val MagicMain = 0xD9B4BEF9L
  val MagicTestNet = 0xDAB5BFFAL
  val MagicTestnet3 = 0x0709110BL
  val MagicNamecoin = 0xFEB4BEF9L
  val MagicSegnet =  0xC4A1ABDC

  override def read(in: InputStream, protocolVersion: Long): Message = {
    val magic = uint32(in)
    val buffer = new Array[Byte](12)
    in.read(buffer)
    val buffer1 = buffer.takeWhile(_ != 0)
    val command = new String(buffer1, "ISO-8859-1")
    val length = uint32(in)
    require(length < 2000000, "invalid payload length")
    val checksum = uint32(in)
    val payload = new Array[Byte](length.toInt)
    in.read(payload)
    require(checksum == uint32(Crypto.hash256(payload).take(4)), "invalid checksum")
    Message(magic, command, payload)
  }

  override def write(input: Message, out: OutputStream, protocolVersion: Long) = {
    writeUInt32(input.magic, out)
    val buffer = new Array[Byte](12)
    input.command.getBytes("ISO-8859-1").copyToArray(buffer)
    out.write(buffer)
    writeUInt32(input.payload.length, out)
    val checksum = Crypto.hash256(input.payload).take(4).toArray
    out.write(checksum)
    out.write(input.payload)
  }
}

/**
 * Bitcoin message exchanged by nodes over the network
  *
  * @param magic Magic value indicating message origin network, and used to seek to next message when stream state is unknown
 * @param command ASCII string identifying the packet content, NULL padded (non-NULL padding results in packet rejected)
 * @param payload The actual data
 */
case class Message(magic: Long, command: String, payload: BinaryData) {
  require(command.length <= 12)
}

object NetworkAddressWithTimestamp extends BtcMessage[NetworkAddressWithTimestamp] {
  override def read(in: InputStream, protocolVersion: Long): NetworkAddressWithTimestamp = {
    val time = uint32(in)
    val services = uint64(in)
    val raw = new Array[Byte](16)
    in.read(raw)
    val address = InetAddress.getByAddress(raw)
    val port = uint16BigEndian(in)
    NetworkAddressWithTimestamp(time, services, address, port)
  }

  override def write(input: NetworkAddressWithTimestamp, out: OutputStream, protocolVersion: Long) = {
    writeUInt32(input.time, out)
    writeUInt64(input.services, out)
    input.address match {
      case _: Inet4Address => out.write(fromHexString("00000000000000000000ffff"))
      case _: Inet6Address => ()
    }
    out.write(input.address.getAddress)
    writeUInt16BigEndian(input.port, out)
  }
}

case class NetworkAddressWithTimestamp(time: Long, services: Long, address: InetAddress, port: Long)

object NetworkAddress extends BtcMessage[NetworkAddress] {
  override def read(in: InputStream, protocolVersion: Long): NetworkAddress = {
    val services = uint64(in)
    val raw = new Array[Byte](16)
    in.read(raw)
    val address = InetAddress.getByAddress(raw)
    val port = uint16BigEndian(in)
    NetworkAddress(services, address, port)
  }

  override def write(input: NetworkAddress, out: OutputStream, protocolVersion: Long) = {
    writeUInt64(input.services, out)
    input.address match {
      case _: Inet4Address => out.write(fromHexString("00000000000000000000ffff"))
      case _: Inet6Address => ()
    }
    out.write(input.address.getAddress)
    writeUInt16BigEndian(input.port, out) // wtf ?? why BE there ?
  }
}

case class NetworkAddress(services: Long, address: InetAddress, port: Long)

object Version extends BtcMessage[Version] {
  override def read(in: InputStream, protocolVersion: Long): Version = {
    val version = uint32(in)
    val services = uint64(in)
    val timestamp = uint64(in)
    val addr_recv = NetworkAddress.read(in)
    val addr_from = NetworkAddress.read(in)
    val nonce = uint64(in)
    val length = varint(in)
    val buffer = new Array[Byte](length.toInt)
    in.read(buffer)
    val user_agent = new String(buffer, "ISO-8859-1")
    val start_height = uint32(in)
    val relay = if (uint8(in) == 0) false else true
    Version(version, services, timestamp, addr_recv, addr_from, nonce, user_agent, start_height, relay)
  }

  override def write(input: Version, out: OutputStream, protocolVersion: Long) = {
    writeUInt32(input.version, out)
    writeUInt64(input.services, out)
    writeUInt64(input.timestamp, out)
    NetworkAddress.write(input.addr_recv, out)
    NetworkAddress.write(input.addr_from, out)
    writeUInt64(input.nonce, out)
    writeVarint(input.user_agent.length, out)
    out.write(input.user_agent.getBytes("ISO-8859-1"))
    writeUInt32(input.start_height, out)
    writeUInt8(if (input.relay) 1 else 0, out)
  }
}

/**
 *
 * @param version Identifies protocol version being used by the node
 * @param services bitfield of features to be enabled for this connection
 * @param timestamp standard UNIX timestamp in seconds
 * @param addr_recv The network address of the node receiving this message
 * @param addr_from The network address of the node emitting this message
 * @param nonce Node random nonce, randomly generated every time a version packet is sent. This nonce is used to detect
 *              connections to self.
 * @param user_agent User Agent
 * @param start_height The last block received by the emitting node
 * @param relay Whether the remote peer should announce relayed transactions or not, see BIP 0037,
 *              since version >= 70001
 */
case class Version(version: Long, services: Long, timestamp: Long, addr_recv: NetworkAddress, addr_from: NetworkAddress, nonce: Long, user_agent: String, start_height: Long, relay: Boolean)

object Addr extends BtcMessage[Addr] {
  override def write(t: Addr, out: OutputStream, protocolVersion: Long): Unit = writeCollection(t.addresses, out, protocolVersion)

  override def read(in: InputStream, protocolVersion: Long): Addr =
    Addr(readCollection[NetworkAddressWithTimestamp](in, Some(1000), protocolVersion))
}

case class Addr(addresses: Seq[NetworkAddressWithTimestamp])

object InventoryVector extends BtcMessage[InventoryVector] {
  val ERROR = 0L
  val MSG_TX = 1L
  val MSG_BLOCK = 2L

  override def write(t: InventoryVector, out: OutputStream, protocolVersion: Long): Unit = {
    writeUInt32(t.`type`, out)
    out.write(t.hash)
  }

  override def read(in: InputStream, protocolVersion: Long): InventoryVector = InventoryVector(uint32(in), hash(in))
}

case class InventoryVector(`type`: Long, hash: BinaryData) {
  require(hash.length == 32, "invalid hash length")
}

object Inventory extends BtcMessage[Inventory] {
  override def write(t: Inventory, out: OutputStream, protocolVersion: Long): Unit = writeCollection(t.inventory, out, protocolVersion)

  override def read(in: InputStream, protocolVersion: Long): Inventory = Inventory(readCollection[InventoryVector](in, Some(1000), protocolVersion))
}

case class Inventory(inventory: Seq[InventoryVector])

object Getheaders extends BtcMessage[Getheaders] {
  override def write(t: Getheaders, out: OutputStream, protocolVersion: Long): Unit = {
    writeUInt32(t.version, out)
    writeCollection(t.locatorHashes, (h:BinaryData, o:OutputStream, _: Long) => o.write(h), out, protocolVersion)
    out.write(t.stopHash)
  }

  override def read(in: InputStream, protocolVersion: Long): Getheaders = {
    Getheaders(version = uint32(in), locatorHashes = readCollection[BinaryData](in, (i: InputStream, _: Long) => BinaryData(hash(i)), protocolVersion), stopHash = hash(in))
  }
}

case class Getheaders(version: Long, locatorHashes: Seq[BinaryData], stopHash: BinaryData) {
  locatorHashes.map(h => require(h.length == 32))
  require(stopHash.length == 32)
}

object Headers extends BtcMessage[Headers] {
  override def write(t: Headers, out: OutputStream, protocolVersion: Long): Unit = {
    writeCollection(t.headers, (t:BlockHeader, o:OutputStream, v: Long) => {
      BlockHeader.write(t, o, v)
      writeVarint(0, o)
    }, out, protocolVersion)
  }

  override def read(in: InputStream, protocolVersion: Long): Headers = {
    Headers(readCollection(in, (i: InputStream, v: Long) => {
      val header = BlockHeader.read(i, v)
      val dummy = varint(in)
      require(dummy == 0, s"header in headers message ends with $dummy, should be 0 instead")
      header
    }, protocolVersion))
  }
}

case class Headers(headers: Seq[BlockHeader])

object Getblocks extends BtcMessage[Getblocks] {
  override def write(t: Getblocks, out: OutputStream, protocolVersion: Long): Unit = {
    writeUInt32(t.version, out)
    writeCollection(t.locatorHashes, (h: BinaryData, o:OutputStream, _: Long) => o.write(h), out, protocolVersion)
    out.write(t.stopHash)
  }

  override def read(in: InputStream, protocolVersion: Long): Getblocks = {
    Getblocks(version =  uint32(in), locatorHashes = readCollection(in, (i: InputStream, _: Long) => BinaryData(hash(i)), protocolVersion), stopHash = hash(in))
  }
}

case class Getblocks(version: Long, locatorHashes: Seq[BinaryData], stopHash: BinaryData) {
  locatorHashes.map(h => require(h.length == 32))
  require(stopHash.length == 32)
}

object Getdata extends BtcMessage[Getdata] {
  override def write(t: Getdata, out: OutputStream, protocolVersion: Long): Unit = writeCollection(t.inventory, out, protocolVersion)

  override def read(in: InputStream, protocolVersion: Long): Getdata = Getdata(readCollection[InventoryVector](in, protocolVersion))
}

case class Getdata(inventory: Seq[InventoryVector])

object Reject extends BtcMessage[Reject] {
  override def write(t: Reject, out: OutputStream, protocolVersion: Long): Unit = {
    writeVarstring(t.message, out)
    writeUInt8(t.code, out)
    writeVarstring(t.reason, out)
  }

  override def read(in: InputStream, protocolVersion: Long): Reject = {
    Reject(message = varstring(in), code = uint8(in), reason =  varstring(in), Array.empty[Byte])
  }
}

case class Reject(message: String, code: Long, reason: String, data: BinaryData)