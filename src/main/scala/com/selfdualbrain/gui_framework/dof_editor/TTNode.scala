package com.selfdualbrain.gui_framework.dof_editor

import com.selfdualbrain.data_structures.FastMapOnIntInterval
import com.selfdualbrain.dynamic_objects._
import com.selfdualbrain.gui_framework.dof_editor.cell_editors.{BooleanWidget, FloatingPointIntervalWithQuantityWidget, FloatingPointWidget, FloatingPointWithQuantityWidget, GenericDofCellEditor, GenericDofCellRenderer, HumanReadableTimeAmountWidget, IntWidget, LongWidget, SimTimepointWidget, StringEditorWidget, StringLabelWidget}
import com.selfdualbrain.gui_framework.layout_dsl.GuiLayoutConfig

import javax.swing.table.{TableCellEditor, TableCellRenderer}
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
  * @param owner the tree-table model this node belongs to.
  * @param obj dynamic object which contains information displayed by this node
  * @tparam V type of values this node encapsulates
  */
sealed abstract class TTNode[V](owner: TTModel, obj: DynamicObject)  {
  private var childNodesX: Option[Seq[TTNode[_]]] = None
  private var cellEditorX: Option[TableCellRenderer] = None

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

  /**
    * Called after the data in this node is changed in a way that enforces re-creation of the whole subtree.
    */
  def recreateChildNodes(): Unit = {
    childNodesX = None
  }

  def isEditable: Boolean = false

  //Returns the value to be displayed in tree-table, in a visual widget corresponding to this node.
  //This should be understood as a conversion: internal data of the dynamic object ---> a value that ui widget expected at this position of the tree table can deal with.
  def value: Option[V]


  def cellRenderer(guiLayoutConfig: GuiLayoutConfig): TableCellRenderer =
    cellEditorX match {
      case Some(ed) => ed
      case None =>
        val ed = this.createCellRenderer(guiLayoutConfig)
        cellEditorX = Some(ed)
        ed
    }

  protected def createCellRenderer(guiLayoutConfig: GuiLayoutConfig): TableCellRenderer

  protected def createTTNodeFromOneFieldInDynamicObject[T](obj: DynamicObject, property: DofProperty[T]): TTNode[_] = {
    def createNodeForAttrSingle[X](p: DofAttributeSingleWithStaticType[X]) = new TTNode.AttrSingle[X](owner, obj, p)

    return property match {
      case p: DofLinkSingle => new TTNode.LinkSingle(owner, obj, p.name)
      case p: DofLinkCollection => new TTNode.LinkCollection(owner, obj, p.name)
      case p: DofAttributeSingleWithStaticType[_] => createNodeForAttrSingle(p)
      case p: DofAttributeNumberWithContextDependentQuantity => new TTNode.AttrSingle[NumberWithQuantityAndUnit](owner, obj, p)
      case p: DofAttributeIntervalWithContextDependentQuantity => new TTNode.AttrSingle[IntervalWithQuantity](owner, obj, p)
      case p: DofAttributeCollection[_] => new TTNode.AttrCollection(owner, obj, p.name)
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

abstract class EditableTTNode[V](owner: TTModel, obj: DynamicObject) extends TTNode[V](owner, obj) with ValueHolderWithValidation[Option[V]] {

  override def isEditable: Boolean = true

  //Updates the underlying dynamic-object using the value obtained from the visual widget.
  //This should be understood as a conversion: a value that ui widget expected at this position of the tree table can deal with ---> internal data of the dynamic object.
  //Caution: this update can possibly lead to a recalculation of the corresponding subtree.
  def value_=(x: Option[V]): Unit = {
    //by default do nothing
    //(this happens to be enough for non-editable nodes)
  }

  override def check(x: Option[V]): Option[String] = None //by default there is no checking

  override def cellRenderer(guiLayoutConfig: GuiLayoutConfig): TableCellRenderer with TableCellEditor =
    super.createCellRenderer(guiLayoutConfig).asInstanceOf[TableCellRenderer with TableCellEditor]

  override protected def createCellRenderer(guiLayoutConfig: GuiLayoutConfig): TableCellRenderer with TableCellEditor

}

/*                                                                                                                                                                           */

object TTNode {

  /* Root */
  class Root(owner: TTModel, obj: DynamicObject) extends TTNode[String](owner, obj) {
    override def displayedName: String = "simulation config"

    override def createChildNodes: Iterable[TTNode[_]] = generateChildNodesForDynamicObject(context = obj)

    override def isEditable: Boolean = false

    override def value: Option[String] = Some("")

    override protected def createCellRenderer(guiLayoutConfig: GuiLayoutConfig): TableCellRenderer = new GenericDofCellRenderer[String](new StringLabelWidget, "")
  }

  /* AttrSingle */
  class AttrSingle[V](owner: TTModel, obj: DynamicObject, property: DofAttribute[V]) extends EditableTTNode[V](owner, obj) {
    private val valueType: DofValueType[V] = property.valueType(obj)

    override def displayedName: String = property.displayName

    override def createChildNodes: Iterable[TTNode[_]] = Iterable.empty

    override def value: Option[V] = obj.getSingle(property.name)

    override def value_=(x: Option[V]): Unit = {
      obj.setSingle(property.name, x)
    }

    override protected def createCellRenderer(guiLayoutConfig: GuiLayoutConfig): TableCellRenderer with TableCellEditor = {

      val tmp = valueType match {
        case t@DofValueType.TBoolean =>
          new GenericDofCellEditor(widget = new BooleanWidget, defaultValue = t.defaultValue)
        case t@DofValueType.TString =>
          new GenericDofCellEditor(widget = new StringEditorWidget, defaultValue = t.defaultValue)
        case t@DofValueType.TNonemptyString =>
          new GenericDofCellEditor(widget = new StringEditorWidget, defaultValue = t.defaultValue)
        case t:DofValueType.TInt =>
          new GenericDofCellEditor(widget = new IntWidget, defaultValue = t.defaultValue)
        case t:DofValueType.TLong =>
          new GenericDofCellEditor(widget = new LongWidget, defaultValue = t.defaultValue)
        case t:DofValueType.TDecimal =>
          throw new RuntimeException("not supported yet") //todo: add support for decimal values
        case t:DofValueType.TFloatingPoint =>
          new GenericDofCellEditor(widget = new FloatingPointWidget, defaultValue = t.defaultValue)
        case t:DofValueType.TFloatingPointWithQuantity =>
          new GenericDofCellEditor(widget = new FloatingPointWithQuantityWidget(guiLayoutConfig, t.quantity), defaultValue = t.defaultValue)
        case t:DofValueType.TFloatingPointIntervalWithQuantity =>
          new GenericDofCellEditor(widget = new FloatingPointIntervalWithQuantityWidget(guiLayoutConfig, t.quantity), defaultValue = t.defaultValue)
        case t@DofValueType.TSimTimepoint =>
          new GenericDofCellEditor(widget = new SimTimepointWidget, defaultValue = t.defaultValue)
        case t@DofValueType.HHMMSS =>
          new GenericDofCellEditor(widget = new HumanReadableTimeAmountWidget(guiLayoutConfig), defaultValue = t.defaultValue)
        case other =>
          throw new RuntimeException(s"unsupported dof type: $other")
      }

      val internalCellEditor = tmp.asInstanceOf[GenericDofCellEditor[V]]

      //todo: wrap in a null editor if needed

      return
    }

    override def check(x: V): Option[String] = super.check(x)
  }

  /* AttrCollection */
  class AttrCollection(owner: TTModel, obj: DynamicObject, propertyName: String) extends TTNode[Int](owner: TTModel, obj: DynamicObject) {
    private val property = obj.dofClass.getProperty(propertyName).asInstanceOf[DofAttribute[_]]

    override def displayedName: String = property.displayName

    override def createChildNodes: Iterable[TTNode[_]] = {
      val coll: FastMapOnIntInterval[_] = obj.getCollection(propertyName)
      return coll.zipWithIndex map { case (element, i) => new AttrCollectionElement(owner, obj, propertyName, i) }
    }

    override def isEditable: Boolean = false

    override def value: Int = obj.getCollection(propertyName).size

    override protected def createCellRenderer: DofCellEditor[Int] = ???

    override def check(x: Int): Option[String] = ???
  }

  /* AttrCollectionElement */
  class AttrCollectionElement[V](owner: TTModel, obj: DynamicObject, propertyName: String, index: Int) extends TTNode[V](owner: TTModel, obj: DynamicObject) {
    private val property = obj.dofClass.getProperty(propertyName)

    override def displayedName: String = index.toString

    override def createChildNodes: Iterable[TTNode[_]] = Iterable.empty

    override def isEditable: Boolean = true

    override def value: A = obj.getCollection[A](propertyName)(index)

    override protected def createCellRenderer: DofCellEditor[A] = ???

    override def check(x: A): Option[String] = ???
  }

  /* LinkSingle */
  class LinkSingle(owner: TTModel, obj: DynamicObject, propertyName: String) extends TTNode[Option[DofClass]](owner, obj) {
    private val property: DofLink = obj.dofClass.getProperty(propertyName).asInstanceOf[DofLink]

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
          recreateChildNodes()
          //todo: broadcast change event here

        case Some(c) =>
          obj.setSingle(propertyName, Some(new DynamicObject(c)))
          recreateChildNodes()
        //todo: broadcast change event here
      }
    }

    override def check(x: Option[DofClass]): Option[String] = None

    override protected def createCellRenderer: DofCellEditor[Option[DofClass]] = ??? //todo
  }

  /* LinkCollection */
  class LinkCollection(owner: TTModel, obj: DynamicObject, propertyName: String) extends TTNode[Int](owner, obj) {
    private val property = obj.dofClass.getProperty(propertyName)

    override def displayedName: String = property.displayName

    override def createChildNodes: Iterable[TTNode[_]] = {
      val coll: FastMapOnIntInterval[DynamicObject] = obj.getCollection[DynamicObject](propertyName)
      return coll.zipWithIndex map { case (element, i) => new LinkCollectionElement(owner, obj, propertyName, i) }
    }

    override def isEditable: Boolean = false

    override def value: Int = this.childNodes.size

    override protected def createCellRenderer: DofCellEditor[Int] = ???

    override def check(x: Int): Option[String] = ???
  }

  /* LinkCollectionElement */
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
      recreateChildNodes()
      //todo: broadcast change event here
    }

    override protected def createCellRenderer: DofCellEditor[DofClass] = ???

    override def check(x: DofClass): Option[String] = ???
  }

  /* PropertiesGroup */
  class PropertiesGroup(owner: TTModel, obj: DynamicObject, groupName: String) extends TTNode[String](owner, obj) {

    override def displayedName: String = groupName

    override def createChildNodes: Iterable[TTNode[_]] = {
      for (propertyName <- obj.dofClass.perGroupDisplayOrder(groupName))
      yield createTTNodeFromOneFieldInDynamicObject(obj, obj.dofClass.getProperty(propertyName))
    }

    override def isEditable: Boolean = false

    override def value: String = groupName

    override protected def createCellRenderer: DofCellEditor[String] = ???

    override def check(x: String): Option[String] = ???
  }

}


