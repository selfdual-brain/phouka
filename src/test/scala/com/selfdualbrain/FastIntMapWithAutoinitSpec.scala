package com.selfdualbrain

import com.selfdualbrain.data_structures.FastIntMapWithAutoinit

import scala.util.Random

class FastIntMapWithAutoinitSpec extends BaseSpec {

  case class User(name: String)

  val random: Random = new Random(42) //using fixed seed so to have repeatable behaviour of unit tests

  def createMapInstanceForTesting = new FastIntMapWithAutoinit[User](100)(i => User(s"auto-$i"))

  "FastIntMapWithAutoinit" should "return empty iterator when just initialized" in {
    val map = createMapInstanceForTesting
    var counter: Int = 0
    for ((k,v) <- map)
      counter += 1
    counter shouldBe 0
  }

  it should "be empty when just initialized" in {
    val map = new FastIntMapWithAutoinit[User](100)(i => User(s"auto-$i"))
    map.size shouldBe 0
  }

  it should "correctly handle adding first element" in {
    val map = createMapInstanceForTesting
    map += 0 -> User("test")
    map.size shouldBe 1
    map(0) shouldEqual User("test")
  }

  it should "handle mixed adds and removals" in {
    val map = createMapInstanceForTesting
    map += 3 -> User("test-3")
    map += 13 -> User("test-13")
    map += 7 -> User("test-7")
    map += 5 -> User("test-5")
    map += 3 -> User("test-3-updated")
    map += 8 -> User("test-8")
    map += 14 -> User("test-14")

    val coll1: Seq[(Int, User)] = map.iterator.toSeq
    val coll2: Seq[(Int, User)] = Seq(
      0 -> User("auto-0"),
      1 -> User("auto-1"),
      2 -> User("auto-2"),
      3 -> User("test-3-updated"),
      4 -> User("auto-4"),
      5 -> User("test-5"),
      6 -> User("auto-6"),
      7 -> User("test-7"),
      8 -> User("test-8"),
      9 -> User("auto-9"),
      10 -> User("auto-10"),
      11-> User("auto-11"),
      12 -> User("auto-12"),
      13 -> User("test-13"),
      14 -> User("test-14")
    )

    coll1 should contain theSameElementsInOrderAs coll2
  }

}
