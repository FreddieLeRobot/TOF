// TOF Software V.0.0.2




// JFM - 20JAN23
//


// Constants - Change these as testing allows
const int wakeThreshold = 950;  // Threshold on analog reading to make sure the device stays awake.
const int sleepTime = 300000;   // Threshold (milliseconds) for program to stay on with no activity before falling asleep.

// Startup Variables
bool isOn = 0;
bool interrupt = 1;

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
  digitalWrite(testLED, HIGH);

  //Set up for low power modes


  //Setup for BLE transmission
}

void loop() {

  //Start cycle
    cycleTimer = millis();
  // Check for any incoming serial commands
  messageCheck();
  // Sleep Mode
  while (interrupt != 1) {
    delay(100);
    sensorVal = analogRead(sensorPin);
    if (sensorVal >= wakeThreshold) {
      wakeupRoutine();
    } else {
      byte message[1] = { 0x00 };
      Messenger(SLEEPING, message);
      //Check for incoming message and act accordingly
      messageCheck();
    }
  }

  // BLE Initialize


  // Ble advertise


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
  pressureVoltage = fmap(sensorVal, 0, 996, 0.000, 15.000);

  //Low Pass Filter
  float yn = 0.969 * yn1 + 0.0155 * pressureVoltage + 0.0155 * xn1;
  delay(1);
  xn1 = pressureVoltage;
  yn1 = yn;

  FloatToHex(yn, press_hex);
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

void messageCheck() {

  while (Serial.available()) {
    //Commands from bluetooth devices and serial are simple and only need 2 bytes
    byte data[2];

    Serial.readBytes(data,2);

      if (data[0] == 0xC4) {

        if (data[1] == 0x00) {
          goToSleep();
        } 
        else if (data[1] == 0x01) {
          wakeupRoutine();
        } 
        else {
          byte message[1] = { 0x01 };
          Messenger(ERROR, message);
        }
    }
  }  
}

void cycleCheck() {
  activeCheck = millis();
  while (activeCheck - cycleTimer < 5){
    activeCheck = millis();
  }
}

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
  interrupt = 1;  // Set interrupt bit
  // Turn on BLE functions?

  // Start over timer for sleep.
  sleepTimerStart = millis();
}

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
  interrupt = 0;  // Set interrupt bit
  byte message[1] = { 0x00 };
  Messenger(SLEEPING, message);  // Send message that device is sleeping
  // Turn off BLE functions?
}

void Messenger(code type, byte* message) {
  switch (type) {
    case PRESSURE:
      Serial.print(0xC1, HEX);
      for (int i = 3; i >= 0; i--) {
        if (message[i] < 16) Serial.print("0");
        Serial.print(message[i], HEX);
      }
      Serial.println();
      break;
    case CHARGING:
      Serial.print(0xC2, HEX);
      if (message[0] < 16) Serial.print("0");
      Serial.print(message[0], HEX);
      Serial.println();
      break;
    case BATT_VOLTAGE:
      Serial.print(0xC3, HEX);
      for (int i = 3; i >= 0; i--) {
        if (message[i] < 16) Serial.print("0");
        Serial.print(message[i], HEX);
      }
      Serial.println();
      break;
    case SLEEPING:
      Serial.print(0xC4, HEX);
      if (message[0] < 16) Serial.print("0");
      Serial.print(message[0], HEX);
      Serial.println();
      break;
    case ERROR:
      Serial.print(0xC0, HEX);
      if (message[0] < 16) Serial.print("0");
      Serial.print(message[0], HEX);
      Serial.println();
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
