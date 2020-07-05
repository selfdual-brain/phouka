import java.awt.{Canvas, GraphicsEnvironment}

import javax.swing.JFrame

object MultipleScreensTest2 {

  def main(args: Array[String]): Unit = {
    val ge = GraphicsEnvironment.getLocalGraphicsEnvironment
    val gs = ge.getScreenDevices
    for (j <- 0 until gs.length) {
      val gd = gs(j)
      val gc = gd.getConfigurations
      for (i <- 0 until gc.length) {
        val f = new JFrame(gs(j).getDefaultConfiguration)
        val c = new Canvas(gc(i))
        val gcBounds = gc(i).getBounds
        val xoffs = gcBounds.x
        val yoffs = gcBounds.y
        f.getContentPane.add(c)
        f.setLocation((i * 50) + xoffs, (i * 60) + yoffs)
        f.setVisible(true)
      }
    }
  }

}
