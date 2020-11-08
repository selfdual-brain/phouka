package com.selfdualbrain

import com.selfdualbrain.data_structures.LayeredMap

class LayeredMapSpec extends BaseSpec {
  case class Car(id: Int)
  case class User(name: String)

  "layered map" should "correctly iterate elements when is empty" in {
    val map = new LayeredMap[Car, User](car => car.id)
    var counter: Int = 0
    for ((k,v) <- map)
      counter += 1
    counter shouldBe 0
  }

}
