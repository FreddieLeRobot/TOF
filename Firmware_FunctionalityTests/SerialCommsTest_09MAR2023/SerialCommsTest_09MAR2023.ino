// Serial Comm Recieve and Send example and test

/*  With this uploaded to the TOF board, toggling the "sleep" checkbox on the GUI
    should return Hex-string "C400" and "C401".
    for use with the TOF project boards and Desktop Applications.

    JFM - 09MAR23
*/

byte data[2];

void setup() {
  // put your setup code here, to run once:
 Serial.begin(9600);
}

void loop() {
  // put your main code here, to run repeatedly:
  if (Serial.available()){
    while (Serial.available()){
      Serial.readBytes(data, 2);
    }
    p(data[0]);
    p(data[1]);
    Serial.println();
    data[0] = 0;
    data[1] = 0;
  }
  delay(10);
}

void p(char X) {

   if (X < 16) {Serial.print("0");}

   Serial.print(X, HEX);

}