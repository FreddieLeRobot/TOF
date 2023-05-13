// TOF Software V.1.0.1
// JFM - 19MAR23
// BLE Communication is current task.

// Libraries
#include <ArduinoBLE.h>


// Constants - Change these as testing allows
const int wakeThreshold = 950;  // Threshold on analog reading to make sure the device stays awake.
const int sleepTime = 3600000;  // Threshold (milliseconds) for program to stay on with no activity before falling asleep.

// Startup Variables
bool isOn = 0;
bool sleepInterrupt = 1;

// Timing variables
int sleepTimerStart = millis();  // As name implies, timer value that starts when timing for sleep
int activeCheck = millis();      // Timer value to compare to sleepTimerStart
int cycleTimer = millis();
bool resetTimer = 0;

// Battery pin setup
int battPin = A0;
int battVal = 0;
int battCtrl = 7;
float battVoltage = 0;
int battCounter = 0;

// Pressure sensor setup
int sensorVal = 0;
int sensorPin = A4;
float pressureVoltage = 0;

// BLE Service Setup
BLEService tofService("180A");  // BLE time. 180A = "Device Information Service"
bool bleConnected = false;

// BLE Characteristics Setup
BLEByteCharacteristic sleepCharacteristic("2B42", BLERead | BLEWrite | BLENotify);  // 2B42 = "Sleep Activity Summary Data"
BLECharacteristic pressureCharacteristic("2A6D", BLERead | BLENotify, 4);           // 2A6D = "Pressure"
BLEByteCharacteristic chargingCharacteristic("2B06", BLERead | BLENotify);          // 2BED = "Power Specification"
BLECharacteristic batteryCharacteristic("2A19", BLERead | BLENotify, 4);            // 2A19 = "Battery Level"
BLEByteCharacteristic errorCharacteristic("2A4D", BLERead | BLEWrite | BLENotify);  // 2A4D = "Report"

#define testLED PIN_ENABLE_I2C_PULLUP

//Digital Low Pass Filter Variables
float xn1 = 0;
float yn1 = 0;

//Running Average Setup
const int RunningAverageCount = 3;
float RunningAverageBuffer[RunningAverageCount];
int NextRunningAverage;

// Messenger definitions and uses
typedef enum { PRESSURE,
               CHARGING,
               BATT_VOLTAGE,
               SLEEPING,
               ERROR } code;

typedef enum { WAKE,
               SLEEP } command;
byte CHARGE[1] = { 0xB1 };
byte NO_CHARGE[1] = { 0xB0 };
byte LOW_CHARGE[1] = { 0xB2 };



void setup() {

  Serial.begin(9600);

  // Setup for Battery Monitoring and LED
  pinMode(testLED, OUTPUT);
  pinMode(battCtrl, OUTPUT);
  digitalWrite(battCtrl, LOW);
  digitalWrite(testLED, LOW);  // when the central disconnects, turn on the LED

  // Setup for BLE transmission
  // begin initialization
  if (!BLE.begin()) {

    Serial.println("starting Bluetooth® Low Energy failed!");
    while (1)
      ;
  }
  // set advertised local name and service UUID:
  BLE.setLocalName("TOF_BLE");
  BLE.setAdvertisedService(tofService);
  // add the characteristics to the service
  tofService.addCharacteristic(sleepCharacteristic);
  tofService.addCharacteristic(pressureCharacteristic);
  tofService.addCharacteristic(chargingCharacteristic);
  tofService.addCharacteristic(batteryCharacteristic);
  tofService.addCharacteristic(errorCharacteristic);

  // add the service
  BLE.addService(tofService);

  // set the initial values for the characteristic:
  sleepCharacteristic.writeValue(0);
  //pressureCharacteristic.writeValue(0);
  chargingCharacteristic.writeValue(0);
  //batteryCharacteristic.writeValue(0);
  errorCharacteristic.writeValue(0);

  // start advertising
  BLE.advertise();
}

void loop() {

  // listen for Bluetooth® Low Energy peripherals to connect:
  BLEDevice central = BLE.central();

  if (central) {  // if a central is connected to peripheral:
    Serial.print("Connected to bluetooth central: ");
    // print the central's MAC address:
    Serial.println(central.address());

    connectIndication();
    bleConnected = true;

    while (central.connected()) {
      mainBluetooth();  // Main cycle with BLE
    }
    bleConnected = false;
    digitalWrite(testLED, LOW);
  } else {
    mainSerial();  // Main cycle without BLE
  }
}

void mainBluetooth() {
  //Start cycle
  cycleTimer = millis();
  // Check for any incoming serial commands
  BLEMessageCheck();
  // Sleep Mode
  if (sleepInterrupt != 0) {

  // Main functions - Pressure check regularly, battery check every 1000 samples, sleep check regularly.
  resetTimer = pressureCheck();
  if (resetTimer == 1) {
    sleepTimerStart = millis();  // Reset sleep timer if pressureCheck gets a value higher than the wake up threshold!
  }

  if (battCounter >= 1000) {
    batteryCheck();
  }
  // Manage all timer/interrupt/counter values
  battCounter += 1;
  sleepCheck();
  cycleCheck();
  }
}

void mainSerial() {
  //Start cycle
  cycleTimer = millis();
  // Check for any incoming serial commands
  serialMessageCheck();
  // Sleep Mode
  while (sleepInterrupt != 1) {
    sleepMode();
  }


  // Main functions - Pressure check regularly, battery check every 1000 samples, sleep check regularly.
  resetTimer = pressureCheck();
  if (resetTimer == 1) {
    sleepTimerStart = millis();  // Reset sleep timer if pressureCheck gets a value higher than the wake up threshold!
  }

  if (battCounter >= 1000) {
    batteryCheck();
  }
  // Manage all timer/interrupt/counter values
  battCounter += 1;
  sleepCheck();
  cycleCheck();
}

void batteryCheck() {

  //Turn on battery control pin (allows voltage to be checked)
  //Serial.println("Checking Battery");
  digitalWrite(battCtrl, HIGH);
  delay(100);  // Slight delay to let voltage settle - may change this later.
  battVal = analogRead(battPin);
  battVoltage = fmap(battVal, 0, 975, 0.0, 4.0);

  RunningAverageBuffer[NextRunningAverage++] = battVoltage;
  if (NextRunningAverage >= RunningAverageCount) {
    NextRunningAverage = 0;
  }
  float RunningAverageVolts = 0;
  for (int i = 0; i < RunningAverageCount; ++i) {
    RunningAverageVolts += RunningAverageBuffer[i];
  }
  RunningAverageVolts /= RunningAverageCount;

  if (battVoltage >= 4.0) {
    Messenger(CHARGING, CHARGE);
    byte hex[4] = { 0 };
    FloatToHex(battVoltage, hex);
    Messenger(BATT_VOLTAGE, hex);

  } else if (battVoltage <= 3) {
    Messenger(CHARGING, LOW_CHARGE);
    byte hex[4] = { 0 };
    FloatToHex(battVoltage, hex);
    Messenger(BATT_VOLTAGE, hex);
  } else {
    Messenger(CHARGING, NO_CHARGE);
    byte hex[4] = { 0 };
    FloatToHex(battVoltage, hex);
    Messenger(BATT_VOLTAGE, hex);
  }

  digitalWrite(battCtrl, LOW);
  battCounter = 0;
}

bool pressureCheck() {

  byte press_hex[4] = { 0 };

  if (isOn == 0) {
    isOn = 1;  // Do something with this later
  }


  sensorVal = analogRead(sensorPin);
  pressureVoltage = fmap(sensorVal, 100, 930, 0.000, 5.000);

  //Low Pass Filter
  float yn = 0.969 * yn1 + 0.0155 * pressureVoltage + 0.0155 * xn1;
  delay(1);
  xn1 = pressureVoltage;
  yn1 = yn;

  //FloatToHex(yn, press_hex); // With low pass filter
  FloatToHex(pressureVoltage, press_hex); // No low pass filter
  Messenger(PRESSURE, press_hex);

  delay(1);  // wait for 1 ms

  //Check to reset timer
  if (sensorVal >= wakeThreshold) {
    return 1;
  } else return 0;
}

void sleepCheck() {
  activeCheck = millis();
  if ((activeCheck - sleepTimerStart) >= sleepTime) goToSleep();
}

void serialMessageCheck() {

  while (Serial.available()) {
    //Commands from bluetooth devices and serial are simple and only need 2 bytes
    byte data[2];

    Serial.readBytes(data, 2);

    if (data[0] == 0xC4) {

      if (data[1] == 0x00) {
        goToSleep();
      } else if (data[1] == 0x01) {
        wakeupRoutine();
      } else {
        byte message[1] = { 0x01 };
        Messenger(ERROR, message);
      }
    }
  }
}

void BLEMessageCheck() {
  if (sleepCharacteristic.written()) {

    switch (sleepCharacteristic.value()) {  // any value other than 0
      case 00:
        if (sleepInterrupt != 0) {
          Serial.println("Sleeping");
          goToSleep();
        }
        break;
      default:
        if (sleepInterrupt != 1) {
          Serial.println("Wake");
          wakeupRoutine();  // Wake Routine
        }
        break;
    }
  } else if (errorCharacteristic.written()) {

    switch (errorCharacteristic.value()) {  // any value other than 0
      case 01:
        Serial.println("Incorrect Value");
        break;
      default:
        Serial.println("Unknown Error Message");
        break;
    }
  }
}

void cycleCheck() {
  activeCheck = millis();
  while (activeCheck - cycleTimer < 5) {
    activeCheck = millis();
  }
}

//Routine to exit sleep mode
void wakeupRoutine() {

  Serial.println("Waking Up!");
  byte message[1] = { 0x01 };
  Messenger(SLEEPING, message);  // Send message that device is waking up
  // Blink LED to show user that device is back on.
  digitalWrite(testLED, HIGH);
  delay(100);
  digitalWrite(testLED, LOW);
  delay(100);
  digitalWrite(testLED, HIGH);
  delay(100);
  digitalWrite(testLED, LOW);
  delay(100);
  digitalWrite(testLED, HIGH);
  delay(100);
  digitalWrite(testLED, LOW);
  delay(100);
  digitalWrite(testLED, HIGH);
  sleepInterrupt = 1;  // Set interrupt bit
  // Turn on BLE functions?

  // Start over timer for sleep.
  sleepTimerStart = millis();
}

// Routine to enter sleep mode
void goToSleep() {

  Serial.println("Going to sleep!");
  // Blink LED to show user that device is going to sleep.
  digitalWrite(testLED, HIGH);
  delay(300);
  digitalWrite(testLED, LOW);
  delay(300);
  digitalWrite(testLED, HIGH);
  delay(300);
  digitalWrite(testLED, LOW);
  delay(300);
  digitalWrite(testLED, HIGH);
  delay(300);
  digitalWrite(testLED, LOW);
  sleepInterrupt = 0;  // Set interrupt bit
  byte message[1] = { 0x00 };
  Messenger(SLEEPING, message);  // Send message that device is sleeping
  // Turn off BLE functions?
}

void sleepMode() {
  delay(100);
  sensorVal = analogRead(sensorPin);
  if (sensorVal >= wakeThreshold) {
    wakeupRoutine();
  } else {
    byte message[1] = { 0x00 };
    Messenger(SLEEPING, message);
    //Check for incoming message and act accordingly
    if (bleConnected) BLEMessageCheck();
    else serialMessageCheck();

  }
}

// Led blink to indicate connection.
void connectIndication() {
  digitalWrite(testLED, HIGH);
  delay(200);
  digitalWrite(testLED, LOW);
  delay(200);
  digitalWrite(testLED, HIGH);
}

void Messenger(code type, byte* message) {
  switch (type) {
    case PRESSURE:
      if (bleConnected) {
        pressureCharacteristic.writeValue(message, 4);
      } else {
        Serial.print(0xC1, HEX);
        for (int i = 3; i >= 0; i--) {
          if (message[i] < 16) Serial.print("0");
          Serial.print(message[i], HEX);
        }
        Serial.println();
      }
      break;
    case CHARGING:
      if (bleConnected) {
        chargingCharacteristic.writeValue(message[0]);
      } else {
        Serial.print(0xC2, HEX);
        if (message[0] < 16) Serial.print("0");
        Serial.print(message[0], HEX);
        Serial.println();
      }
      break;
    case BATT_VOLTAGE:
      if (bleConnected) {
        batteryCharacteristic.writeValue(message, 4);
      } else {
        Serial.print(0xC3, HEX);
        for (int i = 3; i >= 0; i--) {
          if (message[i] < 16) Serial.print("0");
          Serial.print(message[i], HEX);
        }
        Serial.println();
      }
      break;
    case SLEEPING:
      if (bleConnected) {
        sleepCharacteristic.writeValue(message[0]);
      } else {
        Serial.print(0xC4, HEX);
        if (message[0] < 16) Serial.print("0");
        Serial.print(message[0], HEX);
        Serial.println();
        break;
      }
    case ERROR:
      if (bleConnected) {
        errorCharacteristic.writeValue(message[0]);
      } else {
        Serial.print(0xC0, HEX);
        if (message[0] < 16) Serial.print("0");
        Serial.print(message[0], HEX);
        Serial.println();
      }
      break;
  }
}

float fmap(float x, float in_min, float in_max, float out_min, float out_max) {
  return (x - in_min) * (out_max - out_min) / (in_max - in_min) + out_min;
}

byte FloatToHex(float f, byte* hex) {
  byte* f_byte = reinterpret_cast<byte*>(&f);
  memcpy(hex, f_byte, 4);
}
