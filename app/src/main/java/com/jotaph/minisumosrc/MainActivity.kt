package com.jotaph.minisumosrc

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import android.speech.tts.TextToSpeech
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale
import java.util.UUID
import kotlin.concurrent.thread

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val bluetoothAdapter: BluetoothAdapter? by lazy { BluetoothAdapter.getDefaultAdapter() }
    private var socket: BluetoothSocket? = null
    @Volatile private var reading = false
    private lateinit var deviceSpinner: Spinner
    private lateinit var status: TextView
    private lateinit var robots: TextView
    private lateinit var timer: TextView
    private lateinit var attacks: TextView
    private lateinit var announcement: TextView
    private lateinit var connectButton: Button
    private var pairedDevices: List<BluetoothDevice> = emptyList()
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var lastAnnouncement = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        tts = TextToSpeech(this, this)
        buildUi()
        refreshPairedDevices()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(20, 12, 20, 12)
            setBackgroundColor(Color.rgb(12, 16, 24))
        }
        status = label("Bluetooth: desconectado", 16, Color.LTGRAY)
        root.addView(status, matchWrap())
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        deviceSpinner = Spinner(this)
        controls.addView(deviceSpinner, LinearLayout.LayoutParams(0, 52, 1f))
        val refresh = Button(this).apply { text = "Actualizar" ; setOnClickListener { refreshPairedDevices() } }
        controls.addView(refresh)
        connectButton = Button(this).apply {
            text = "Conectar"
            setOnClickListener { if (reading) disconnect() else connectSelected() }
        }
        controls.addView(connectButton)
        root.addView(controls, matchWrap())

        robots = label("ALPHA  vs  GAMMA", 36, Color.WHITE).apply { setPadding(0, 20, 0, 8) }
        root.addView(robots, matchWrap())
        timer = label("0:00:00", 68, Color.rgb(255, 210, 65))
        root.addView(timer, matchWrap())
        attacks = label("ATAQUES  0       |       0  ATAQUES", 22, Color.LTGRAY).apply { setPadding(0, 8, 0, 14) }
        root.addView(attacks, matchWrap())
        announcement = label("PREPARADOS PARA LA BATALLA", 28, Color.rgb(70, 220, 130)).apply {
            setPadding(12, 10, 12, 10)
        }
        root.addView(announcement, matchWrap())
        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)
    }

    private fun label(textValue: String, size: Float, color: Int) = TextView(this).apply {
        text = textValue
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER
        setTypeface(null, android.graphics.Typeface.BOLD)
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun hasBluetoothPermission(): Boolean =
        Build.VERSION.SDK_INT < 31 ||
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun refreshPairedDevices() {
        if (Build.VERSION.SDK_INT >= 31 && !hasBluetoothPermission()) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 10)
            return
        }
        val adapter = bluetoothAdapter
        if (adapter == null) {
            status.text = "Este dispositivo no tiene Bluetooth"
            return
        }
        if (!adapter.isEnabled) {
            status.text = "Activa Bluetooth en los ajustes del teléfono"
            return
        }
        pairedDevices = try { adapter.bondedDevices.toList().sortedBy { it.name ?: it.address } }
        catch (_: SecurityException) { emptyList() }
        val names = pairedDevices.map { "${it.name ?: "Dispositivo"} — ${it.address}" }
        deviceSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names.ifEmpty { listOf("Empareja primero la ESP32 en Android") })
        status.text = if (pairedDevices.isEmpty()) "Bluetooth: no hay dispositivos emparejados" else "Selecciona la ESP32 emparejada"
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 10) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) refreshPairedDevices()
            else status.text = "Se necesita permiso Bluetooth para conectar"
        }
    }

    private fun connectSelected() {
        if (!hasBluetoothPermission()) {
            if (Build.VERSION.SDK_INT >= 31) requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 10)
            return
        }
        if (pairedDevices.isEmpty()) {
            Toast.makeText(this, "Empareja primero la ESP32 desde los ajustes Bluetooth", Toast.LENGTH_LONG).show()
            return
        }
        val device = pairedDevices.getOrNull(deviceSpinner.selectedItemPosition) ?: return
        status.text = "Conectando con ${device.name ?: device.address}..."
        connectButton.isEnabled = false
        thread {
            try {
                bluetoothAdapter?.cancelDiscovery()
                val newSocket = device.createRfcommSocketToServiceRecord(sppUuid)
                newSocket.connect()
                socket = newSocket
                reading = true
                runOnUiThread {
                    status.text = "Conectado: ${device.name ?: device.address}"
                    connectButton.text = "Desconectar"
                    connectButton.isEnabled = true
                }
                listen(newSocket)
            } catch (e: Exception) {
                reading = false
                try { socket?.close() } catch (_: Exception) {}
                socket = null
                runOnUiThread {
                    status.text = "No se pudo conectar: ${e.localizedMessage ?: "revisa el Bluetooth SPP"}"
                    connectButton.text = "Conectar"
                    connectButton.isEnabled = true
                }
            }
        }
    }

    private fun listen(activeSocket: BluetoothSocket) {
        try {
            val reader = BufferedReader(InputStreamReader(activeSocket.inputStream, Charsets.UTF_8))
            while (reading) {
                val line = reader.readLine() ?: break
                if (line.isNotBlank()) processMessage(line)
            }
        } catch (_: Exception) {
            // La desconexión también puede cerrar el flujo de lectura.
        } finally {
            reading = false
            try { activeSocket.close() } catch (_: Exception) {}
            if (socket === activeSocket) socket = null
            runOnUiThread {
                status.text = "Bluetooth: desconectado"
                connectButton.text = "Conectar"
                connectButton.isEnabled = true
            }
        }
    }

    private fun processMessage(line: String) {
        try {
            val json = JSONObject(line)
            val robot1 = json.optString("robot1", json.optString("r1", "ALPHA"))
            val robot2 = json.optString("robot2", json.optString("r2", "GAMMA"))
            val time = json.optString("timer", json.optString("tiempo", "0:00:00"))
            val a1 = json.optInt("attacks1", json.optInt("ataques1", 0))
            val a2 = json.optInt("attacks2", json.optInt("ataques2", 0))
            val speech = json.optString("announcement", json.optString("anuncio", ""))
            runOnUiThread {
                robots.text = "$robot1  vs  $robot2"
                timer.text = time
                attacks.text = "ATAQUES  $a1       |       $a2  ATAQUES"
                if (speech.isNotBlank()) {
                    announcement.text = speech
                    if (speech != lastAnnouncement && ttsReady) tts?.speak(speech, TextToSpeech.QUEUE_FLUSH, null, "match_announcement")
                    lastAnnouncement = speech
                }
            }
        } catch (_: Exception) {
            // Ignora líneas que no sean JSON válido.
        }
    }

    private fun disconnect() {
        reading = false
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        status.text = "Bluetooth: desconectado"
        connectButton.text = "Conectar"
    }

    override fun onInit(statusCode: Int) {
        ttsReady = statusCode == TextToSpeech.SUCCESS
        if (ttsReady) tts?.language = Locale("es", "CO")
    }

    override fun onDestroy() {
        disconnect()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
