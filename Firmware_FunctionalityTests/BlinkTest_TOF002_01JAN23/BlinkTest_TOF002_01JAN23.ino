bool isOn = 0;
#define testLED PIN_ENABLE_I2C_PULLUP

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
  Serial.println("Turning LED On.");
  digitalWrite(testLED, HIGH);  // turn the LED on (HIGH is the voltage level)
  delay(1000);                      // wait for a second
  Serial.println("Turning LED Off.");
  digitalWrite(testLED, LOW);   // turn the LED off by making the voltage LOW
  delay(1000);                      // wait for a second
}
