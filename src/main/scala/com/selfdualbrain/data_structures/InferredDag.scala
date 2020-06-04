package com.selfdualbrain.data_structures

//Abstraction of directed acyclic graph, where the structure is inferred from vertices.
//We use this to represent the j-dag.
trait InferredDag[Vertex] {

  /**
   * Returns targets reachable (in one step) from given vertex by going along the arrows.
   * @param v vertex
   * @return collection of vertices
   */
  def targetsOf(v: Vertex): Iterable[Vertex]

  /**
   * Returns sources reachable (in one step) from given vertex by going against the arrows.
   * @param v vertex
   * @return collection of vertices
   */
  def sourcesOf(v: Vertex): Iterable[Vertex]

  /**
   * Returns true if given vertex is a member of this DAG.
   * @param v vertex
   * @return true if this DAG contains vertex
   */
  def contains(v: Vertex): Boolean

  /**
   * List of nodes which are only sources, but not targets,
   * i.e. nodes with only outgoing arrows and no incoming arrows.
   * @return list of nodes which are only sources.
   */
  def tips: Iterable[Vertex]

  /**
   * Add a new node to the DAG.
   * Sources and targets of this node must be inferred (so we assume that this information is somehow encoded
   * inside the vertex itself).
   * @param v new vertex to be added; targets of v must be already present in the DAG
   * @return true if the vertex was actually added, false if the vertex was already present in the DAG
   */
  def insert(v: Vertex): Boolean

  /**
   * Traverses the DAG (breadth-first-search) along the arrows.
   * @param v vertex from which we start the traversal
   * @return iterator of vertices, sorted by dagLevel (descending)
   */
  def toposortTraverseFrom(v: Vertex): Iterator[Vertex]

  /**
   * Traverses the DAG (breadth-first-search) along the arrows.
   * @param coll collection of vertices from which we start the traversal
   * @return iterator of vertices, sorted by dagLevel (descending)
   */
  def toposortTraverseFrom(coll: Iterable[Vertex]): Iterator[Vertex]

}
