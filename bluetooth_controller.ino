const int ledPin13 = 13;
long previousMillis;
long interval;
String data;

void setup() {
  Serial.begin(9600);
  
  pinMode(ledPin13, OUTPUT);
  
  previousMillis = millis();
  interval = 1;
}

void loop() {
  unsigned long currentMillis = millis();
  
  if((currentMillis - previousMillis) > interval){
    previousMillis = currentMillis;
    
    if(Serial.available()){
      data = Serial.readString();
      
      if(data == "true"){
        digitalWrite(ledPin13, HIGH);  
      }else if(data == "false"){
        digitalWrite(ledPin13, LOW);
      }
    }  
  }
}
