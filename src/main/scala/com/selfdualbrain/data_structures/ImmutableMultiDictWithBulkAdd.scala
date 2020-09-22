package com.selfdualbrain.data_structures

import scala.collection.immutable.{Iterable, Map, Set}
import scala.collection.mutable.{Builder, ImmutableBuilder}
import scala.collection.{IterableOnce, MapFactory, MultiDictOps, mutable}

/**
  * Extended implementation of immutable MultiDict (copied from Scala standard lib)
  * We add one extra feature - an ability to add a key->collection pair (i.e. a "bulk update" feature).
  * This is just a performance optimization.
  *
  * @tparam K the type of keys
  * @tparam V the type of values
  */
class ImmutableMultiDictWithBulkAdd[K, V] private(elems: Map[K, Set[V]])
  extends collection.MultiDict[K, V]
    with Iterable[(K, V)]
    with collection.MultiDictOps[K, V, ImmutableMultiDictWithBulkAdd, ImmutableMultiDictWithBulkAdd[K, V]]
    with collection.IterableOps[(K, V), Iterable, ImmutableMultiDictWithBulkAdd[K, V]] {

  def sets: Map[K, Set[V]] = elems

  override def multiDictFactory: MapFactory[ImmutableMultiDictWithBulkAdd] = ImmutableMultiDictWithBulkAdd
  override protected def fromSpecific(coll: IterableOnce[(K, V)]): ImmutableMultiDictWithBulkAdd[K, V] = multiDictFactory.from(coll)
  override protected def newSpecificBuilder: mutable.Builder[(K, V), ImmutableMultiDictWithBulkAdd[K, V]] = multiDictFactory.newBuilder[K, V]
  override def empty: ImmutableMultiDictWithBulkAdd[K, V] = multiDictFactory.empty
  override def withFilter(p: ((K, V)) => Boolean): MultiDictOps.WithFilter[K, V, Iterable, ImmutableMultiDictWithBulkAdd] =
    new MultiDictOps.WithFilter(this, p)

  /**
    * @return a new multidict that contains all the entries of this multidict
    *         excepted the entry defined by the given `key` and `value`
    */
  def remove(key: K, value: V): ImmutableMultiDictWithBulkAdd[K, V] =
    new ImmutableMultiDictWithBulkAdd(elems.updatedWith(key) {
      case Some(vs) =>
        val updatedVs = vs - value
        if (updatedVs.nonEmpty) Some(updatedVs) else None
      case None => None
    })

  /** Alias for `remove` */
  @`inline` final def - (kv: (K, V)): ImmutableMultiDictWithBulkAdd[K, V] = remove(kv._1, kv._2)

  /**
    * @return a new multidict that contains all the entries of this multidict
    *         excepted those associated with the given `key`
    */
  def removeKey(key: K): ImmutableMultiDictWithBulkAdd[K, V] = new ImmutableMultiDictWithBulkAdd(elems - key)

  /** Alias for `removeKey` */
  @`inline` final def -* (key: K): ImmutableMultiDictWithBulkAdd[K, V] = removeKey(key)

  /**
    * @return a new multidict that contains all the entries of this multidict
    *         and the entry defined by the given `key` and `value`
    */
  def add(key: K, value: V): ImmutableMultiDictWithBulkAdd[K, V] =
    new ImmutableMultiDictWithBulkAdd(elems.updatedWith(key) {
      case None     => Some(Set(value))
      case Some(vs) => Some(vs + value)
    })

  def bulkAdd(key: K, collection: Iterable[V]): ImmutableMultiDictWithBulkAdd[K, V] =
    new ImmutableMultiDictWithBulkAdd(elems.updatedWith(key) {
      case None     => Some(collection.toSet)
      case Some(vs) => Some(vs ++ collection)
    })

  /** Alias for `add` */
  @`inline` final def + (kv: (K, V)): ImmutableMultiDictWithBulkAdd[K, V] = add(kv._1, kv._2)

}

object ImmutableMultiDictWithBulkAdd extends MapFactory[ImmutableMultiDictWithBulkAdd] {

  def empty[K, V]: ImmutableMultiDictWithBulkAdd[K, V] = new ImmutableMultiDictWithBulkAdd[K, V](Map.empty)

  def from[K, V](source: IterableOnce[(K, V)]): ImmutableMultiDictWithBulkAdd[K, V] =
    source match {
      case mm: ImmutableMultiDictWithBulkAdd[K, V] => mm
      case _ => (newBuilder[K, V] ++= source).result()
    }

  def newBuilder[K, V]: mutable.Builder[(K, V), ImmutableMultiDictWithBulkAdd[K, V]] =
    new mutable.ImmutableBuilder[(K, V), ImmutableMultiDictWithBulkAdd[K, V]](empty[K, V]) {
      def addOne(elem: (K, V)): this.type = { elems = elems + elem; this }
    }

}
