bool isOn = 0;
int battPin = A0;
int battVal = 0;
int battCtrl = 7;
int sensorVal = 0;
float voltage = 0;
int counter = 0;
#define testLED PIN_ENABLE_I2C_PULLUP

const int RunningAverageCount = 16;
float RunningAverageBuffer[RunningAverageCount];
int NextRunningAverage;

void setup() {
  // put your setup code here, to run once:
  Serial.begin(9600);
  pinMode(testLED, OUTPUT);
  pinMode(battCtrl, OUTPUT);
  digitalWrite(battCtrl, LOW);
  digitalWrite(testLED, LOW);
  delay(5000);
}

void batteryCheck() {
  if (isOn == 0) {
    Serial.println("Hello world!");
    isOn = 1;
  }
  // put your main code here, to run repeatedly:
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

float fmap(float x, float in_min, float in_max, float out_min, float out_max) {
  return (x - in_min) * (out_max - out_min) / (in_max - in_min) + out_min;
}