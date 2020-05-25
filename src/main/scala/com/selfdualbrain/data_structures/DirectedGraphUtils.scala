package com.selfdualbrain.data_structures

import scala.annotation.tailrec
import scala.collection.mutable

/**
  * Provides utility methods for graph-like structures.
  */
object DirectedGraphUtils {

  private trait QueueLike[E] {
    def head: E
    def enqueue(a: E): Unit
    def dequeue(): E
    def isEmpty: Boolean
  }

  private class BfIterator[Vertex](startingVertices: Iterable[Vertex], discoverTargets: Vertex => Iterable[Vertex], buffer: QueueLike[Vertex]) extends Iterator[Vertex] {
    private val visited: mutable.HashSet[Vertex] = mutable.HashSet.empty
    startingVertices.foreach(buffer.enqueue)

    override def next(): Vertex =
      if (this.hasNext) {
        val node = buffer.dequeue()
        visited.add(node)
        discoverTargets(node).foreach(n => if (!visited(n)) buffer.enqueue(n))
        node
      } else throw new NoSuchElementException("called next() for empty iterator")

    @tailrec
    override final def hasNext: Boolean =
      if (! buffer.isEmpty) {
       val n = buffer.head
        if (visited(n)) {
          val _ = buffer.dequeue()
          hasNext
        } else true
      } else false

  }

  /**
    * Breadth-first traversal, where we iterate over vertices only.
    *
    * @param starts starting nodes (first nodes to visit)
    * @param nextVertices function determining which nodes a given nodes is connected to
    * @return an iterator traversing the nodes in breadth-first order
    */
  def breadthFirstTraverse[Vertex](starts: Iterable[Vertex], nextVertices: Vertex => Iterable[Vertex]): Iterator[Vertex] = {
    val buf: QueueLike[Vertex] = new QueueLike[Vertex] {
      val q = new mutable.Queue[Vertex]
      override def head: Vertex = q.head
      override def enqueue(a: Vertex): Unit = q.enqueue(a)
      override def dequeue(): Vertex = q.dequeue()
      override def isEmpty: Boolean = q.isEmpty
    }
    new BfIterator[Vertex](starts, nextVertices, buf)
  }

  /**
    * Variant of breadth-first traversal which respect ordering of vertices, so that effectively the traversal
    * follows (some) topological sorting of the DAG. We assume that for each vertex, a daglevel value is provided.
    *
    * @param starts starting nodes (first nodes to visit)
    * @param nextVertices function determining which nodes a given nodes is connected to
    * @param dagLevel function returning dag-level for any vertex x; this must be the length of longest path from r --> x, where r is root (many roots are possible in a DAG)
    * @param ascending signals the direction of dagLevels against nextVertices function; if nextVertices goes along ascending of dagLevel, then ascending=true
    *
    * @return an iterator traversing the nodes in breadth-first order
    */
  def breadthFirstToposortTraverse[Vertex](starts: Iterable[Vertex], nextVertices: Vertex => Iterable[Vertex], dagLevel: Vertex => Int, ascending: Boolean): Iterator[Vertex] = {
    implicit val queueItemOrdering: Ordering[Vertex] =
    new Ordering[Vertex] {
      override def compare(x: Vertex, y: Vertex): Int =
        if (ascending)
          dagLevel(y) - dagLevel(x)
        else
          dagLevel(x) - dagLevel(y)
    }
    val buf: QueueLike[Vertex] = new QueueLike[Vertex] {
      val q = new mutable.PriorityQueue[Vertex]()
      override def head: Vertex = q.head
      override def enqueue(a: Vertex): Unit = q.enqueue(a)
      override def dequeue(): Vertex = q.dequeue()
      override def isEmpty: Boolean = q.isEmpty
    }
    return new BfIterator[Vertex](starts, nextVertices, buf)
  }

  /**
    * Finds the latest common ancestor of any subset of vertices in a tree.
    * We assume that the function level: Vertex -> Int is available (most likely by vertices knowing their level),
    * so we can be mode optimal by reusing this information.
    *
    * @param starts collection of vertices, which common ancestor is being searched for
    * @param getParent function giving a parent of given vertex (so, this is how structure of the tree is encoded)
    * @param getLevel function calculating the level of a vertex; level of A is the length of the path from root to A; level of root is 0
    * @tparam Vertex type of vertices in the tree
    * @return from all vertices that are common parents of 'starts' picks the one with biggest level
    */
  def latestCommonAncestorInATree[Vertex](starts: Iterable[Vertex], getParent: Vertex => Vertex, getLevel: Vertex => Int): Vertex =
    starts reduceLeft { (a,b) => latestCommonAncestorInATree(a,b, getParent, getLevel) }

  /**
    * Finds the latest common ancestor for two given vertices in a tree.
    * We assume that the function level: Vertex -> Int is available (most likely by vertices knowing their level),
    * so we can be mode optimal by reusing this information.
    *
    * @param a first vertex
    * @param b second vertex
    * @param getParent function giving a parent of given vertex (so, this is how structure of the tree is encoded)
    * @param getLevel function calculating the level of a vertex; level of A is the length of the path from root to A; level of root is 0
    * @tparam Vertex type of vertices in the tree
    * @return from all vertices that are common parents of 'a' and 'b' picks the one with biggest level
    */
  @tailrec
  def latestCommonAncestorInATree[Vertex](a: Vertex, b: Vertex, getParent: Vertex => Vertex, getLevel: Vertex => Int): Vertex =
    math.signum(getLevel(a) - getLevel(b)) match {
      case -1 => latestCommonAncestorInATree(a, getParent(b), getParent, getLevel)
      case 0  => if (a == b) a else latestCommonAncestorInATree(getParent(a), getParent(b), getParent, getLevel)
      case 1  =>  latestCommonAncestorInATree(getParent(a), b, getParent, getLevel)
    }

  /**
    * Calculates the transitive closure of a directed graph.
    * We use classical Warshall's algorithm
    * https://cs.winona.edu/lin/cs440/ch08-2.pdf
    *
    * @param graph adjacency matrix of the input graph; 'graph(row)(col) == true' represents an edge from vertex i to vertex j;
    *              caution: adjacency matrix must be a square matrix !
    * @return adjacency matrix of the resulting transitive closure of input graph; the result is a newly allocated array (input array is never modified)
    */
  def transitiveClosure(graph: Array[Array[Boolean]]): Array[Array[Boolean]] = {
    val n: Int = graph.length
    val result: Array[Array[Boolean]] = Array.ofDim[Boolean](n,n)
    graph.copyToArray(result)

    for {
      k <- 0 until n
      row <- 0 until n if row != k
      col <- 0 until n if col != k
    } {
      if (! result(row)(col))
        result(row)(col) = result(row)(k) && result(k)(col)
    }

    return result
  }

  /**
    * Calculates the transitive reduction of a directed acyclic graph.
    * This is an "reversed Warshall's algorithm" described in the paper David Gries, Alain J. Martin, Jan L.A. van de Snepscheut, Jan Tijmen Udding
    * "An algorithm for transitive reduction of an acyclic graph", Science of Computer Programming 12 (1989) 151-155, North Holland
    *
    * Caution: notice lack of symmetry between transitive closure and transitive reduction. In transitive closure we do not need the "acyclic" condition.
    * In general, transitive closure is always unique, while transitive reduction is unique only for acyclic graphs.
    *
    * @param graph adjacency matrix of the input graph; 'graph(row)(col) == true' represents an edge from vertex i to vertex j;
    *              caution: adjacency matrix must be a square matrix !
    * @return adjacency matrix of the resulting transitive closure of input graph; the result is a newly allocated array (input array is never modified)
    */
  def transitiveReduction(graph: Array[Array[Boolean]]): Array[Array[Boolean]] = {
    val n: Int = graph.length
    val result = transitiveClosure(graph)

    for {
      k <- n-1 to 0 by -1
      row <- 0 until n if row != k
      col <- 0 until n if col != k
    } {
      if (result(row)(k) && result(k)(col)) {
        result(row)(col) = false
      }

    }

    return result
  }

}
