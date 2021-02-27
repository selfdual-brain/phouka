package com.selfdualbrain.data_structures

import scala.collection._

class MutableCloneableMultidictionary[K,V] private (elems: mutable.Map[K, mutable.Set[V]])
  extends collection.MultiDict[K, V]
    with mutable.Iterable[(K, V)]
    with IterableOps[(K, V), mutable.Iterable, MutableCloneableMultidictionary[K, V]]
    with collection.MultiDictOps[K, V, MutableCloneableMultidictionary, MutableCloneableMultidictionary[K, V]]
    with mutable.Growable[(K, V)]
    with mutable.Shrinkable[(K, V)]
    with CloningSupport[MutableCloneableMultidictionary[K,V]] {

  override def multiDictFactory: MapFactory[MutableCloneableMultidictionary] = MutableCloneableMultidictionary
  override protected def fromSpecific(coll: IterableOnce[(K, V)]): MutableCloneableMultidictionary[K, V] = multiDictFactory.from(coll)
  override protected def newSpecificBuilder: mutable.Builder[(K, V), MutableCloneableMultidictionary[K, V]] = multiDictFactory.newBuilder[K, V]
  override def empty: MutableCloneableMultidictionary[K, V] = multiDictFactory.empty
  override def withFilter(p: ((K, V)) => Boolean): MultiDictOps.WithFilter[K, V, mutable.Iterable, MutableCloneableMultidictionary] =
    new MultiDictOps.WithFilter(this, p)
  override def knownSize: Int = -1

  def sets: collection.Map[K, collection.Set[V]] = elems

  def addOne(elem: (K, V)): this.type = {
    val (k, v) = elem
    elems.updateWith(k) {
      case None     => Some(mutable.Set(v))
      case Some(vs) => Some(vs += v)
    }
    this
  }

  def subtractOne(elem: (K, V)): this.type = {
    val (k, v) = elem
    elems.updateWith(k) {
      case Some(vs) =>
        vs -= v
        if (vs.nonEmpty) Some(vs) else None
      case None => None
    }
    this
  }

  /**
    * Removes all the entries associated with the given `key`
    * @return the collection itself
    */
  def removeKey(key: K): this.type = {
    elems -= key
    this
  }

  /** Alias for `removeKey` */
  @`inline` final def -*= (key: K): this.type = removeKey(key)

  def clear(): Unit = elems.clear()

  override def createDetachedCopy(): MutableCloneableMultidictionary[K,V] = {
    val clonedElems = new mutable.HashMap[K, mutable.Set[V]]
    for ((k,v) <- elems)
      clonedElems += k -> v.clone()
    new MutableCloneableMultidictionary(clonedElems)
  }
}

object MutableCloneableMultidictionary extends MapFactory[MutableCloneableMultidictionary] {

  def empty[K, V]: MutableCloneableMultidictionary[K, V] = new MutableCloneableMultidictionary(mutable.Map.empty)

  def from[K, V](source: IterableOnce[(K, V)]): MutableCloneableMultidictionary[K, V] = (newBuilder[K, V] ++= source).result()

  def newBuilder[K, V]: mutable.Builder[(K, V), MutableCloneableMultidictionary[K, V]] = new mutable.GrowableBuilder[(K, V), MutableCloneableMultidictionary[K, V]](empty)

}
