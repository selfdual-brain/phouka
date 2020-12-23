package com.selfdualbrain

import com.selfdualbrain.data_structures.LayeredMap

import scala.collection.mutable
import scala.util.Random

class LayeredMapSpec extends BaseSpec {
  case class Car(id: Int) extends Ordered[Car] {
    override def compare(that: Car): Int = this.id.compareTo(that.id)
  }
  case class User(name: String)

  val random: Random = new Random(42) //using fixed seed so to have repeatable behaviour of unit tests

  class MapOperationsSandbox {
    val layeredMap = new LayeredMap[Car, User](car => car.id % 7)
    val referenceMap = new mutable.HashMap[Car, User]
    val keys = new mutable.HashSet[Car]

    def doSomeAdding(numberOfIterations: Int): Unit = {
      for (i <- 1 to numberOfIterations) {
        val tuple = Car(math.abs(random.nextInt(200))) -> User(random.nextString(5))
        keys += tuple._1
        layeredMap += tuple
        referenceMap += tuple
        layeredMap.keys.toSeq.sorted shouldEqual referenceMap.keys.toSeq.sorted
      }
    }

    def doSomeMixedOperations(numberOfIterations: Int): Unit = {
      for (i <- 1 to numberOfIterations) {
        if (random.nextBoolean()) {
          val tuple = Car(math.abs(random.nextInt(200))) -> User(random.nextString(5))
          keys += tuple._1
          layeredMap += tuple
          referenceMap += tuple
        } else {
          val selectedKey: Car = keys.head
          layeredMap.remove(selectedKey)
          referenceMap.remove(selectedKey)
          keys.remove(selectedKey)
        }
        layeredMap.keys.toSeq.sorted shouldEqual referenceMap.keys.toSeq.sorted
      }
    }
  }

  "layered map" should "correctly iterate elements when is empty" in {
    val map = new LayeredMap[Car, User](car => car.id)
    var counter: Int = 0
    for ((k,v) <- map)
      counter += 1
    counter shouldBe 0
  }

  it should "behave like a normal mutable map" in {
    val sandbox = new MapOperationsSandbox
    sandbox.doSomeAdding(100)
    sandbox.doSomeMixedOperations(100)
  }

  it should "correctly prune levels" in {
    val sandbox = new MapOperationsSandbox

    //build-up some non-trivial contents
    sandbox.doSomeAdding(100)
    sandbox.doSomeMixedOperations(100)

    //prune levels 0,1,2,3
    val keysAtLevels0123 = sandbox.referenceMap.keys.filter (car => car.id % 7 < 3)
    sandbox.layeredMap.pruneLevelsBelow(3)
    sandbox.referenceMap --= keysAtLevels0123
    sandbox.keys --= keysAtLevels0123

    //mix adding, removing and overriding keys (could possibly fail, if pruning did some mess inside)
    sandbox.doSomeMixedOperations(1)
  }

}
