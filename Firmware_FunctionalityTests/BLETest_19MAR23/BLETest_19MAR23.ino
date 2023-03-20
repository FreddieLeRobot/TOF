// Bluetooth communication test
// JFM - 19MAR23
// Tests BLE functionality - suggested to be used with a generic application like LightBlue.


#define testLED PIN_ENABLE_I2C_PULLUP

#include <ArduinoBLE.h>


BLEService ledService("180A");  // BLE LED Service


// BLE LED Switch Characteristic - custom 128-bit UUID, read and writable by central
BLEByteCharacteristic switchCharacteristic("2A58", BLERead | BLEWrite);

void setup() {
  Serial.begin(9600);
  while (!Serial)
    ;

  // set LED's pin to output mode
  pinMode(testLED, OUTPUT);
  digitalWrite(testLED, LOW);  // when the central disconnects, turn on the LED

  // begin initialization
  if (!BLE.begin()) {

    Serial.println("starting Bluetooth® Low Energy failed!");
    while (1)
      ;
  }

  // set advertised local name and service UUID:
  BLE.setLocalName("TOF - BLE Test");
  BLE.setAdvertisedService(ledService);

  // add the characteristic to the service
  ledService.addCharacteristic(switchCharacteristic);

  // add service
  BLE.addService(ledService);

  // set the initial value for the characteristic:
  switchCharacteristic.writeValue(0);

  // start advertising
  BLE.advertise();

  Serial.println("BLE LED Peripheral");
}

void loop() {
  // listen for Bluetooth® Low Energy peripherals to connect:
  BLEDevice central = BLE.central();

  // if a central is connected to peripheral:
  if (central) {
    Serial.print("Connected to central: ");
    // print the central's MAC address:
    Serial.println(central.address());

    connectIndicaiton();

    // while the central is still connected to peripheral:
    while (central.connected()) {
      // if the remote device wrote to the characteristic,
      // use the value to control the LED:
      if (switchCharacteristic.written()) {

        switch (switchCharacteristic.value()) {  // any value other than 0
          case 01:
            Serial.println("LED on");
            digitalWrite(testLED, HIGH);  // will turn the LED on
            break;
          default:
            Serial.println(F("LED off"));
            digitalWrite(testLED, LOW);  // will turn the LED off
            break;
        }
      }
      delay(000);
    }

    // when the central disconnects, print it out:
    Serial.print(F("Disconnected from central: "));
    Serial.println(central.address());

    digitalWrite(testLED, LOW);  // will turn the LED off

  }
}

// Led blink to indicate connection.
void connectIndicaiton(){
  digitalWrite(testLED, HIGH);
  delay(200);
  digitalWrite(testLED, LOW);
  delay(200);
  digitalWrite(testLED, HIGH);
  delay(200);
  digitalWrite(testLED, LOW);
}
