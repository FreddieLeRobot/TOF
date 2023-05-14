package com.example.tof_ble_graph

import android.Manifest
import android.app.Activity
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*

private const val CHART_LABEL = "DATA_CHART"
private const val ENABLE_BLUETOOTH_REQUEST_CODE = 1
private const val RUNTIME_PERMISSION_REQUEST_CODE = 2

// Threshold constant
private const val PRESSURE_THRESHOLD = 0.05f

//UUIDs
private const val CCC_DESCRIPTOR_UUID = "00002902-0000-1000-8000-00805f9b34fb"
private const val DEVICEINFO_SERV_UUID = "0000180a-0000-1000-8000-00805f9b34fb"
private const val PRESSURE_CHAR_UUID = "00002a6d-0000-1000-8000-00805f9b34fb"
private const val SLEEP_CHAR_UUID = "00002B42-0000-1000-8000-00805f9b34fb"

class MainActivity : AppCompatActivity() {

    // CSV Data Type for writer
    data class DataLog(
        var time: LocalDateTime,
        var pressure: Float,
        var sleep: Boolean,
        var peakCount: Int,
        var peakRatio: String
    )
    // Peak Handler Data Class
    data class PeakData(
        var last: Float,
        var peak: Float,
        var peakList: MutableList<Float>,
        var peakTimer: Long, // Must cast all .now() calls to Epoch Seconds, makes comparison easier.
        var newPeak: Boolean
    )

    private var peakData = PeakData(0.0f,0.0f, peakList = mutableListOf(), LocalDateTime.now().toEpochSecond(ZoneOffset.UTC), false)

    lateinit var writeFile: File

    var avgList = mutableListOf<Float>()
    var movingAvg = 0.00f

    // Bluetooth low energy variables
    private val bleScanner by lazy {
        bluetoothAdapter.bluetoothLeScanner
    }
    private val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }
    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()

    lateinit var readBytes: ByteArray
    private var subscribedPressure = false


    // Bluetooth scan results
    private val scanResults = mutableListOf<ScanResult>()    // Bluetooth scan results

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            with(result.device){
                Log.i("ScanCallback", "Found BLE device! Name: ${name ?: "Unnamed"}, address: $address")
                var duplicate = false
                scanResults.forEach{
                    if (result.device.address == it.device.address){
                        duplicate = true
                    }
                }
                if (!duplicate) {
                    scanResults.add(result)
                    updateSpinner(scanResults)
                }
            }
        }
    }
    private var isScanning = false

    // GATT Callback

    lateinit var bluetoothGatt: BluetoothGatt

    private val gattCallback = object: BluetoothGattCallback(){
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceAddress = gatt.device.address

            if (status == BluetoothGatt.GATT_SUCCESS){
                if (newState == BluetoothProfile.STATE_CONNECTED){
                    Log.w("BluetoothGattCallback", "Successfully connected to $deviceAddress")
                    bluetoothGatt = gatt
                    Handler(Looper.getMainLooper()).post {
                        bluetoothGatt.discoverServices()
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.w("BluetoothGattCallback", "Successfully disconnected from $deviceAddress")
                    gatt.close()
                }
            } else {
                Log.w("BluetoothGattCallback", "Error $status encountered for $deviceAddress. Disconnecting....")
                gatt.close()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            with(gatt) {
                Log.w("BluetoothGattCallback", "Discovered ${services.size} services for ${device.address}")
                printGattTable()
                runOnUiThread{
                    subscribedPressure = true
                    subBLEPressure()
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.w("BluetoothGattCallback","ATT MTU changed to $mtu, success: ${status == BluetoothGatt.GATT_SUCCESS}")
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            with(characteristic) {
                when (status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        Log.i("BluetoothGattCallback", "Read characteristic $uuid:\n${value.toHex()}")
                        readBytes = value
                        if (this.uuid == UUID.fromString(PRESSURE_CHAR_UUID)){
                            runOnUiThread {
                                pressureHandler(value)
                            }
                        } else {
                            Log.i("UnknownBLECharacteristic",this.uuid.toString())
                        }
                    }
                    BluetoothGatt.GATT_READ_NOT_PERMITTED -> {
                        Log.e("BluetoothGattCallback", "Read not permitted for $uuid!")
                    }
                    else -> {
                        Log.e("BluetoothGattCallback", "Characteristic read failed for $uuid, error: $status")
                    }
                }
            }
        }
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            with(characteristic) {
                when (status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        Log.i("BluetoothGattCallback", "Wrote to characteristic $uuid | value: ${value.toHex()}")
                    }
                    BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH -> {
                        Log.e("BluetoothGattCallback", "Write exceeded connection ATT MTU!")
                    }
                    BluetoothGatt.GATT_WRITE_NOT_PERMITTED -> {
                        Log.e("BluetoothGattCallback", "Write not permitted for $uuid!")
                    }
                    else -> {
                        Log.e("BluetoothGattCallback", "Characteristic write failed for $uuid, error: $status")
                    }
                }
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
            when (status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        // TODO: Handle different descriptor writes as the app subscribes to more services.
                    }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            with(characteristic) {
                //Log.i("BluetoothGattCallback", "Characteristic $uuid changed | value: ${value.toHex()}")
                readBytes = value
                if (this.uuid == UUID.fromString(PRESSURE_CHAR_UUID)){
                    runOnUiThread {
                        pressureHandler(value)
                    }
                } else {
                    Log.i("UnknownBLECharacteristic",this.uuid.toString())
                }
            }
        }
    }

    private fun BluetoothGatt.printGattTable() {
        if (services.isEmpty()) {
            Log.i("printGattTable", "No service and characteristic available, call discoverServices() first?")
            return
        }
        services.forEach { service ->
            val characteristicsTable = service.characteristics.joinToString(
                separator = "\n|--",
                prefix = "|--"
            ) { it.uuid.toString() }
            Log.i("printGattTable", "\nService ${service.uuid}\nCharacteristics:\n$characteristicsTable"
            )
        }
    }


    // Declare floating action buttons for animations
    lateinit var bleOptionsBtn: FloatingActionButton
    lateinit var bleConnectBtn: FloatingActionButton
    lateinit var bleDisconnectBtn: FloatingActionButton

    // Declare buttons for chart
    lateinit var pauseBtn: Button
    lateinit var resetBtn: Button

    //Declare buttons for functionality
    lateinit var sleepBtn: ToggleButton
    lateinit var logBtn: ToggleButton

    // Declare textview for data entry
    lateinit var dataText: TextView
    // Declare textview for moving average
    lateinit var avgText: TextView

    // Declare textview for peak avgs and ratios
    lateinit var countText: TextView
    lateinit var ratioText: TextView

    // Declare spinner for devices found (and related adapter)
    lateinit var spinner: Spinner
    lateinit var adapter: ArrayAdapter<String>

    // Declare line Chart
    lateinit var lineChart: LineChart

    //Animations for the bluetooth connect buttons
    private val rotateOpen: Animation by lazy { AnimationUtils.loadAnimation(this, R.anim.rotate_open_anim) }
    private val rotateClosed: Animation by lazy { AnimationUtils.loadAnimation(this, R.anim.rotate_closed_anim) }
    private val fromBottom: Animation by lazy { AnimationUtils.loadAnimation(this, R.anim.from_bottom) }
    private val toBottom: Animation by lazy { AnimationUtils.loadAnimation(this, R.anim.to_bottom) }

    // boolean to determine if menu is expanded or not
    private var bleClicked = false

    // Mutable list for live entry of data to chart
    private val lineData = mutableListOf<Entry>()
    private var _lineDataSet = LineDataSet(lineData, CHART_LABEL)


    // When first starting app
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //init FABs
        bleOptionsBtn = findViewById(R.id.bleOptions_btn)
        bleConnectBtn = findViewById(R.id.bleConnect_btn)
        bleDisconnectBtn = findViewById(R.id.bleDisconnect_btn)


        // Listeners for floating action buttons
        bleOptionsBtn.setOnClickListener{
            onBleOptionsClicked()
        }
        bleConnectBtn.setOnClickListener{
            Toast.makeText(this, "Connecting to BLE Device...", Toast.LENGTH_SHORT).show()
            onBleConnectClicked()
        }
        bleDisconnectBtn.setOnClickListener{
            onBleDisconnectClicked()
        }

        // init Btns
        pauseBtn = findViewById(R.id.button_stop)
        resetBtn = findViewById(R.id.reset_graph)
        sleepBtn = findViewById(R.id.sleep_toggle)
        logBtn = findViewById(R.id.log_toggle)


        //Listeners for btns
        pauseBtn.setOnClickListener{
            if (!subscribedPressure) {
                subscribedPressure = true
                subBLEPressure()
            }
            else{
                subscribedPressure = false
                unsubBLEPressure()
            }
        }
        resetBtn.setOnClickListener{
            resetLineChartData()
        }

        sleepBtn.setOnClickListener {
            onSleepToggle()
        }

        logBtn.setOnClickListener {
            onLogToggle()
        }

        // Init spinner
        spinner = findViewById(R.id.BleDrop)

        // Init TextViews
        dataText = findViewById(R.id.DataBox)
        avgText = findViewById(R.id.AvgView)

        // Init Table
        countText = findViewById(R.id.count)
        ratioText = findViewById(R.id.ratio)

        // Init chart
        lineChart = findViewById(R.id.lineChart)
        setLineChartStyle(lineChart)
    }

    // When app is running
    override fun onResume() {
        super.onResume()
        if (!bluetoothAdapter.isEnabled){
            promptEnableBluetooth()
        }
        startBleScan()
    }

    // Actions for results of prompts
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            ENABLE_BLUETOOTH_REQUEST_CODE -> {
                if (resultCode != Activity.RESULT_OK) {
                    promptEnableBluetooth()
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when(requestCode){
            RUNTIME_PERMISSION_REQUEST_CODE -> {
                val containsPermanentDenial = permissions.zip(grantResults.toTypedArray()).any {
                    it.second == PackageManager.PERMISSION_DENIED &&
                            !ActivityCompat.shouldShowRequestPermissionRationale(this, it.first)
                }
                val containsDenial = grantResults.any { it == PackageManager.PERMISSION_DENIED }
                val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
                when {
                    containsPermanentDenial -> {
                        // TODO: Handle permanent denial
                        // Note: user will need to navigate to app settings
                    }
                    containsDenial -> {
                        requestRelevantRuntimePermissions()
                    }
                    allGranted && hasRequiredRuntimePermissions() -> {
                        startBleScan()
                    }
                    else -> {
                        // Unexpected scenario
                        recreate()
                    }
                }
            }
        }
    }

    // Functions for Bluetooth buttons
    private fun onBleOptionsClicked() {
        setVisibility(bleClicked)
        setAnimation(bleClicked)
        setClickable(bleClicked)
        bleClicked = !bleClicked
    }

    private fun onSleepToggle() {
        val sleepUuid = UUID.fromString(SLEEP_CHAR_UUID)
        val deviceInfoUuid = UUID.fromString(DEVICEINFO_SERV_UUID)
        val sleepChar = bluetoothGatt
            .getService(deviceInfoUuid).getCharacteristic(sleepUuid)
        var payload = byteArrayOf(0x00)
        // Change payload number for sleep value depending on sleep value.
        if (sleepBtn.isChecked){
            if (logBtn.isChecked) {
                log(writeFile, 0.00f, true)
            }
        }
        else {
            payload = byteArrayOf(0x01)
        }
        writeBLESleep(sleepChar,payload)
    }

    private fun onLogToggle(){
        // TODO: Add back in datetime to filename
        val localTime = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("MMdd_HHmm")
        val time = localTime.format(formatter)
        val csvName = "TOF_Data_$time.csv"
        val fileCreated = createFile(csvName)
        if (fileCreated.isFile) {
            writeFile = fileCreated
            FileOutputStream(writeFile).apply { createCsv() }
        }
    }

    private fun log(file: File, pressure: Float, sleep: Boolean){
        val logInfo = DataLog(LocalDateTime.now(),pressure, sleep, peakData.peakList.size, ratioText.text.toString())
        val writer = FileWriter(file,true)
        writer.write("${logInfo.time},${logInfo.pressure},${logInfo.sleep},${logInfo.peakCount},${logInfo.peakRatio}")
        writer.write(System.getProperty("line.separator"))
        writer.flush()
    }

    private fun onBleConnectClicked(){
        if (isScanning){
            stopBleScan()
        }
        if (!spinner.selectedItem.toString().isNullOrEmpty()){
            val text = spinner.selectedItem.toString()
            scanResults.forEach {
                if (text == it.device.name){
                    connectToBLE(it)
                }
            }
        }
    }

    private fun connectToBLE(device: ScanResult) {
        with(device.device){
            Log.w("BluetoothLE", "Connecting to $address")
            connectGatt(this@MainActivity,false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        }
    }

    private fun onBleDisconnectClicked(){
        if (this::bluetoothGatt.isInitialized){
            Toast.makeText(this, "Disconnecting BLE Device...", Toast.LENGTH_SHORT).show()
        bluetoothGatt.disconnect()
        }
        else {
            Toast.makeText(this, "No BLE Device Connected!", Toast.LENGTH_SHORT).show()
        }
        if (!isScanning){
            startBleScan()
        }
    }

    // Functions for Bluetooth button animations
    private fun setClickable(clicked: Boolean) {
        if (clicked){
            bleConnectBtn.isClickable = false
            bleDisconnectBtn.isClickable = false
        }
        else{
            bleConnectBtn.isClickable = true
            bleDisconnectBtn.isClickable = true
        }
    }

    private fun setVisibility(clicked: Boolean) {
        if(!clicked){
            bleConnectBtn.visibility = View.VISIBLE
            bleDisconnectBtn.visibility = View.VISIBLE
        }
        else{
            bleConnectBtn.visibility = View.INVISIBLE
            bleDisconnectBtn.visibility = View.INVISIBLE
        }
    }

    private fun setAnimation(clicked: Boolean) {
        if(!clicked){
            bleConnectBtn.startAnimation(fromBottom)
            bleDisconnectBtn.startAnimation(fromBottom)
            bleOptionsBtn.startAnimation(rotateOpen)
        }
        else{
            bleConnectBtn.startAnimation(toBottom)
            bleDisconnectBtn.startAnimation(toBottom)
            bleOptionsBtn.startAnimation(rotateClosed)
        }
    }

    // Functions for spinner
    private fun updateSpinner(results: List<ScanResult>) {
        var list = ArrayList<String>()
        results.forEach{
            // Correctly working TOF Devices will always have a name, so filter out unnamed devices
            if (!it.device.name.isNullOrEmpty()) {
                list.add(it.device.name ?:"Unnamed")
            }
        }
        adapter = ArrayAdapter<String>(
            applicationContext,
        android.R.layout.simple_spinner_item, list)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
    }

    // Function for data box
    private fun updateDataBox(str: String){
        dataText.text = str
    }

    /* Functions for lineChart
        Adding and resetting, themes etc.
     */
    private fun setLineChartStyle(lineChart: LineChart) = lineChart.apply{
        axisRight.isEnabled = false

        // Y Axis not changed for now
        // X Axis
        xAxis.apply {
            axisMinimum = 0f
            isGranularityEnabled = true
            setDrawGridLines(false)
            position = XAxis.XAxisPosition.BOTTOM
        }

        setTouchEnabled(true)
        isDragEnabled = true

        description = null
        legend.isEnabled = false

        //Do not want to highlight, only pan/scroll/zoom
        isHighlightPerTapEnabled = false
        isHighlightPerDragEnabled = false

    }

    private fun resetLineChartData() {
        lineChart.clearValues()
        lineData.clear()
        lineChart.invalidate()
        initPeakData()
        movingAvg = 0.0f
        avgList = mutableListOf<Float>()
    }

    private fun lineChartAddData(fl: Float) {
            var index = 0f
            if (lineData.isNotEmpty()) {
                index = lineData.last().x + 1f
                if (lineData.lastIndex >= 99){
                    peakAndAvgCheck(fl)
                }
            }

            lineData.add(Entry(index, fl))

            _lineDataSet = LineDataSet(lineData, CHART_LABEL)
            _lineDataSet.setDrawCircles(false)


            lineChartUpdate()
    }

    private fun lineChartUpdate(){
        lineChart.data = LineData(_lineDataSet)
        lineChart.notifyDataSetChanged()
        lineChart.setVisibleXRangeMaximum(3000F)
        lineChart.setVisibleXRangeMinimum(3000F)
        lineChart.moveViewToX(_lineDataSet.xMax)
    }

    // BLE Functions
    private fun promptEnableBluetooth() {
        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                    ENABLE_BLUETOOTH_REQUEST_CODE
                )
                return
            }
            Toast.makeText(this,"Bluetooth must be on for correct operation.", Toast.LENGTH_LONG).show()
            startActivityForResult(enableBtIntent, ENABLE_BLUETOOTH_REQUEST_CODE)
        }
    }

    private fun startBleScan() {
        Toast.makeText(this,"Starting BLE Scan",Toast.LENGTH_SHORT).show()
        if (!hasRequiredRuntimePermissions()) {
            requestRelevantRuntimePermissions()
        } else {
            bleScanner.startScan(null, scanSettings, scanCallback)
            isScanning = true
        }
    }

    private fun stopBleScan() {
        bleScanner.stopScan(scanCallback)
        isScanning = false
    }


    // Bluetooth Gatt Characteristic checks

    fun BluetoothGattCharacteristic.isReadable(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_READ)

    fun BluetoothGattCharacteristic.isWritable(): Boolean =
    containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE)

    fun BluetoothGattCharacteristic.isWritableWithoutResponse(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)

    fun BluetoothGattCharacteristic.containsProperty(property: Int): Boolean {
        return properties and property != 0
    }

    fun BluetoothGattCharacteristic.isIndicatable(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_INDICATE)

    fun BluetoothGattCharacteristic.isNotifiable(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_NOTIFY)


    // BLE Reads (May be used later)
    private fun readBLEPressure() {

        val pressureUuid = UUID.fromString(PRESSURE_CHAR_UUID)
        val deviceInfoUuid = UUID.fromString(DEVICEINFO_SERV_UUID)
        val pressureChar = bluetoothGatt
            .getService(deviceInfoUuid)?.getCharacteristic(pressureUuid)
        if (pressureChar?.isReadable() == true) {
            bluetoothGatt.readCharacteristic(pressureChar)
        }
    }

    // BLE Writes
    private fun writeBLESleep(characteristic: BluetoothGattCharacteristic, payload: ByteArray) {
        val writeType = when {
            characteristic.isWritable() -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            characteristic.isWritableWithoutResponse() -> {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            }
            else -> error("Characteristic ${characteristic.uuid} cannot be written to")
        }
        bluetoothGatt.let { gatt ->
            characteristic.writeType = writeType
            characteristic.value = payload
            gatt.writeCharacteristic(characteristic)
        } ?: error("Not Connected to BLE device!")
    }


    // BLE Subscribes
    private fun subBLEPressure(){
        val pressureUuid = UUID.fromString(PRESSURE_CHAR_UUID)
        val deviceInfoUuid = UUID.fromString(DEVICEINFO_SERV_UUID)
        val pressureChar = bluetoothGatt
            .getService(deviceInfoUuid).getCharacteristic(pressureUuid)
        enableNotifications(pressureChar)
    }

    private fun unsubBLEPressure(){
        val pressureUuid = UUID.fromString(PRESSURE_CHAR_UUID)
        val deviceInfoUuid = UUID.fromString(DEVICEINFO_SERV_UUID)
        val pressureChar = bluetoothGatt
            .getService(deviceInfoUuid).getCharacteristic(pressureUuid)
        disableNotifications(pressureChar)
    }

    private fun enableNotifications(characteristic: BluetoothGattCharacteristic) {
        val cccdUuid = UUID.fromString(CCC_DESCRIPTOR_UUID)
        val payload = when {
            characteristic.isIndicatable() -> BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            characteristic.isNotifiable() -> BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            else -> {
                Log.e("ConnectionManager", "${characteristic.uuid} doesn't support notifications/indications")
                return
            }
        }

        characteristic.getDescriptor(cccdUuid)?.let { cccDescriptor ->
            if (bluetoothGatt?.setCharacteristicNotification(characteristic, true) == false) {
                Log.e("ConnectionManager", "setCharacteristicNotification failed for ${characteristic.uuid}")
                return
            }
            writeDescriptor(cccDescriptor, payload)
        } ?: Log.e("ConnectionManager", "${characteristic.uuid} doesn't contain the CCC descriptor!")
    }

    private fun disableNotifications(characteristic: BluetoothGattCharacteristic) {
        if (!characteristic.isNotifiable() && !characteristic.isIndicatable()) {
            Log.e("ConnectionManager", "${characteristic.uuid} doesn't support indications/notifications")
            return
        }

        val cccdUuid = UUID.fromString(CCC_DESCRIPTOR_UUID)
        characteristic.getDescriptor(cccdUuid)?.let { cccDescriptor ->
            if (bluetoothGatt?.setCharacteristicNotification(characteristic, false) == false) {
                Log.e("ConnectionManager", "setCharacteristicNotification failed for ${characteristic.uuid}")
                return
            }
            writeDescriptor(cccDescriptor, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)
        } ?: Log.e("ConnectionManager", "${characteristic.uuid} doesn't contain the CCC descriptor!")
    }

    private fun writeDescriptor(descriptor: BluetoothGattDescriptor, payload: ByteArray) {
        bluetoothGatt?.let { gatt ->
            descriptor.value = payload
            gatt.writeDescriptor(descriptor)
        } ?: error("Not connected to a BLE device!")
    }

    // Data Handlers
    private fun pressureHandler(readBytesPressure: ByteArray) {
        updateDataBox("Pressure Data: " + readBytesPressure.toHex())
        //thread(name = "Thread-LineChart") {
        //    lineChartAddData(readBytesPressure.toFloat())
        //}
        lineChartAddData(readBytesPressure.toFloat())
        if(logBtn.isChecked) {
            log(writeFile, readBytesPressure.toFloat(), false)
        }
    }

    //Check for permissions
    private fun Context.hasPermission(permissionType: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permissionType) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun Context.hasRequiredRuntimePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermission(Manifest.permission.BLUETOOTH_SCAN) &&
                    hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // Request permissions
    private fun Activity.requestRelevantRuntimePermissions() {
        if (hasRequiredRuntimePermissions()) { return }
        when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> {
                requestLocationPermission()
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                requestBluetoothPermissions()
            }
        }
    }

    private fun requestLocationPermission() {

        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            RUNTIME_PERMISSION_REQUEST_CODE
        )
        }

    private fun requestBluetoothPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            ),
            RUNTIME_PERMISSION_REQUEST_CODE
        )
    }

    // Byte Array Manipulation
    fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

    private fun ByteArray.toFloat(): Float {
        val buffer = ByteBuffer.wrap(this)
            .order(ByteOrder.LITTLE_ENDIAN)
        return buffer.float
    }

    // Float to String formatting
    fun Float.format(digits: Int) = "%.${digits}f".format(this)

    // CSV Writing functions

    private fun OutputStream.createCsv(){
            val writer = bufferedWriter()
            writer.write("Date/Time,Pressure,Sleep Mode,Peak Count,Peak Ratio")
            writer.newLine()
            writer.flush()
    }

    private fun createFile(name:String):File{
        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),name)

        val isNewFileCreated: Boolean = file.createNewFile()

        if(!isNewFileCreated) {
            Log.e("FileOutputStream", "File could not be created successfully!")
        }
        return file
    }

    // Peak detection and baseline averaging functions
    private fun peakAndAvgCheck(dataPoint: Float){
        if (lineData.lastIndex == 99){
            lineData.forEach {
                avgList.add(it.y)
            }
            var sum = avgList.sum()
            movingAvg = sum / 100.00f
            avgText.text = movingAvg.toString()
            return
        }
        // Check for peaks, and if no peak recalculate moving average
        if (!peakCheck(dataPoint)) {
            //Push
            avgList.removeAt(0)
            avgList.add(dataPoint)
            //Sum moving average list
            var sum = avgList.sum()
            movingAvg = sum / 100.00f
            avgText.text = "Avg: " + movingAvg.format(4)

        }
        else{
            peakHandler(dataPoint)
        }
    }

    private fun peakHandler(dataPoint: Float) {
        if (dataPoint > peakData.last) {
            peakData.newPeak = true
            peakData.peakTimer = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
            peakData.last = dataPoint
        }
        else if (dataPoint < peakData.last) {
            if (peakData.newPeak){
                peakData.peak = peakData.last
                peakData.peakList.add(peakData.peak)
                peakData.newPeak = false
                peakUIHandler()
            }
            //Check timer to see if it has been too long since last peak
            if (LocalDateTime.now().toEpochSecond(ZoneOffset.UTC) - peakData.peakTimer > 2 ) {
                initPeakData()
            }
            peakData.last = dataPoint
        }

    }

    private fun initPeakData() {
        peakData = PeakData(0.0f,0.0f, peakList = mutableListOf(), LocalDateTime.now().toEpochSecond(ZoneOffset.UTC), false)
    }

    private fun peakUIHandler(){
        val index = peakData.peakList.size
        if (index == 1){
            initTable()
            countText.text = index.toString()
            ratioText.text = "N/A"
        }
        else if (index <= 8) {
            countText.text = index.toString()
            ratioText.text = ((peakData.peakList[index-1]-movingAvg)/(peakData.peakList[0]-movingAvg)).format(2)
        }
        else {
            countText.text = index.toString() + "!"
            ratioText.text = "Error!"
            Toast.makeText(this, "Detecting too many peaks. Reset graph or check pressure of device.", Toast.LENGTH_SHORT).show()
        }

    }

    private fun peakCheck(fl: Float): Boolean{
        if (fl > (movingAvg+ PRESSURE_THRESHOLD)){
            return true
        }
        return false
    }

    private fun initTable(){
        countText.text = ""
        ratioText.text = ""
    }

}