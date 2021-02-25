package com.selfdualbrain

import com.selfdualbrain.data_structures.FastIntMap

import scala.collection.mutable
import scala.util.Random

class FastIntMapSpec extends BaseSpec {

  case class User(name: String)

  val random: Random = new Random(42) //using fixed seed so to have repeatable behaviour of unit tests

  class MapOperationsSandbox {
    val mapInstanceUnderTest = new FastIntMap[User]
    val referenceMap = new mutable.HashMap[Int, User]
    val keys = new mutable.HashSet[Int]

    def doSomeAdding(numberOfIterations: Int): Unit = {
      for (i <- 1 to numberOfIterations) {
        val tuple = random.nextInt(200) -> User(random.nextString(5))
        keys += tuple._1
        mapInstanceUnderTest += tuple
        referenceMap += tuple
        mapInstanceUnderTest.keys.toSeq.sorted shouldEqual referenceMap.keys.toSeq.sorted
        mapInstanceUnderTest.size shouldEqual referenceMap.size
      }
    }

    def doSomeMixedOperations(numberOfIterations: Int): Unit = {
      for (i <- 1 to numberOfIterations) {
        if (random.nextBoolean()) {
          val tuple = random.nextInt(200) -> User(random.nextString(5))
          keys += tuple._1
          mapInstanceUnderTest += tuple
          referenceMap += tuple
        } else {
          val selectedKey: Int = keys.head
          mapInstanceUnderTest.remove(selectedKey)
          referenceMap.remove(selectedKey)
          keys.remove(selectedKey)
        }
        mapInstanceUnderTest.keys.toSeq.sorted shouldEqual referenceMap.keys.toSeq.sorted
        mapInstanceUnderTest.size shouldEqual referenceMap.size
      }
    }
  }

  "FastIntMap" should "return empty iterator when just initialized" in {
    val map = new FastIntMap[User]
    var counter: Int = 0
    for ((k,v) <- map)
      counter += 1
    counter shouldBe 0
  }

  it should "be empty when just initialized" in {
    val map = new FastIntMap[User]
    map.size shouldBe 0
  }

  it should "behave like a normal mutable map while performing mixed adds and removals" in {
    val sandbox = new MapOperationsSandbox
    sandbox.doSomeAdding(100)
    sandbox.doSomeMixedOperations(100)
  }

  it should "return empty iterator after removing all elements" in {
    val sandbox = new MapOperationsSandbox
    sandbox.doSomeAdding(100)
    sandbox.doSomeMixedOperations(100)
    val keysSnapshot = sandbox.keys.toSeq
    for (key <- keysSnapshot)
      sandbox.mapInstanceUnderTest.remove(key)
    var counter: Int = 0
    for ((k,v) <- sandbox.mapInstanceUnderTest)
      counter += 1
    counter shouldBe 0
  }

}
