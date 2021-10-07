package com.selfdualbrain.gui_framework.dof_editor

import com.selfdualbrain.dynamic_objects._
import com.selfdualbrain.gui_framework.dof_editor.cell_editors._
import com.selfdualbrain.gui_framework.layout_dsl.GuiLayoutConfig
import com.selfdualbrain.util.LineUnreachable

import javax.swing.table.{TableCellEditor, TableCellRenderer}
import javax.swing.tree.TreePath
import scala.collection.mutable.ArrayBuffer
import scala.language.existentials

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
  * In the low-layer we start with one dynamic object (the root object) and then we discover transitive closure of dynamic-object links. In general this would lead to
  * a directed graph. This graph happens to be a tree because of the specific way we create dynamic objects with this editor (i.e. they do not share dependencies).
  *
  * The middle layer reads data from dynamic objects network and:
  *   1. materializes the tree
  *   2. establishes a concept of "value" attached to every node of this tree; on these "values" the interaction between mid-layer and high-layer is based
  *   3. reacts to changes in the tree - such a change typically will enforce recalculation of some subtree
  *
  * @tparam V type of values this node encapsulates
  */
sealed trait TTNode[V] {
  //the tree-table model this node belongs to.
  val owner: TTModel
  //parent node in the tree
  val parent: Option[TTNode[_]]
  //dynamic object which contains information displayed by this node
  val obj: DynamicObject
  private var childNodesX: Option[Seq[TTNode[_]]] = None
  private var childNodesOngoingRefresh: Boolean = false
  private var cellRendererX: Option[TableCellRenderer] = None
  val debugId: Long = TTNode.nextNodeId

  def displayedName: String

  def childNodes: Seq[TTNode[_]] =
    childNodesX match {
      case Some(coll) => coll
      case None =>
        val coll = this.discoverChildNodes.toSeq
        childNodesX = Some(coll)
        if (childNodesOngoingRefresh) {
          childNodesOngoingRefresh = false
          val index2childPairs: Seq[(Int, TTNode[_])] = childNodesX.get.zipWithIndex.map{case (node, index) => (index, node)}
          owner.fireNodesInserted(this, index2childPairs)
        }
        coll
    }

  def discoverChildNodes: Iterable[TTNode[_]]

  /**
    * Called after the data in this node is changed in a way that enforces re-creation of the whole subtree.
    */
  def recreateChildNodes(): Unit = {
    if (childNodesX.nonEmpty) {
      val index2childPairs: Seq[(Int, TTNode[_])] = childNodesX.get.zipWithIndex.map{case (node, index) => (index, node)}
      childNodesX = None
      childNodesOngoingRefresh = true
      owner.fireNodesRemoved(this, index2childPairs)
    }
  }

  def isEditable: Boolean = false

  def path: TreePath =
    parent match {
      case None => new TreePath(this)
      case Some(node) => node.path.pathByAddingChild(this)
    }

  def getIndexOfChild(node: TTNode[_]): Int = this.childNodes.indexOf(node)

  def indexAmongSiblings: Int =
    parent match {
      case None => 0
      case Some(p) => p.getIndexOfChild(this)
    }

  //Returns the value to be displayed in tree-table, in a visual widget corresponding to this node.
  //This should be understood as a conversion: internal data of the dynamic object ---> a value that ui widget expected at this position of the tree table can deal with.
  def value: Option[V]

  def cellRenderer(guiLayoutConfig: GuiLayoutConfig): TableCellRenderer =
    cellRendererX match {
      case Some(ed) => ed
      case None =>
        val ed = this.createCellRenderer(guiLayoutConfig)
        cellRendererX = Some(ed)
        ed
    }

  protected def createCellRenderer(guiLayoutConfig: GuiLayoutConfig): TableCellRenderer

  protected def createTTNodeFromOneFieldInDynamicObject[T](obj: DynamicObject, property: DofProperty[T]): TTNode[_] = {
    def createNodeForAttrSingle[X](p: DofAttributeSingleWithStaticType[X]) = TTNode.AttrSingle[X](owner, Some(this), obj, p)

    return property match {
      case p: DofLinkSingle =>
        if (p.valueType.isAbstract)
          TTNode.LinkSingleWithPolymorphicTargetType(owner, Some(this), obj, p)
        else
          TTNode.LinkSingleWithExactTargetType(owner, Some(this), obj, p)
      case p: DofLinkCollection => TTNode.LinkCollection(owner, Some(this), obj, p)
      case p: DofAttributeSingleWithStaticType[_] => createNodeForAttrSingle(p)
      case p: DofAttributeNumberWithContextDependentQuantity => TTNode.AttrSingle[NumberWithQuantityAndUnit](owner, Some(this), obj, p)
      case p: DofAttributeIntervalWithContextDependentQuantity => TTNode.AttrSingle[IntervalWithQuantity](owner, Some(this), obj, p)
      case p: DofAttributeCollection[_] => TTNode.AttrCollection(owner, Some(this), obj, p)
    }
  }

  protected def generateChildNodesForDynamicObject(context: DynamicObject): Iterable[TTNode[_]] =
    for ((marker, elementName) <- context.dofClass.masterDisplayOrder)
    yield
      marker match {
        case "group" => TTNode.PropertiesGroup(owner, context, Some(this), elementName)
        case "property" => createTTNodeFromOneFieldInDynamicObject(context, context.dofClass.getProperty(elementName))
      }

}

/*                                                                          EditableTTNode                                                                                */

sealed trait EditableTTNode[V] extends TTNode[V] with ValueHolderWithValidation[Option[V]] {
  private var cellEditorX: Option[TableCellRenderer with TableCellEditor] = None

  override def isEditable: Boolean = true

  //Updates the underlying dynamic-object using the value obtained from the visual widget.
  //This should be understood as a conversion: a value that ui widget expected at this position of the tree table can deal with ---> internal data of the dynamic object.
  //Caution: this update can possibly lead to a recalculation of the corresponding subtree.
  def value_=(x: Option[V]): Unit = {
    //by default do nothing
    //(this happens to be enough for non-editable nodes)
  }

  override def check(x: Option[V]): Option[String] = None //by default there is no checking

  override final def cellRenderer(guiLayoutConfig: GuiLayoutConfig): TableCellRenderer = this.cellEditor(guiLayoutConfig)

  override protected def createCellRenderer(guiLayoutConfig: GuiLayoutConfig): TableCellRenderer with TableCellEditor = throw new LineUnreachable

  def cellEditor(guiLayoutConfig: GuiLayoutConfig): TableCellRenderer with TableCellEditor =
    cellEditorX match {
      case Some(ed) => ed
      case None =>
        val ed = this.createCellEditor(guiLayoutConfig)
        cellEditorX = Some(ed)
        ed
    }

  protected def createCellEditor(guiLayoutConfig: GuiLayoutConfig): TableCellRenderer with TableCellEditor

  protected def createAttrCellEditor[E](guiLayoutConfig: GuiLayoutConfig, valueType: DofValueType[E], nullPolicy: NullPolicy): TableCellRenderer with TableCellEditor = {
    val internalWidget = (valueType match {
      case DofValueType.TBoolean => new BooleanWidget
      case DofValueType.TString => new StringEditorWidget
      case DofValueType.TNonemptyString => new StringEditorWidget
      case t:DofValueType.TInt => new IntWidget
      case t:DofValueType.TLong => new LongWidget
      case t:DofValueType.TDecimal => throw new RuntimeException("not supported yet") //todo: add support for decimal values
      case t:DofValueType.TFloatingPoint => new FloatingPointWidget
      case t:DofValueType.TFloatingPointWithQuantity => new FloatingPointWithQuantityWidget(guiLayoutConfig, t.quantity)
      case t:DofValueType.TFloatingPointIntervalWithQuantity => new FloatingPointIntervalWithQuantityWidget(guiLayoutConfig, t.quantity, t.leftEndName, t.rightEndName)
      case DofValueType.TSimTimepoint => new SimTimepointWidget
      case DofValueType.HHMMSS => new HumanReadableTimeAmountWidget(guiLayoutConfig)
      case other => throw new RuntimeException(s"unsupported dof type: $other")
    }).asInstanceOf[SingleValueEditingSwingWidget[E]]

    val optionalityAwareWidget: SingleValueEditingSwingWidget[E] =
      nullPolicy match {
        case NullPolicy.Mandatory => internalWidget
        case NullPolicy.Optional(present, absent) => new OptionalityDecoratorWidget(guiLayoutConfig, absent, present, internalWidget)
      }

    val editor: GenericDofCellEditor[E] = new GenericDofCellEditor(optionalityAwareWidget, valueType.defaultValue)
    return editor
  }

}

/*                                                                            IMPLEMENTATIONS                                                                                        */

object TTNode {

  @volatile
  var lastNodeId: Long = 0

  def nextNodeId: Long = {
    lastNodeId += 1
    return lastNodeId
  }

  /* Root */
  case class Root(owner: TTModel, parent: Option[TTNode[_]], obj: DynamicObject) extends TTNode[String] {
    override def displayedName: String = "simulation config"

    override def discoverChildNodes: Iterable[TTNode[_]] = generateChildNodesForDynamicObject(context = obj)

    override def isEditable: Boolean = false

    override def value: Option[String] = Some("")

    override protected def createCellRenderer(guiLayoutConfig: GuiLayoutConfig): TableCellRenderer =
      new GenericDofCellRenderer[String](new StringLabelWidget, "")

    override def toString: String = "ttNode:Root"
  }

  /* AttrSingle */
  case class AttrSingle[V](owner: TTModel, parent: Option[TTNode[_]], obj: DynamicObject, property: DofAttribute[V]) extends EditableTTNode[V] {
    private val valueType: DofValueType[V] = property.valueType(obj)

    override def displayedName: String = property.displayName

    override def discoverChildNodes: Iterable[TTNode[_]] = Iterable.empty

    override def value: Option[V] = obj.getSingle(property.name)

    override def value_=(x: Option[V]): Unit = {
      obj.setSingle(property.name, x)
    }

    override protected def createCellEditor(guiLayoutConfig: GuiLayoutConfig): TableCellRenderer with TableCellEditor =
      this.createAttrCellEditor(guiLayoutConfig, valueType, property.nullPolicy)

    override def check(x: Option[V]): Option[String] = super.check(x) //todo

    override def toString: String = s"ttNode:AttrSingle(obj=$obj, property=$property)"
  }

  /* AttrCollection */
  case class AttrCollection[V](owner: TTModel, parent: Option[TTNode[_]], obj: DynamicObject, property: DofAttribute[V]) extends TTNode[String] {

    override def displayedName: String = property.displayName

    override def discoverChildNodes: Iterable[TTNode[_]] = {
      val coll: ArrayBuffer[_] = obj.getCollection(property.name)
      return coll map { element => new AttrCollectionElement(owner, Some(this), obj, property) }
    }

    override def isEditable: Boolean = false

    override def value: Option[String] = Some(obj.getCollection(property.name).size.toString)

    override protected def createCellRenderer(guiLayoutConfig: GuiLayoutConfig): TableCellRenderer =
      new GenericDofCellRenderer[String](new StringLabelWidget, "")

    override def toString: String = s"ttNode:AttrCollection(obj=$obj, property=$property)"
  }

  /* AttrCollectionElement */
  case class AttrCollectionElement[V](owner: TTModel, parent: Option[TTNode[_]],obj: DynamicObject, property: DofAttribute[V]) extends EditableTTNode[V] {
    private val valueType: DofValueType[V] = property.valueType(obj)

    override def displayedName: String = this.indexAmongSiblings.toString

    override def discoverChildNodes: Iterable[TTNode[_]] = Iterable.empty

    override def isEditable: Boolean = true

    override def value: Option[V] = Some(obj.getCollection[V](property.name)(this.indexAmongSiblings))

    override protected def createCellEditor(guiLayoutConfig: GuiLayoutConfig): TableCellRenderer with TableCellEditor =
      this.createAttrCellEditor(guiLayoutConfig, valueType, NullPolicy.Mandatory)

    override def check(x: Option[V]): Option[String] = super.check(x) //todo

    override def toString: String = s"ttNode:AttrCollectionElement(obj=$obj, property=$property, index=${this.indexAmongSiblings})"
  }

  /* LinkSingle - exact target type*/
  case class LinkSingleWithExactTargetType(owner: TTModel, parent: Option[TTNode[_]], obj: DynamicObject, property: DofLinkSingle) extends EditableTTNode[DofClass] {

    override def displayedName: String = property.displayName

    override def discoverChildNodes: Iterable[TTNode[_]] =
      obj.getSingle[DynamicObject](property.name) match {
        case None => Iterable.empty
        case Some(value) => generateChildNodesForDynamicObject(context = value)
      }

    override def isEditable: Boolean = property.nullPolicy != NullPolicy.Mandatory

    override def value: Option[DofClass] = {
      //When this property has nullPolicy == Optional, the actual new value (i.e. a dynamic object)
      //is created from scratch every time the user enables the "present/absent" checkbox.
      //A little complication arises in the Mandatory case - then there is no checkbox
      //and forcing the user to explicitly click something so to create the instance would be quite redundant
      //given the fact that we (1) know the class to be instantiated (2) know that the presence of a value is mandatory.
      //Hence we do this here "automagically" as a side effect of accessing the value.

      val currentValueOfThisProperty: Option[DynamicObject] = obj.getSingle[DynamicObject](property.name)
      val effectiveValueOfThisProperty: Option[DynamicObject] =
        if (currentValueOfThisProperty.isEmpty && property.nullPolicy == NullPolicy.Mandatory) {
          if (property.valueType.isAbstract)
            throw new RuntimeException(s"property $property")
          val newEmptyInstanceOfTargetClass = new DynamicObject(property.valueType)
          obj.setSingle[DynamicObject](property.name, Some(newEmptyInstanceOfTargetClass))
          recreateChildNodes()
          Some(newEmptyInstanceOfTargetClass)
        } else {
          currentValueOfThisProperty
        }
      return effectiveValueOfThisProperty map (x => x.dofClass)
    }

    override def value_=(clazzOrNone: Option[DofClass]): Unit = {
      clazzOrNone match {
        case None =>
          obj.setSingle(property.name, None)
          recreateChildNodes()
        case Some(c) =>
          val oldClassOption = this.value
          if (! oldClassOption.contains(c)) {
            val newInstanceOfTargetClass = new DynamicObject(c)
            obj.setSingle(property.name, Some(newInstanceOfTargetClass))
            recreateChildNodes()
          }
      }
    }

    override protected def createCellEditor(guiLayoutConfig: GuiLayoutConfig): TableCellRenderer with TableCellEditor = {
      val classNameRenderer: SingleValueEditingSwingWidget[DofClass] = new StringLabelMapperWidget((clazz: DofClass) => clazz.displayName)
      val widget: SingleValueEditingSwingWidget[DofClass] = property.nullPolicy match {
        case NullPolicy.Mandatory => classNameRenderer
        case NullPolicy.Optional(present, absent) => new OptionalityDecoratorWidget(guiLayoutConfig, absent, present, classNameRenderer)
      }
      return new GenericDofCellEditor[DofClass](widget, property.valueType)
    }

    override def check(x: Option[DofClass]): Option[String] = None

    override def toString: String = s"ttNode:LinkSingleWithExactTargetType(obj=$obj, property=$property)"
  }

  /* LinkSingle - polymorphic type*/
  case class LinkSingleWithPolymorphicTargetType(owner: TTModel, parent: Option[TTNode[_]], obj: DynamicObject, property: DofLinkSingle) extends EditableTTNode[DofClass] {

    override def displayedName: String = property.displayName

    override def discoverChildNodes: Iterable[TTNode[_]] =
      obj.getSingle[DynamicObject](property.name) match {
        case None => Iterable.empty
        case Some(value) => generateChildNodesForDynamicObject(context = value)
      }

    override def isEditable: Boolean = true

    override def value: Option[DofClass] = obj.getSingle[DynamicObject](property.name) map (x => x.dofClass)

    override def value_=(clazzOrNone: Option[DofClass]): Unit = {
      clazzOrNone match {
        case None =>
          obj.setSingle(property.name, None)
          recreateChildNodes()

        case Some(c) =>
          val oldClassOption = this.value
          if (! oldClassOption.contains(c)) {
            val newInstanceOfTargetClass = new DynamicObject(c)
            obj.setSingle(property.name, Some(newInstanceOfTargetClass))
            recreateChildNodes()
          }
      }
    }

    override protected def createCellEditor(guiLayoutConfig: GuiLayoutConfig): TableCellRenderer with TableCellEditor = {
      val widget = new DofSubclassSelectionWidget(guiLayoutConfig, property.valueType)
      val editor: GenericDofCellEditor[DofClass] = new GenericDofCellEditor(widget, property.valueType)
      return editor
    }

    override def check(x: Option[DofClass]): Option[String] = None

    override def toString: String = s"ttNode:LinkSingleWithPolymorphicTargetType(obj=$obj, property=$property)"
  }

  /* LinkCollection */
  case class LinkCollection(owner: TTModel, parent: Option[TTNode[_]], obj: DynamicObject, property: DofLink) extends TTNode[String] {

    override def displayedName: String = property.displayName

    override def discoverChildNodes: Iterable[TTNode[_]] = {
      val coll: ArrayBuffer[DynamicObject] = obj.getCollection[DynamicObject](property.name)
      return coll map { element => LinkCollectionElement(owner, Some(this), obj, property) }
    }

    override def isEditable: Boolean = false

    override def value: Option[String] = Some(this.childNodes.size.toString)

    override protected def createCellRenderer(guiLayoutConfig: GuiLayoutConfig): TableCellRenderer =
      new GenericDofCellRenderer[String](new StringLabelWidget, "")

    override def toString: String = s"ttNode:LinkCollection(obj=$obj, property=$property)"
  }

  /* LinkCollectionElement */
  case class LinkCollectionElement(owner: TTModel, parent: Option[TTNode[_]], obj: DynamicObject, property: DofLink) extends EditableTTNode[DofClass] {

    override def displayedName: String = this.indexAmongSiblings.toString

    override def discoverChildNodes: Iterable[TTNode[_]] = {
      val context = obj.getCollection[DynamicObject](property.name)(this.indexAmongSiblings)
      return generateChildNodesForDynamicObject(context)
    }

    override def isEditable: Boolean = true

    override def value: Option[DofClass] = {
      val targetObject = obj.getCollection[DynamicObject](property.name)(this.indexAmongSiblings)
      return Some(targetObject.dofClass)
    }

    override def value_=(clazz: Option[DofClass]): Unit = {
      //todo: finish this
      throw new RuntimeException("not implemented")
      //      obj.getCollection[DynamicObject](property.name)(this.indexAmongSiblings) = new DynamicObject(clazz)
      //      recreateChildNodes()
    }

    override protected def createCellEditor(guiLayoutConfig: GuiLayoutConfig): TableCellRenderer with TableCellEditor = ??? //todo

    override def toString: String = s"ttNode:LinkCollectionElement(obj=$obj, property=$property)"
  }

  /* PropertiesGroup */
  case class PropertiesGroup(owner: TTModel, obj: DynamicObject, parent: Option[TTNode[_]], groupName: String) extends TTNode[String] {

    override def displayedName: String = groupName

    override def discoverChildNodes: Iterable[TTNode[_]] = {
      for (propertyName <- obj.dofClass.perGroupDisplayOrder(groupName))
      yield createTTNodeFromOneFieldInDynamicObject(obj, obj.dofClass.getProperty(propertyName))
    }

    override def isEditable: Boolean = false

    override def value: Option[String] = Some("")

    override protected def createCellRenderer(guiLayoutConfig: GuiLayoutConfig): TableCellRenderer =
      new GenericDofCellRenderer[String](new StringLabelWidget, "")

    override def toString: String = s"ttNode:PropertiesGroup(obj=$obj, groupName=$groupName)"
  }

}


