package fr.acinq.bitcoin

import java.io.DataInputStream

import org.junit.runner.RunWith
import org.scalatest.FlatSpec
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class MerkleTreeSpec extends FlatSpec {
  "MerkleTree" should "compute the root of a merkle tree" in {
    val stream = classOf[ProtocolSpec].getResourceAsStream("/block1.dat")
    val block = Block.read(new DataInputStream(stream))
    assert(toHexString(MerkleTree.computeRoot(block.tx)) === toHexString(block.header.hashMerkleRoot))
  }
}
