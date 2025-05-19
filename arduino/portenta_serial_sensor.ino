#include <Arduino.h>

// Частота выборки и усреднение
const uint32_t SAMPLE_RATE = 1; // 1 Гц для калибровки
const uint32_t SAMPLE_INTERVAL = 1000000 / SAMPLE_RATE; // 1000 мс
const uint8_t AVERAGE_SAMPLES = 8;

void setup() {
  Serial.begin(115200);
  while (!Serial);

  // Настройка АЦП
  analogReadResolution(16);
  pinMode(A0, INPUT);

  Serial.println("Serial connection established");
}

void loop() {
  static uint32_t lastSampleTime = 0;
  uint32_t currentTime = micros();

  if (currentTime - lastSampleTime >= SAMPLE_INTERVAL) {
    lastSampleTime = currentTime;

    // Усреднение АЦП
    uint32_t adcValue = 0;
    for (uint8_t i = 0; i < AVERAGE_SAMPLES; i++) {
      adcValue += analogRead(A0);
      delayMicroseconds(100); // Пауза для стабильности
    }
    adcValue /= AVERAGE_SAMPLES;

    // Пересчет в напряжение
    double vA0 = (adcValue / 65535.0) * 3.3;
    double vSensor = vA0 / 0.6; // Учитываем делитель
    double pressure = (vSensor - 0.5) * 450.0;

    // Формирование строки: time_ms,adc_value,vA0,vSensor,pressure
    char buffer[64];
    snprintf(buffer, sizeof(buffer), "%lu,%u,%.3f,%.3f,%.2f",
             millis(), adcValue, vA0, vSensor, pressure);

    // Отправка по Serial
    Serial.println(buffer);
  }
}