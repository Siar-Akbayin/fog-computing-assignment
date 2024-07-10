import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class Sensor implements Runnable {
    private final String sensorId;
    private final String edgeDeviceUrl;
    private final HttpClient httpClient;
    private final Random random;
    private boolean running;
    private final String CACHE_FILE; // Change this to a non-static field

    public Sensor(String sensorId, String edgeDeviceUrl) {
        this.sensorId = sensorId;
        this.edgeDeviceUrl = edgeDeviceUrl;
        this.httpClient = HttpClient.newHttpClient();
        this.random = new Random();
        this.running = true;
        this.CACHE_FILE = "sensor_cache_" + sensorId + ".txt"; // Initialize the cache file with sensorId

        // Start a thread to handle retries
        new Thread(this::handleRetries).start();
    }

    public void run() {
        while (running) {
            try {
                double temperature = generateTemperature();
                String timeStamp = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new java.util.Date());
                String jsonData = "{\"sensorId\":\"" + sensorId
                        + "\",\"temperature\":" + temperature
                        + ",\"timestamp\":\"" + timeStamp + "\"}";

                if (sendData(jsonData)) {
                    System.out.println("Data sent successfully: Sensor ID: " + sensorId + ", Temperature: " + temperature);
                } else {
                    cacheData(jsonData);
                    System.out.println("Failed to send data: Sensor ID: " + sensorId + ", Temperature: " + temperature);
                }

                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                running = false;
            }
        }
    }

    private boolean sendData(String jsonData) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(edgeDeviceUrl + "/data"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonData))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private double generateTemperature() {
        // Simulate temperature generation
        return 20.0 + (15.0 * random.nextDouble());
    }

    public void stop() {
        running = false;
    }

    private void cacheData(String data) {
        try (FileWriter fw = new FileWriter(CACHE_FILE, true);
             PrintWriter pw = new PrintWriter(fw)) {
            pw.println(data);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleRetries() {
        File file = new File(CACHE_FILE);

        while (running) {
            try {
                TimeUnit.SECONDS.sleep(5);
                try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                    String line;
                    int count = 0;
                    while ((line = br.readLine()) != null && count++ < 25) {
                        if (sendData(line)) {
                            System.out.println("Retry successful: " + line);
                            deleteFirstLine(file);
                        }
                        else {
                            break;
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private static void deleteFirstLine(File file) throws IOException {
        List<String> lines = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            boolean firstLineSkipped = false;

            while ((line = br.readLine()) != null) {
                if (!firstLineSkipped) {
                    firstLineSkipped = true;
                    continue; // Skip the first line
                }
                lines.add(line);
            }
        }

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
            for (String line : lines) {
                bw.write(line);
                bw.newLine();
            }
        }
    }

    public static void main(String[] args) {
        String edgeDeviceUrl = "http://localhost:8000";
        Sensor sensor1 = new Sensor("1", edgeDeviceUrl);
        Sensor sensor2 = new Sensor("2", edgeDeviceUrl);

        Thread thread1 = new Thread(sensor1);
        Thread thread2 = new Thread(sensor2);

        thread1.start();
        thread2.start();
    }
}
