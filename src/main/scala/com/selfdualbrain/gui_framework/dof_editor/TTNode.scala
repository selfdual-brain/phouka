package com.selfdualbrain.gui_framework.dof_editor

import com.selfdualbrain.data_structures.FastMapOnIntInterval
import com.selfdualbrain.dynamic_objects.DofAttribute.Multiplicity
import com.selfdualbrain.dynamic_objects.{DofAttribute, DofClass, DofLink, DofProperty, DynamicObject}

/**
  * Nodes making the tree structure we use underneath tree-table of dof editor.
  *
  * This is needed because the mapping of dynamic object to corresponding tree is non-trivial.
  * Also, we anticipate this mapping may evolve in future versions of the software, hence it is convenient
  * to have it being clearly encapsulated.
  *
  * Please notice 3 layers of tree-shaped "values" that emerge. Below we describe these layers in a stacked view:
  * high-layer (tree-table): this is made of JxTreeTable instance with relevant CellEditor collection - it displays and allows manipulation of data
  * middle-layer (tree made of TTNode instances) - establishes a tree, which is 1-1 to the tree displayed in the high layer
  * low-layer (dependency graph of a dynamic object) - actual instances of dynamic objects with their properties (=fields) possibly pointing to other dynamic objects
  *
  * In low-layer we start with one dynamic object (the root object) and then we discover transitive closure of dynamic-object links. This gives us a tree, because
  * we create our dynamic objects in a way that they do not share dependencies.
  *
  * The middle layer reads data from dynamic objects network and:
  *   1. materializes the tree
  *   2. establishes a concept of "value" attached to every node of this tree; on these "values" the interaction between mid-layer and high-layer is based
  *   3. reacts to changes in the tree - such a change typically will enforce recalculation of some subtree
  *
  * @param owner the tree-table model this node belongs to.
  * @param obj dynamic object which contains information displayed by this node
  * @tparam V type of values this node encapsulates
  */
sealed abstract class TTNode[V](owner: TTModel, obj: DynamicObject) {
  private var childNodesX: Option[Seq[TTNode[_]]] = None

  def displayedName: String

  def childNodes: Seq[TTNode[_]] =
    childNodesX match {
      case Some(coll) => coll
      case None =>
        val coll = this.createChildNodes.toSeq
        childNodesX = Some(coll)
        coll
    }

  def createChildNodes: Iterable[TTNode[_]]

  def invalidateChildNodes(): Unit = {
    childNodesX = None
  }

  def isEditable: Boolean

  //Returns the value to be displayed in tree-table, in a visual widget corresponding to this node.
  //This should be understood as a conversion: internal data of the dynamic object ---> a value that ui widget expected at this position of the tree table can deal with.
  def value: V

  //Updates the underlying dynamic-object using the value obtained from the visual widget.
  //This should be understood as a conversion: a value that ui widget expected at this position of the tree table can deal with ---> internal data of the dynamic object.
  //Caution: this update can possibly lead to a recalculation of the corresponding subtree.
  def value_=(x: V): Unit = {
    //by default do nothing
    //(this happens to be enough for non-editable nodes)
  }

  protected def createTTNodeFromOneFieldInDynamicObject[T](obj: DynamicObject, property: DofProperty[T]): TTNode[_] =
    property match {
      case p: DofLink =>
        if (p.isCollection)
          new TTNode.LinkCollection(owner, obj, p.name)
        else
          new TTNode.LinkSingle(owner, obj, p.name)

      case p: DofAttribute[_] =>
        p.multiplicity match {
          case Multiplicity.Single => new TTNode.AttrSingle(owner, obj, p.name)
          case Multiplicity.Interval(a,b) => new TTNode.AttrInterval(owner, obj, p.name)
          case Multiplicity.Sequence => new TTNode.AttrCollection(owner, obj, p.name)
        }
    }

  protected def generateChildNodesForDynamicObject(context: DynamicObject): Iterable[TTNode[_]] =
    for ((marker, elementName) <- context.dofClass.masterDisplayOrder)
    yield
      marker match {
        case "group" => new TTNode.PropertiesGroup(owner, context, elementName)
        case "property" => createTTNodeFromOneFieldInDynamicObject(context, context.dofClass.getProperty(elementName))
      }

}

object TTNode {

  class Root(owner: TTModel, obj: DynamicObject) extends TTNode[Unit](owner, obj) {
    override def displayedName: String = "simulation config"

    override def createChildNodes: Iterable[TTNode[_]] = generateChildNodesForDynamicObject(context = obj)

    override def isEditable: Boolean = false

    override def value: Unit = ()
  }

  class AttrSingle[A](owner: TTModel, obj: DynamicObject, propertyName: String) extends TTNode[Option[A]](owner, obj) {
    private val property = obj.dofClass.getProperty(propertyName)

    override def displayedName: String = property.displayName

    override def createChildNodes: Iterable[TTNode[_]] = Iterable.empty

    override def isEditable: Boolean = true

    override def value: Option[A] = obj.getSingle(propertyName)

    override def value_=(x: Option[A]): Unit = {
      obj.setSingle(propertyName, x)
    }
  }

  class AttrInterval[A](owner: TTModel, obj: DynamicObject, propertyName: String) extends TTNode[Option[(A,A)]](owner: TTModel, obj: DynamicObject) {
    private val property = obj.dofClass.getProperty(propertyName)

    override def displayedName: String = property.displayName

    override def createChildNodes: Iterable[TTNode[_]] = Iterable.empty

    override def isEditable: Boolean = true

    override def value: Option[(A,A)] = obj.getInterval(propertyName)

    override def value_=(x: Option[(A,A)]): Unit = obj.setInterval(propertyName, x.asInstanceOf[Option[(Any, Any)]])
  }

  class AttrCollection(owner: TTModel, obj: DynamicObject, propertyName: String) extends TTNode[Int](owner: TTModel, obj: DynamicObject) {
    private val property = obj.dofClass.getProperty(propertyName)

    override def displayedName: String = property.displayName

    override def createChildNodes: Iterable[TTNode[_]] = {
      val coll: FastMapOnIntInterval[_] = obj.getCollection(propertyName)
      return coll.zipWithIndex map { case (element, i) => new AttrCollectionElement(owner, obj, propertyName, i) }
    }

    override def isEditable: Boolean = false

    override def value: Int = obj.getCollection(propertyName).size
  }

  class AttrCollectionElement[A](owner: TTModel, obj: DynamicObject, propertyName: String, index: Int) extends TTNode[A](owner: TTModel, obj: DynamicObject) {
    private val property = obj.dofClass.getProperty(propertyName)

    override def displayedName: String = index.toString

    override def createChildNodes: Iterable[TTNode[_]] = Iterable.empty

    override def isEditable: Boolean = true

    override def value: A = obj.getCollection[A](propertyName)(index)
  }

  class LinkSingle(owner: TTModel, obj: DynamicObject, propertyName: String) extends TTNode[Option[DofClass]](owner, obj) {
    private val property = obj.dofClass.getProperty(propertyName)

    override def displayedName: String = propertyName

    override def createChildNodes: Iterable[TTNode[_]] =
      obj.getSingle[DynamicObject](propertyName) match {
        case None => Iterable.empty
        case Some(value) => generateChildNodesForDynamicObject(context = value)
      }

    override def isEditable: Boolean = true

    override def value: Option[DofClass] = obj.getSingle[DynamicObject](propertyName) map (x => x.dofClass)

    override def value_=(clazzOrNone: Option[DofClass]): Unit = {
      clazzOrNone match {
        case None =>
          obj.setSingle(propertyName, None)
          invalidateChildNodes()
          //todo: broadcast change event here

        case Some(c) =>
          obj.setSingle(propertyName, Some(new DynamicObject(c)))
          invalidateChildNodes()
        //todo: broadcast change event here
      }

    }
  }

  class LinkCollection(owner: TTModel, obj: DynamicObject, propertyName: String) extends TTNode[Int](owner, obj) {
    private val property = obj.dofClass.getProperty(propertyName)

    override def displayedName: String = property.displayName

    override def createChildNodes: Iterable[TTNode[_]] = {
      val coll: FastMapOnIntInterval[DynamicObject] = obj.getCollection[DynamicObject](propertyName)
      return coll.zipWithIndex map { case (element, i) => new LinkCollectionElement(owner, obj, propertyName, i) }
    }

    override def isEditable: Boolean = false

    override def value: Int = this.childNodes.size
  }

  class LinkCollectionElement(owner: TTModel, obj: DynamicObject, propertyName: String, index: Int) extends TTNode[DofClass](owner, obj) {
    private val property = obj.dofClass.getProperty(propertyName)

    override def displayedName: String = index.toString

    override def createChildNodes: Iterable[TTNode[_]] = {
      val context = obj.getCollection[DynamicObject](propertyName)(index)
      return generateChildNodesForDynamicObject(context)
    }

    override def isEditable: Boolean = true

    override def value: DofClass = obj.getCollection[DynamicObject](propertyName)(index).dofClass

    override def value_=(clazz: DofClass): Unit = {
      obj.getCollection[DynamicObject](propertyName)(index) = new DynamicObject(clazz)
      invalidateChildNodes()
      //todo: broadcast change event here
    }
  }

  class PropertiesGroup(owner: TTModel, obj: DynamicObject, groupName: String) extends TTNode[String](owner, obj) {

    override def displayedName: String = groupName

    override def createChildNodes: Iterable[TTNode[_]] = {
      for (propertyName <- obj.dofClass.perGroupDisplayOrder(groupName))
      yield createTTNodeFromOneFieldInDynamicObject(obj, obj.dofClass.getProperty(propertyName))
    }

    override def isEditable: Boolean = false

    override def value: String = groupName
  }

}


