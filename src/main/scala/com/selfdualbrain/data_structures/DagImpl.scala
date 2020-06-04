package com.selfdualbrain.data_structures

import scala.collection.mutable

class DagImpl[Vertex](getTargets: Vertex => Iterable[Vertex]) extends InferredDag[Vertex] {
  type Buffer = mutable.ListBuffer[Vertex]

  private def empty: Buffer = mutable.ListBuffer.empty[Vertex]

  private val vertex2children: mutable.HashMap[Vertex, Buffer] = mutable.HashMap.empty
  private val verticesWithNoChildren: mutable.HashSet[Vertex] = mutable.HashSet.empty
  private val allVertices: mutable.HashSet[Vertex] = mutable.HashSet.empty

  override def contains(v: Vertex): Boolean = allVertices.contains(v)

  override def targetsOf(b: Vertex): Iterable[Vertex] =
    if (allVertices.contains(b))
      getTargets(b)
    else
      Nil

  override def sourcesOf(v: Vertex): Iterable[Vertex] = vertex2children.getOrElse(v, Nil)

  override def tips: Iterable[Vertex] = verticesWithNoChildren

  override def insert(v: Vertex): Boolean =
    if (allVertices.contains(v))
      false
    else {
      val missing = getTargets(v).filterNot(allVertices.contains)
      if (missing.nonEmpty)
        throw new RuntimeException(s"Dag.insert() failed - missing targets for vertex $v: ${missing.mkString(",")}")
      else {
        for (block <- getTargets(v)) {
          vertex2children.getOrElseUpdate(block, empty).append(v)
          val tmp = verticesWithNoChildren.remove(block)
        }
        vertex2children.update(v, empty)
        verticesWithNoChildren.add(v)
        allVertices.add(v)
      }
    }

  override def toposortTraverseFrom(v: Vertex): Iterator[Vertex] = this.toposortTraverseFrom(Seq(v))

  override def toposortTraverseFrom(coll: Iterable[Vertex]): Iterator[Vertex] = DirectedGraphUtils.breadthFirstTraverse(coll, this.targetsOf)
}
