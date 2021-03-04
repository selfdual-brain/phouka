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
    val mapInstanceUnderTest = new LayeredMap[Car, User](car => car.id % 7, 10, 100)
    val referenceMap = new mutable.HashMap[Car, User]
    val keys = new mutable.HashSet[Car]

    def doSomeAdding(numberOfIterations: Int): Unit = {
      for (i <- 1 to numberOfIterations) {
        val tuple = Car(random.nextInt(200)) -> User(random.nextString(5))
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
          val tuple = Car(random.nextInt(200)) -> User(random.nextString(5))
          keys += tuple._1
          mapInstanceUnderTest += tuple
          referenceMap += tuple
        } else {
          val selectedKey: Car = keys.head
          mapInstanceUnderTest.remove(selectedKey)
          referenceMap.remove(selectedKey)
          keys.remove(selectedKey)
        }
        mapInstanceUnderTest.keys.toSeq.sorted shouldEqual referenceMap.keys.toSeq.sorted
        mapInstanceUnderTest.size shouldEqual referenceMap.size
      }
    }
  }

  "layered map" should "return empty iterator when just initialized" in {
    val map = new LayeredMap[Car, User](car => car.id, 10, 100)
    var counter: Int = 0
    for ((k,v) <- map)
      counter += 1
    counter shouldBe 0
  }

  it should "be empty when just initialized" in {
    val map = new LayeredMap[Car, User](car => car.id, 10, 100)
    map.size shouldBe 0
  }

  it should "return empty iterator after pruning all levels" in {
    val sandbox = new MapOperationsSandbox
    sandbox.doSomeAdding(100)
    sandbox.doSomeMixedOperations(100)
    val levels: Set[Int] = sandbox.keys.map(car => car.id % 7).toSet
    val maxLevel: Int = levels.max
    sandbox.mapInstanceUnderTest.pruneLevelsBelow(maxLevel + 1)
    var counter: Int = 0
    for ((k,v) <- sandbox.mapInstanceUnderTest)
      counter += 1
    counter shouldBe 0
  }

  it should "behave like a normal mutable map while performing mixed adds and removals" in {
    val sandbox = new MapOperationsSandbox
    sandbox.doSomeAdding(100)
    sandbox.doSomeMixedOperations(100)
  }

  it should "correctly iterate over elements spanning across several levels" in {
    val sandbox = new MapOperationsSandbox
    sandbox.doSomeAdding(100)
    sandbox.doSomeMixedOperations(100)

    val sortedEntriesOfReferenceMap: Seq[(Car, User)] = sandbox.referenceMap.toSeq.sortBy{case (car, user) => car.id}
    val sortedEntriesOfLayeredMap: Seq[(Car, User)] = sandbox.mapInstanceUnderTest.toSeq.sortBy{case (car, user) => car.id}
    sortedEntriesOfReferenceMap should contain theSameElementsInOrderAs sortedEntriesOfLayeredMap
  }

  it should "correctly prune levels" in {
    val sandbox = new MapOperationsSandbox

    //build-up some non-trivial contents
    sandbox.doSomeAdding(100)
    sandbox.doSomeMixedOperations(100)

    //prune levels 0,1,2,3
    val keysAtLevels0123 = sandbox.referenceMap.keys.filter (car => car.id % 7 < 3)
    sandbox.mapInstanceUnderTest.pruneLevelsBelow(3)
    sandbox.referenceMap --= keysAtLevels0123
    sandbox.keys --= keysAtLevels0123

    //mix adding, removing and overriding keys (could possibly fail, if pruning did some mess inside)
    sandbox.doSomeMixedOperations(1)
  }

}
