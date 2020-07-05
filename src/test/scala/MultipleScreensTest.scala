import java.awt.GraphicsDevice
import java.awt.GraphicsEnvironment

object MultipleScreensTest {

  def main(args: Array[String]): Unit = {
    val screen = GraphicsEnvironment.getLocalGraphicsEnvironment.getDefaultScreenDevice
    GraphicsEnvironment.getLocalGraphicsEnvironment.getScreenDevices
    val bounds = screen.getDefaultConfiguration.getBounds
    println(bounds)
  }


}
