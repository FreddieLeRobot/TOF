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
import android.os.CountDownTimer
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow

private const val CHART_LABEL = "DATA_CHART"
private const val DERIV_LABEL = "DERIV_CHART"
private const val ENABLE_BLUETOOTH_REQUEST_CODE = 1
private const val RUNTIME_PERMISSION_REQUEST_CODE = 2

// Threshold constant
private const val PRESSURE_THRESHOLD = 0.07
private const val SLOPE_THRESHOLD = 0.04

// Peak Detection Constant
private const val BASELINE_AVG_WINDOW_SIZE = 100 // Change this to make the average pressure calc window larger
private const val LLS_WINDOW = 9 // Change this to make the linear regression window larger

private const val PEAK_TIMEOUT_MS = 5000

// Constant to allow app to operate in debug mode
private const val DEBUG_MODE = 0

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
        var peakList: MutableList<Float>,
        var newPeak: Boolean
    )

    // Assignable thresholds
    private var ampThreshold = PRESSURE_THRESHOLD
    private var slopeThreshold = SLOPE_THRESHOLD

    // Timer for peaks
    private var isTimerRunning = false
    private var peakTimer = object: CountDownTimer(PEAK_TIMEOUT_MS.toLong(),1000){
        override fun onTick(p0: Long) {
            Log.i("Peak Timer", "1 sec left.")
        }

        override fun onFinish() {
            isTimerRunning = false
            Log.i("Peak Timer", "Timer done!")
        }
    }

    private var peakData = PeakData(peakList = mutableListOf(), false)

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

    // Init amp threshold control
    lateinit var ampThreshText: EditText
    lateinit var ampPlusBtn: Button
    lateinit var ampMinusBtn: Button

    // Init slope threshold control
    lateinit var slopeThreshText: EditText
    lateinit var slopePlusBtn: Button
    lateinit var slopeMinusBtn: Button

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

    // Mutable list for live entry of derivative to chart
    private val derivData = mutableListOf<Entry>()
    private var _derivDataSet = LineDataSet(derivData, DERIV_LABEL)

    //Derivative smoothing Array
    private val smoothArray = intArrayOf(0,0,0,0,-3,-3,-2,-1,0,1,2,3,3,0,0,0,0)


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

        ampThreshText = findViewById(R.id.ampThreshText)
        ampThreshText.setText(ampThreshold.toString())
        ampMinusBtn = findViewById(R.id.ampThreshButtonMinus)
        ampPlusBtn = findViewById(R.id.ampThreshButtonPlus)

        slopeThreshText = findViewById(R.id.slopeThreshText)
        slopeThreshText.setText(slopeThreshold.toString())
        slopeMinusBtn = findViewById(R.id.slopeThreshButtonMinus)
        slopePlusBtn = findViewById(R.id.slopeThreshButtonPlus)

        //Listeners for btns

        slopePlusBtn.setOnClickListener {
            if (slopeThreshold <= 0.99) {
                slopeThreshold += 0.01
            }
            else if (slopeThreshold < 1){
                slopeThreshold = 1.0
            }
            slopeThreshText.setText(slopeThreshold.format(2))
        }
        slopeMinusBtn.setOnClickListener {
            if (slopeThreshold >= 0.01) {
                slopeThreshold -= 0.01
            }
            else if (slopeThreshold > 0){
                slopeThreshold = 0.0
            }
            slopeThreshText.setText(slopeThreshold.format(2))
        }

        ampPlusBtn.setOnClickListener {
            if (ampThreshold <= 0.99) {
                ampThreshold += 0.01
            }
            else if (ampThreshold < 1){
                ampThreshold = 1.0
            }
            ampThreshText.setText(ampThreshold.format(2))
        }
        ampMinusBtn.setOnClickListener {
            if (ampThreshold >= 0.01) {
                ampThreshold -= 0.01
            }
            else if (ampThreshold > 0){
                ampThreshold = 0.0
            }
            ampThreshText.setText(ampThreshold.format(2))
        }

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

        // Listener for text input
        ampThreshText.addTextChangedListener (object: TextWatcher{

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                val value = ampThreshText.text.toString().toDoubleOrNull()
                if (value != null) {
                    ampThreshold = value!!
                }
            }

            override fun afterTextChanged(p0: Editable?) {
            }

        })
        slopeThreshText.addTextChangedListener (object: TextWatcher{

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                val value = slopeThreshText.text.toString().toDoubleOrNull()
                if (value != null) {
                    slopeThreshold = value!!
                }
            }

            override fun afterTextChanged(p0: Editable?) {
            }

        })

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

    private fun lineChartUpdate(){
        // Change DEBUG Mode at top to 0 to see regular data
        if(DEBUG_MODE==0){
            lineChart.data = LineData(_lineDataSet)
            lineChart.notifyDataSetChanged()
            lineChart.setVisibleXRangeMaximum(3000F)
            lineChart.setVisibleXRangeMinimum(3000F)
            lineChart.moveViewToX(_lineDataSet.xMax)
        }
        else {
            lineChart.data = LineData(_derivDataSet)
            lineChart.notifyDataSetChanged()
            lineChart.setVisibleXRangeMaximum(3000F)
            lineChart.setVisibleXRangeMinimum(10F)
            lineChart.moveViewToX(_derivDataSet.xMax)
        }
    }

    private fun resetLineChartData() {
        lineChart.clearValues()
        lineData.clear()
        derivData.clear()
        lineChart.invalidate()
        initPeakData()
        movingAvg = 0.0f
        avgList = mutableListOf<Float>()
    }

    private fun lineChartAddData(fl: Float) {
        var index = 0f
        if (lineData.isNotEmpty()) {
            index = lineData.last().x + 1f
        }
        lineData.add(Entry(index, fl))

        if (lineData.lastIndex >= 17){
            derivativeData(fl, (index-1).toInt())
        }
        if (lineData.lastIndex  >= BASELINE_AVG_WINDOW_SIZE-1){
            avgAndPeakCheck(lineData[lineData.lastIndex-8].y)
        }

        if (DEBUG_MODE == 0) {
            _lineDataSet = LineDataSet(lineData, CHART_LABEL)
            _lineDataSet.setDrawCircles(false)
        }
        else{
            if (lineData.lastIndex >= 18){
                _derivDataSet = LineDataSet(derivData, CHART_LABEL)
                _derivDataSet.setDrawCircles(false)
            }
        }
        if (DEBUG_MODE == 0){
            lineChartUpdate()
        }

        else{
            if (lineData.lastIndex >= 18){
                lineChartUpdate()
            }
        }
    }

    private fun derivativeData(fl:Float, index:Int){
        if (index == 17){
            for (item: Int in 0..7){
                derivData.add(Entry(item.toFloat(),0.00f))
                if (DEBUG_MODE == 1) {
                    Log.i("Derivative Index", derivData[item].x.toString())
                    Log.i("Derivative", derivData[item].y.toString())
                }
            }
        }
        var newDeriv = calculateDerivative(index)

        val entryNum = index-8
        derivData.add(Entry(entryNum.toFloat(),newDeriv))
        if (DEBUG_MODE == 1) {
            Log.i("Derivative Index", derivData.last().x.toString())
            Log.i("Derivative", derivData.last().y.toString())
        }
    }

    private fun calculateDerivative(index: Int):Float{
        var i = 16
        var derivative = 0.00f
        for (num in 0..16){
            derivative += (lineData[index-num].y * smoothArray[i-num])
        }
        return derivative
    }

    // Peak detection and baseline averaging functions
    private fun avgAndPeakCheck(dataPoint: Float){
        if (lineData.lastIndex == BASELINE_AVG_WINDOW_SIZE-1){
            lineData.forEach {
                avgList.add(it.y)
            }
            var sum = avgList.sum()
            movingAvg = sum / BASELINE_AVG_WINDOW_SIZE.toFloat()
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
            movingAvg = sum / BASELINE_AVG_WINDOW_SIZE.toFloat()
            avgText.text = "Avg: " + movingAvg.format(4)

        }
        else{
            peakHandler(dataPoint)
        }
    }

    private fun peakHandler(dataPoint: Float) {

        // Todo: Change out peaklist add for linear least squares curve fit

        //Check timer to see if it has been too long since last peak
        if (!isTimerRunning) {
            initPeakData()
            isTimerRunning = true
        }
        else{
            peakTimer.cancel()
        }

        peakTimer.start()
        val peak = peakLLS(dataPoint)
        peakData.peakList.add(peak.toFloat())
        peakUIHandler()
    }

    private fun peakLLS(dp: Float): Double{
        // if the last data point matches the given data, try to find gaussian parameters
        // using the quadratic (parabola) linear least squares fit to transformed data x, ln(y),
        // regression equation: y = ax^2 + bx + c
        // Need to find sum(x), sum(y), sum(X^2), sum(X^3), sum(X^4), sum(ln(y)), sum(x^2 * ln(y))
        if (lineData[lineData.lastIndex-8].y == dp) {
            //Todo: LLS algorithm for peak detection
            val startIndex = derivData.last().x.toInt() - 4
            // initialize sums
            var X_Sum = 0.00
            var lnY_Sum = 0.00
            var Xy_Sum = 0.00
            var X2_Sum = 0.00
            var X3_Sum = 0.00
            var X4_Sum = 0.00
            var X2y_Sum = 0.00
            var Xcount = 10.0
            // Sum the various
            for (num in startIndex..startIndex + (LLS_WINDOW-1)) {
                // Since we aren't looking for exact position of the peak on the X-Axis
                // (and that time will be logged in a log output)
                // X is always 10-18, to keep floating point calculations low.
                // At higher indexes, values like X^4_Sum get very large, and as a result,
                // accuracy when converting from base-2 to base-10 is the result.
                X_Sum += Xcount
                lnY_Sum += ln(lineData[num].y)
                Xy_Sum += (Xcount * ln(lineData[num].y))
                X2_Sum += Xcount.pow(2)
                X3_Sum += Xcount.pow(3)
                X4_Sum += Xcount.pow(4)
                X2y_Sum += ln(lineData[num].y) * (Xcount.pow(2))
                Xcount += 1.0
                Log.i("LLS Data", "$X_Sum  $lnY_Sum  $Xy_Sum  $X2_Sum  $X3_Sum  $X4_Sum  $X2y_Sum")
            }
            // Create linear coefficients for quadratic formula
            // First find the denominator for the eqns to find a, b and c
            val denominator = LLS_WINDOW.toFloat() * X2_Sum * X4_Sum + 2 * X_Sum * X2_Sum * X3_Sum - X2_Sum.pow(3) - X_Sum.pow(2) * X4_Sum - LLS_WINDOW.toFloat() * X3_Sum.pow(2)

            Log.i("LLS Denominator", "$denominator")
            // Find coefficients
            val coeffA =
                ((LLS_WINDOW * X2_Sum * X2y_Sum) + (X_Sum * X3_Sum * lnY_Sum) + (X_Sum * X2_Sum * Xy_Sum) - (X2_Sum.pow(
                    2
                ) * lnY_Sum) - (X_Sum.pow(2) * X2y_Sum) - (LLS_WINDOW * X3_Sum * Xy_Sum)) / denominator
            val coeffB =
                (LLS_WINDOW * X4_Sum * Xy_Sum + X_Sum * X2_Sum * X2y_Sum + X2_Sum * X3_Sum * lnY_Sum - X2_Sum.pow(
                    2
                ) * Xy_Sum - X_Sum * X4_Sum * lnY_Sum - LLS_WINDOW * X3_Sum * X2y_Sum) / denominator
            val coeffC =
                (X2_Sum * X4_Sum * lnY_Sum + X2_Sum * X3_Sum * Xy_Sum + X_Sum * X3_Sum * X2y_Sum - X2_Sum.pow(
                    2
                ) * X2y_Sum - X_Sum * X4_Sum * Xy_Sum - X3_Sum.pow(2) * lnY_Sum) / denominator

            Log.i("LLS Coeffs", "$coeffA  $coeffB  $coeffC")

            // Finally, find the height of the peak (Formula: Height = EXP(CoeffC-a*(b/(2*a))^2)
            Log.i("Peak Height Found", (exp(coeffC - coeffA * (coeffB / (2 * coeffA)).pow(2))).toString())

            return exp(coeffC - coeffA * (coeffB / (2 * coeffA)).pow(2))
        }
        else{
            Log.i("Peak Detect","Datapoint did not match last point...")
        }
        return 0.00
    }

    private fun initPeakData() {
        peakData = PeakData(peakList = mutableListOf(), false)
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
        // First, check to see if the value is larger than the threshold.
        if (fl > (movingAvg+ ampThreshold)){
            // If so, check to see if there was a down zero crossing in first derivative
            if (DEBUG_MODE == 1) {
                Log.i("deriv Check1", derivData.last().y.toString())
                Log.i("deriv Check2", derivData[derivData.lastIndex - 1].y.toString())
            }
            if (derivData.last().y < 0.0f && derivData[derivData.lastIndex-1].y > 0.0f){
                // Finally, if so, check to see if the crossing slope is greater than the slope threshold.
                if (derivData[derivData.lastIndex-1].y - derivData.last().y > slopeThreshold){
                    // Peak detected!
                    return true
                }
            }
        }
        // If those three conditions are not met, then a peak is not detected.
        return false
    }

    private fun initTable(){
        countText.text = ""
        ratioText.text = ""
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

    // Double to String formatting
    fun Double.format(digits: Int) = "%.${digits}f".format(this)

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

}