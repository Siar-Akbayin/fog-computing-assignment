import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class EdgeDevice {
    private static final String SERVER_IP = "34.159.206.60"; // External IP of your cloud component
    private static final int SERVER_PORT = 8089;
    private final BlockingQueue<SensorData> dataQueue;
    private final BlockingQueue<SensorData> retryQueue;

    private boolean running;

    private static final String CACHE_FILE = "data_cache.txt";

    public EdgeDevice() throws IOException {
        this.dataQueue = new LinkedBlockingQueue<>();
        this.retryQueue = new LinkedBlockingQueue<>();

        // Start a thread to handle retries
        new Thread(this::handleRetries).start();

        // Start a thread to periodically send average data
        new Thread(this::sendAverageDataPeriodically).start();

        // Start HTTP server
        startHttpServer();
    }

    private void startHttpServer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.createContext("/data", new DataHandler());
        server.createContext("/response", new ResponseHandler());
        server.setExecutor(null);
        server.start();
        System.out.println("HTTP server started on port 8000");
    }

    private class DataHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                byte[] requestBody = exchange.getRequestBody().readAllBytes();
                String requestString = new String(requestBody);
                SensorData data = parseJson(requestString);

                if (data != null) {
                    dataQueue.add(data);

                    String response = "Data received";
                    exchange.sendResponseHeaders(200, response.getBytes().length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(response.getBytes());
                    os.close();
                } else {
                    exchange.sendResponseHeaders(400, -1); // Bad Request
                }
            } else {
                exchange.sendResponseHeaders(405, -1); // Method Not Allowed
            }
        }
    }

    private class ResponseHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), "utf-8"));
                StringBuilder requestBody = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    requestBody.append(line);
                }

                String responseMessage = requestBody.toString();
                System.out.println("Received warning from cloud server: " + responseMessage);

                String response = "Warning received";
                exchange.sendResponseHeaders(200, response.getBytes().length);
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            } else {
                exchange.sendResponseHeaders(405, -1); // Method Not Allowed
            }
        }
    }

    private SensorData parseJson(String jsonString) {
        try {
            String[] parts = jsonString.replace("{", "").replace("}", "").split(",");
            String sensorId = parts[0].split(":")[1].replace("\"", "").trim();
            double temperature = Double.parseDouble(parts[1].split(":")[1].trim());
            String timestamp = parts[2].split(":")[1].trim().replace("\"", "").trim();
            return new SensorData(sensorId, temperature, timestamp);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void sendAverageDataPeriodically() {
        while (true) {
            try {
                TimeUnit.SECONDS.sleep(5); // Adjust the interval as needed
                List<SensorData> dataList = new LinkedList<>();
                dataQueue.drainTo(dataList);

                if (!dataList.isEmpty()) {
                    double averageTemperature = dataList.stream()
                            .mapToDouble(SensorData::getTemperature)
                            .average()
                            .orElse(Double.NaN);

                    String timeStamp = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new java.util.Date());

                    SensorData averageData = new SensorData("average", averageTemperature, timeStamp);
                    processData(averageData);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void processData(SensorData data) {
        // send data to server here
        try {
            if (sendDataToServer(data)) {
                // Acknowledged
                System.out.println("Acknowledged: Sensor ID: " + data.getSensorId() + ", Temperature: " + data.getTemperature() + ", Timestamp: " + data.getTimestamp());
            } else {
                // Not acknowledged, add to retry queue
                System.out.println("Not acknowledged, will retry: Sensor ID: " + data.getSensorId() + ", Temperature: " + data.getTemperature() + ", Timestamp: " + data.getTimestamp());
                retryQueue.add(data);
                cacheData(data.toJson()); // Cache data on failure
            }
        } catch (Exception e) {
            // In case of an error, add to retry queue
            System.out.println("Error processing data, will retry: Sensor ID: " + data.getSensorId() + ", Temperature: " + data.getTemperature() + ", Timestamp: " + data.getTimestamp());
            retryQueue.add(data);
            cacheData(data.toJson()); // Cache data on failure
        }
    }

    private boolean sendDataToServer(SensorData data) throws IOException {
        String targetUrl = "http://" + SERVER_IP + ":" + SERVER_PORT + "/data";
        URL url = new URL(targetUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setDoOutput(true);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");

        String jsonInputString = "{\"sensorId\":\"" + data.getSensorId() + "\", \"temperature\":" + data.getTemperature() + ",\"timestamp\":\"" + data.getTimestamp() + "\"}";

        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = jsonInputString.getBytes("utf-8");
            os.write(input, 0, input.length);
        } catch (IOException e) {
            System.out.println("Error sending data to server: " + e.getMessage());
            return false;
        }

        int responseCode = connection.getResponseCode();
        if (responseCode == 200) {
            return true;
        } else {
            // Read the response message for debugging
            try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getErrorStream(), "utf-8"))) {
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                System.out.println("Error response from server: " + response.toString());
            } catch (IOException e) {
                System.out.println("Error reading the error response: " + e.getMessage());
            }
            return false;
        }
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

        while (true) {
            try {
                TimeUnit.SECONDS.sleep(5);
                try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                    String line;
                    int count = 0;
                    while ((line = br.readLine()) != null && count++ < 25) {
                        if (sendDataToServer(Objects.requireNonNull(SensorData.fromJson(line)))) {
                            System.out.println("Retry successful: " + line);
                            deleteFirstLine(file);
                        }
                        else {
                            System.out.println("Retry failed: " + line);
                            break;
                        }
                    }
                } catch (IOException e) {
                    System.out.println("Error reading cache file: " + e.getMessage());
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

    public static class SensorData {
        private final String sensorId;
        private final double temperature;
        private final String timestamp;

        public SensorData(String sensorId, double temperature, String timestamp) {
            this.sensorId = sensorId;
            this.temperature = temperature;
            this.timestamp = timestamp;
        }

        public String getSensorId() {
            return sensorId;
        }

        public double getTemperature() {
            return temperature;
        }

        public String getTimestamp() {
            return timestamp;
        }

        public String toJson() {
            return "{\"sensorId\":\"" + sensorId + "\",\"temperature\":" + temperature + ",\"timestamp\":\"" + timestamp + "\"}";
        }

        public static SensorData fromJson(String jsonString) {
            try {
                String[] parts = jsonString.replace("{", "").replace("}", "").split(",");
                String sensorId = parts[0].split(":")[1].replace("\"", "").trim();
                double temperature = Double.parseDouble(parts[1].split(":")[1].trim());
                String timestamp = parts[2].split(":")[1].trim().replace("\"", "").trim();
                return new SensorData(sensorId, temperature, timestamp);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    public static void main(String[] args) throws IOException {
        EdgeDevice edgeDevice = new EdgeDevice();
    }
}
