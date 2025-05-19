import com.fazecast.jSerialComm.SerialPort;
import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class CalibrationServer {
    private static final String OUTPUT_PATH = "/data/";
    private static final String FILE_PREFIX = "calibration_data_";
    private static final String PORT_NAME = "COM7"; // Укажите ваш порт
    private static final int MAX_STEPS = 10;
    private static final int MEASUREMENTS_PER_STEP = 5;
    private static final long MEASUREMENT_INTERVAL_MS = 20000; // 20 секунд

    public static void main(String[] args) {
        // Шаг 1: Открытие Serial-порта
        SerialPort serialPort = SerialPort.getCommPort(PORT_NAME);
        serialPort.setBaudRate(115200);
        serialPort.setNumDataBits(8);
        serialPort.setNumStopBits(1);
        serialPort.setParity(SerialPort.NO_PARITY);
        serialPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 500, 0);

        int retries = 5;
        boolean portOpened = false;
        for (int i = 0; i < retries && !portOpened; i++) {
            portOpened = serialPort.openPort();
            if (!portOpened) {
                System.err.println("Attempt " + (i + 1) + ": Failed to open port: " + PORT_NAME);
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        if (!portOpened) {
            System.err.println("Could not open port after " + retries + " attempts. Exiting.");
            return;
        }

        System.out.println("Using port: " + serialPort.getSystemPortName());

        // Шаг 2: Подготовка CSV-файла
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
        String fileName = FILE_PREFIX + now.format(formatter) + ".csv";
        String filePath = OUTPUT_PATH + fileName;

        File file = new File(filePath);
        file.getParentFile().mkdirs();

        try (FileWriter fileWriter = new FileWriter(file, true);
             BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
             BufferedReader reader = new BufferedReader(new InputStreamReader(serialPort.getInputStream()))) {

            bufferedWriter.write("Manometer (bar),Min ADC,Max ADC,Average ADC\n");
            bufferedWriter.flush();
            System.out.println("Writing data to " + file.getAbsolutePath());

            Scanner scanner = new Scanner(System.in);
            System.out.println("Enter 'start' to begin calibration:");
            String command = scanner.nextLine().trim().toLowerCase();

            if (!command.equals("start")) {
                System.out.println("Calibration aborted.");
                return;
            }

            // Шаг 3: Цикл калибровки
            int step = 1;
            while (step <= MAX_STEPS) {
                System.out.println("Enter manometer reading for step " + step + " (bar) or 'stop' to end:");
                String input = scanner.nextLine().trim();

                if (input.equalsIgnoreCase("stop")) {
                    break;
                }

                double manometerReading;
                try {
                    manometerReading = Double.parseDouble(input);
                } catch (NumberFormatException e) {
                    System.out.println("Invalid input. Please enter a number or 'stop'.");
                    continue;
                }

                List<Integer> adcValues = new ArrayList<>();
                for (int i = 0; i < MEASUREMENTS_PER_STEP; i++) {
                    try {
                        Thread.sleep(MEASUREMENT_INTERVAL_MS);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    String line = reader.readLine();
                    if (line != null && !line.isEmpty()) {
                        try {
                            String[] parts = line.split(",");
                            if (parts.length >= 2) {
                                int adc = Integer.parseInt(parts[1]);
                                adcValues.add(adc);
                                System.out.println("Measurement " + (i + 1) + ": ADC = " + adc);
                            }
                        } catch (Exception e) {
                            System.err.println("Error parsing data: " + line);
                        }
                    } else {
                        System.out.println("No data received for measurement " + (i + 1));
                    }
                }

                if (!adcValues.isEmpty()) {
                    int minADC = adcValues.stream().min(Integer::compare).orElse(0);
                    int maxADC = adcValues.stream().max(Integer::compare).orElse(0);
                    double avgADC = adcValues.stream().mapToInt(Integer::intValue).average().orElse(0);

                    bufferedWriter.write(String.format("%.2f,%d,%d,%.2f\n", manometerReading, minADC, maxADC, avgADC));
                    bufferedWriter.flush();
                    System.out.println("Step " + step + " completed: Manometer = " + manometerReading +
                            ", Min ADC = " + minADC + ", Max ADC = " + maxADC + ", Avg ADC = " + avgADC);
                } else {
                    System.out.println("No valid data received for step " + step);
                }

                step++;
            }

            System.out.println("Calibration complete. Data saved to " + filePath);

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (serialPort.isOpen()) {
                serialPort.closePort();
            }
        }
    }
}