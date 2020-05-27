package cn.wycode.control.client

import cn.wycode.control.common.*
import com.alibaba.fastjson.JSON
import javafx.concurrent.Task
import java.net.Socket

const val INIT_PROCESS_START_MOUSE_SERVICE = 1
const val INIT_PROCESS_ENABLE_MOUSE_TUNNEL = 2
const val INIT_PROCESS_READ_KEYMAP = 3
const val INIT_PROCESS_CONNECT_MOUSE_SERVICE = 4
const val INIT_PROCESS_ENABLE_CONTROL_TUNNEL = 5
const val INIT_PROCESS_CONNECT_CONTROL_SERVICE = 6

var ENABLE_LOG = false

class InitialTask(val appendText: (text: String) -> Unit) : Task<Int>() {

    private val runtime = Runtime.getRuntime()
    lateinit var mouseSocket: Socket
    lateinit var controlSocket: Socket
    lateinit var keymap: Keymap
    lateinit var keymapString: String

    override fun call(): Int {
        updateMessage("start mouse service")
        if (!startMouseService()) return get()
        updateValue(INIT_PROCESS_START_MOUSE_SERVICE)

        updateMessage("enable mouse tunnel")
        if (!enableTunnel(MOUSE_PORT, MOUSE_SOCKET)) return get()
        updateValue(INIT_PROCESS_ENABLE_MOUSE_TUNNEL)

        updateMessage("read keymap")
        keymapString = javaClass.classLoader.getResource("keymap.json")!!.readText()
        keymap = JSON.parseObject(keymapString, Keymap::class.java)
        updateValue(INIT_PROCESS_READ_KEYMAP)

        updateMessage("connect to mouse")
        while (true) {
            mouseSocket = Socket("localhost", MOUSE_PORT)
            val signal = mouseSocket.getInputStream().read()
            appendText("overlay signal->$signal")
            if (signal == 1) break
            Thread.sleep(200)
        }
        updateValue(INIT_PROCESS_CONNECT_MOUSE_SERVICE)

        updateMessage("enable control tunnel")
        if (!enableTunnel(CONTROL_PORT, CONTROL_SOCKET)) return get()
        updateValue(INIT_PROCESS_ENABLE_CONTROL_TUNNEL)

        updateMessage("start control service")
        Thread(Runnable {
            startController()
        }).start()

        updateMessage("connecting to control")
        while (true) {
            controlSocket = Socket("localhost", CONTROL_PORT)
            val signal = controlSocket.getInputStream().read()
            appendText("control signal->$signal")
            if (signal == 1) break
            Thread.sleep(200)
        }
        updateValue(INIT_PROCESS_CONNECT_CONTROL_SERVICE)


        return get()
    }

    private fun startMouseService(): Boolean {
        try {
            var command = "adb shell am force-stop cn.wycode.control"
            appendText(command)
            var process = runtime.exec(command)
            process.waitFor()
            var result = process.inputStream.bufferedReader().readText()
            appendText(result)

            command = "adb shell am start-activity cn.wycode.control/.MainActivity"
            appendText(command)
            process = runtime.exec(command)
            process.waitFor()
            result = process.inputStream.bufferedReader().readText()
            appendText(result)
            return !result.contains("Error")
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    private fun startController(): Boolean {
        try {
            var command = "adb shell killall $CONTROL_SERVER"
            appendText(command)
            var process = runtime.exec(command)
            process.waitFor()
            val result = process.inputStream.bufferedReader().readText()
            appendText(result)

            //vm-options – VM 选项
            //cmd-dir –父目录 (/system/bin)
            //options –运行的参数 :
            //    –zygote
            //    –start-system-server
            //    –application (api>=14)
            //    –nice-name=nice_proc_name (api>=14)
            //start-class-name –包含main方法的主类  (com.android.commands.am.Am)
            //main-options –启动时候传递到main方法中的参数
            val args = if (ENABLE_LOG) "--debug" else ""
            command =
                "adb shell CLASSPATH=$CONTROL_PATH app_process / --nice-name=$CONTROL_SERVER $CONTROL_SERVER $args"
            appendText(command)
            process = runtime.exec(command)
            while (process.isAlive && !isCancelled) {
                appendText(process.inputStream.bufferedReader().readLine())
            }
            appendText(process.inputStream.bufferedReader().readText())
            appendText(process.errorStream.bufferedReader().readText())

            mouseSocket.close()
            controlSocket.close()
            appendText("all closed!")
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    private fun enableTunnel(port: Int, socketName: String): Boolean {
        try {
            val command = "adb forward tcp:$port localabstract:$socketName"
            appendText(command)
            val process = runtime.exec(command)
            process.waitFor()
            val result = process.inputStream.bufferedReader().readText()
            appendText(result)
            return result.contains(port.toString()) || "" == result
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }
}