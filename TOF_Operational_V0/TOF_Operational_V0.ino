// TOF Software V.0.0.1
// JFM - 20JAN23
// 

// Variable Setup
bool isOn = 0;
int counter = 0;
bool interrupt = 1;
// Battery pin setup
int battPin = A0;
int battVal = 0;
int battCtrl = 7;
float voltage = 0;

// Pressure sensor setup
int sensorVal = 0;
int sensorPin = A4;
float pressureVoltage = 0;


#define testLED PIN_ENABLE_I2C_PULLUP

//Digital Low Pass Filter Variables
float xn1 = 0;
float yn1 = 0;

//Running Average Setup
const int RunningAverageCount = 16;
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
  while (interrupt != 1){
    delay(100);
    sensorVal = analogRead(sensorPin);
    if (sensorVal >= 950) {
      wakeupRoutine();
    }
  }

  // BLE Initialize


  // Ble advertise


  // Main functions - Pressure check regularly, 
  pressureCheck();
}

void batteryCheck() {

  sensorVal = analogRead(battPin);
  voltage = fmap(sensorVal, 0, 1024, 0.0, 3.3);

  RunningAverageBuffer[NextRunningAverage++] = voltage;
  if (NextRunningAverage >= RunningAverageCount) {
    NextRunningAverage = 0;
  }
  float RunningAverageVolts = 0;
  for (int i = 0; i < RunningAverageCount; ++i) {
    RunningAverageVolts += RunningAverageBuffer[i];
  }
  RunningAverageVolts /= RunningAverageCount;

  Serial.println(RunningAverageVolts);

  delay(50);  // wait for a second
  counter += 1;
  if (counter == 200) {
    Serial.println("Setting BattMeas Off");
    digitalWrite(battCtrl, LOW);
    counter = 0;
  } else if (counter == 100) {
    Serial.println("Setting BattMeas On");
    digitalWrite(battCtrl, HIGH);
  }
}

void pressureCheck() {
  if (isOn == 0) {
    Serial.println("Hello world!");
    isOn = 1;
  }
  sensorVal = analogRead(sensorPin);
  voltage = fmap(sensorVal, 0, 1024, 0.000, 15.000);

  //Low Pass Filter
  float yn = 0.969*yn1 + 0.0155*voltage + 0.0155*xn1;
  delay(1);
  xn1 = voltage;
  yn1 = yn;

  Serial.println(yn, 4);

  delay(1);  // wait for 1 ms
}

wakeupRoutine() {
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
  interrupt = 1;
  // Turn on BLE functions?
}

goToSleep() {
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
  interrupt = 0;
  // Turn off BLE functions?
}

float fmap(float x, float in_min, float in_max, float out_min, float out_max) {
  return (x - in_min) * (out_max - out_min) / (in_max - in_min) + out_min;
}
