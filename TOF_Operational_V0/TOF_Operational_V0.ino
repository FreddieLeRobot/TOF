// TOF Software V.0.0.2
// JFM - 20JAN23
//


// Constants - Change these as testing allows
const int wakeThreshold = 950;  // Threshold on analog reading to make sure the device stays awake.
const int sleepTime = 30000;    // Time gap for when threshold needs to

// Startup Variables
bool isOn = 0;
bool interrupt = 1;

// Timing variables
int sleepTimerStart = millis();  // As name implies, timer value that starts when timing for sleep
int activeCheck = millis();      // Timer value to compare to sleepTimerStart
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

  // Sleep Mode
  while (interrupt != 1) {
    delay(100);
    sensorVal = analogRead(sensorPin);
    if (sensorVal >= wakeThreshold) {
      wakeupRoutine();
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
}

void batteryCheck() {

  //Turn on battery control pin (allows voltage to be checked)
  Serial.println("Checking Battery");
  digitalWrite(battCtrl, HIGH);
  delay(100);  // Slight delay to let voltage settle - may change this later.
  battVal = analogRead(battPin);
  battVoltage = fmap(battVal, 0, 975, 0.0, 4);

  RunningAverageBuffer[NextRunningAverage++] = battVoltage;
  if (NextRunningAverage >= RunningAverageCount) {
    NextRunningAverage = 0;
  }
  float RunningAverageVolts = 0;
  for (int i = 0; i < RunningAverageCount; ++i) {
    RunningAverageVolts += RunningAverageBuffer[i];
  }
  RunningAverageVolts /= RunningAverageCount;

  if (RunningAverageVolts >= 3.8) {
    Serial.print("Battery Voltage: ");
    Serial.println(RunningAverageVolts);
    Serial.println("Battery Charging - USB Input Detected");
  }
  else if (RunningAverageVolts <= 3){
    Serial.print("Battery Voltage: ");
    Serial.println(RunningAverageVolts);
    Serial.println("Low Battery, Please Recharge");
  }

  digitalWrite(battCtrl, LOW);
  battCounter = 0;
}

bool pressureCheck() {
  if (isOn == 0) {
    Serial.println("Hello world!");
    isOn = 1;
  }
  sensorVal = analogRead(sensorPin);
  pressureVoltage = fmap(sensorVal, 0, 996, 0.000, 15.000);

  //Low Pass Filter
  float yn = 0.969 * yn1 + 0.0155 * pressureVoltage + 0.0155 * xn1;
  delay(1);
  xn1 = pressureVoltage;
  yn1 = yn;

  Serial.println(yn, 4);

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

void wakeupRoutine() {

  Serial.println("Waking Up!");
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
  // Turn off BLE functions?
}

float fmap(float x, float in_min, float in_max, float out_min, float out_max) {
  return (x - in_min) * (out_max - out_min) / (in_max - in_min) + out_min;
}