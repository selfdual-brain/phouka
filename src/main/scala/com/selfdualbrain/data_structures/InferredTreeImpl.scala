package com.selfdualbrain.data_structures

import scala.collection.mutable

class InferredTreeImpl[B](genesis: B, getParent: B => Option[B]) extends InferredTree[B] {
  type Buffer = mutable.ListBuffer[B]

  private def empty: Buffer = mutable.ListBuffer.empty[B]

  private val vertex2children: mutable.HashMap[B, Buffer] = mutable.HashMap.empty
  private val verticesWithNoChildren: mutable.HashSet[B] = mutable.HashSet.empty
  private val allVertices: mutable.HashSet[B] = mutable.HashSet.empty

  verticesWithNoChildren.add(genesis)
  allVertices.add(genesis)

  override def parent(b: B): Option[B] = getParent(b)

  override def children(b: B): Iterable[B] = vertex2children.getOrElse(b, Nil)

  override def root: B = genesis

  override def contains(b: B): Boolean = allVertices.contains(b)

  override def tips: Iterable[B] = verticesWithNoChildren

  override def insert(b: B): InferredTree.InsertResult = {
    if (allVertices.contains(b))
      InferredTree.InsertResult.AlreadyInserted
    else {
      val parent = getParent(b)
      parent match {
        case None =>
          if (b == genesis)
            InferredTree.InsertResult.AlreadyInserted
          else
            InferredTree.InsertResult.AttemptedToInsertSecondRoot
        case Some(p) =>
          if (allVertices.contains(p)) {
            vertex2children.getOrElseUpdate(p, empty).append(b)
            verticesWithNoChildren.remove(p)
            vertex2children.update(b, empty)
            verticesWithNoChildren.add(b)
            allVertices.add(b)
            InferredTree.InsertResult.Success
          } else
            InferredTree.InsertResult.MissingParent
      }
    }
  }
}
