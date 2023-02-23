bool isOn = 0;
int battPin = A4;
int sensorVal = 0;
int sensorPin = A4;
float voltage = 0;
#define testLED PIN_ENABLE_I2C_PULLUP

//Digital Low Pass Filter Variables
float xn1 = 0;
float yn1 = 0;

const int RunningAverageCount = 16;
float RunningAverageBuffer[RunningAverageCount];
int NextRunningAverage;

float plotUpper = 13.48;
float plotLower = 13.51;

void setup() {
  // put your setup code here, to run once:
Serial.begin(9600);
pinMode(testLED,OUTPUT);
delay(5000); 

}

void loop() {
  if (isOn == 0){
    Serial.println("Hello world!");
    isOn = 1;
  }
  // put your main code here, to run repeatedly:
  sensorVal = analogRead(sensorPin);
  voltage = fmap(sensorVal, 0, 1024, 0.000,15.000);

  // Running average Implementation
  // RunningAverageBuffer[NextRunningAverage++] = voltage;
  // if (NextRunningAverage >= RunningAverageCount) {
  //   NextRunningAverage = 0;
  // }
  // float RunningAveragePsi = 0;
  // for (int i = 0; i < RunningAverageCount; ++i) {
  //   RunningAveragePsi += RunningAverageBuffer[i];
  // }
  // RunningAveragePsi /= RunningAverageCount;


  // Low Pass Filter Implementation
  float yn = 0.969*yn1 + 0.0155*voltage + 0.0155*xn1;
  delay(1);
  xn1 = voltage;
  yn1 = yn;

  Serial.print(plotUpper, 4);
  Serial.print(",");
  Serial.print(plotLower, 4);
  Serial.print(",");
  //Serial.println(RunningAveragePsi, 4);
  Serial.println(yn, 4);

  //delay(1);                      // wait for a second
}

float fmap(float x, float in_min, float in_max, float out_min, float out_max)
{
return (x - in_min) * (out_max - out_min) / (in_max - in_min) + out_min;
}