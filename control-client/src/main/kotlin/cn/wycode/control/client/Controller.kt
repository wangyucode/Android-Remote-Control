package cn.wycode.control.client

import cn.wycode.control.common.Position
import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.scene.canvas.Canvas
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseEvent
import javafx.scene.paint.Color
import javafx.stage.Screen
import java.net.URL
import java.util.*

var RATIO = 3.0
var OFFSET = Position(0, 0)
var SCREEN = Position(0, 0)
var CANVAS = Position(0, 0)

class Controller : Initializable {

    @FXML
    lateinit var canvas: Canvas

    private val initialTask = InitialTask()
    private lateinit var readTask: ReadTask
    private val connections = Connections()
    private val mouseHandler = MouseHandler(connections)
    private val keyHandler = KeyHandler(connections)

    override fun initialize(location: URL?, resources: ResourceBundle?) {
        initialTask.valueProperty().addListener { _, _, value ->
            when (value) {
                INIT_PROCESS_READ_KEYMAP -> {
                    keyHandler.initButtons(initialTask.keymap)
                    connections.initButtons(initialTask.keymap)
                }
                INIT_PROCESS_CONNECT_MOUSE_SERVICE -> onMouseServiceConnected()
                INIT_PROCESS_CONNECT_CONTROL_SERVICE -> onControlServiceConnected()
            }
        }

        Thread(initialTask).start()
    }

    private fun onControlServiceConnected() {
        println("client::connected to control service!")
        mouseHandler.controlConnected = true
        connections.controlOutputStream = initialTask.controlSocket.getOutputStream()
        canvas.scene.addEventHandler(KeyEvent.ANY, keyHandler)
        connections.scene = canvas.scene
        canvas.scene.window.focusedProperty().addListener { _, _, newValue -> keyHandler.focusChange(newValue) }
    }

    private fun onMouseServiceConnected() {
        println("client::connected to overlay service!")
        mouseHandler.mouseConnected = true
        connections.mouseOutputStream = initialTask.mouseSocket.getOutputStream()

        connections.sendKeymap(initialTask.keymapString)

        val screen = Screen.getPrimary()
        val screenBounds = screen.bounds

        val graphics = canvas.graphicsContext2D
        graphics.fill = Color.DARKOLIVEGREEN

        readTask = ReadTask(initialTask.mouseSocket)
        readTask.valueProperty().addListener { _, _, _ ->
            // The client screen is wider than the server
            RATIO =
                if (screenBounds.width / screenBounds.height > SCREEN.x.toDouble() / SCREEN.y) {
                    SCREEN.y / screenBounds.height
                } else {
                    SCREEN.x / screenBounds.width
                }
            canvas.width = SCREEN.x / RATIO
            canvas.height = SCREEN.y / RATIO

            CANVAS.x = canvas.width.toInt()
            CANVAS.y = canvas.height.toInt()

            canvas.layoutX = screenBounds.width / 2 - canvas.width / 2
            canvas.layoutY = 0.0

            val window = canvas.scene.window
            //window.sizeToScene()
            window.y = 0.0
            window.x = screenBounds.width / 2 - window.width / 2

            OFFSET.x = (window.x + canvas.layoutX).toInt()
            OFFSET.y = (window.y + canvas.layoutY).toInt()

            graphics.fillRect(0.0, 0.0, canvas.width, canvas.height)
        }
        Thread(readTask).start()

        canvas.addEventHandler(MouseEvent.ANY, mouseHandler)
    }

    fun stop() {
        readTask.cancel()
        connections.close()
        initialTask.cancel()
    }

}
