package com.selfdualbrain.data_structures

/**
  * Abstraction of a tree, where the structure is inferred from vertices.
  * @tparam Vertex the type of the vertices in the tree
  */
trait InferredTree[Vertex] {

  /**
    * Gets the parent vertex (or none if n is root).
    *
    * @param n vertex
    * @return Some(parent) or None if n is the root
    */
  def parent(n: Vertex): Option[Vertex]

  /**
    * Returns the collection of child vertices.
    *
    * @param n vertex
    * @return collection of children of n
    */
  def children(n: Vertex): Iterable[Vertex]

  /**
    * Returns the root of this tree.
    */
  def root: Vertex

  /**
    * Returns true if given vertex is a member of this DAG.
    * @param n vertex
    * @return true if this DAG contains vertex
    */
  def contains(n: Vertex): Boolean

  /**
    * List of nodes which are only sources, but not targets,
    * i.e. nodes with only outgoing arrows and no incoming arrows.
    * @return list of nodes which are only sources.
    */
  def tips: Iterable[Vertex]

  /**
    * Add a new node to the DAG.
    * Sources and targets of this node must be inferred (so we assume that this information is somehow encoded inside the vertex itself).
    *
    * @param n New node to add
    */
  def insert(n: Vertex): InferredTree.InsertResult

  /**
    * Traverses a single path in this tree, from starting vertex to root.
    *
    * @param start starting vertex
    * @return iterator of vertices along the path to root
    */
  def pathToRoot(start: Vertex): Iterator[Vertex] = {
    var nextElementInTheStream: Option[Vertex] = Some(start)

    return new Iterator[Vertex] {
      override def hasNext: Boolean = nextElementInTheStream.isDefined

      override def next(): Vertex = {
        nextElementInTheStream match {
          case None => throw new RuntimeException("reached end of iterator")
          case Some(x) =>
            nextElementInTheStream = parent(x)
            return x
        }
      }
    }
  }

  def pathFromTo(start: Vertex, stop: Vertex): Iterator[Vertex] = {
    var stopWasSeen: Boolean = false
    this.pathToRoot(start) takeWhile { block =>
      if (stopWasSeen)
        false
      else {
        if (block == stop)
          stopWasSeen = true
        true
      }
    }
  }

  /**
    * Traverses the DAG (breath-first-search) along the inverted arrows.
    *
    * @param starts collection of vertices from which we start traversal
    * @return iterator of vertices (will iterate also the starting collection !)
    */
  def childrenTraverse(starts: Iterable[Vertex]): Iterator[Vertex] = DirectedGraphUtils.breadthFirstTraverse(starts, this.children)

}

object InferredTree {
  sealed trait InsertResult

  object InsertResult {
    case object AlreadyInserted extends InsertResult
    case object Success extends InsertResult
    case object MissingParent extends InsertResult
    case object AttemptedToInsertSecondRoot extends InsertResult
  }

}


