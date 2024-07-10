import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class CloudComponent {
    private static final String LOG_FILE = "/var/log/cloud_component.log";
    private static final String CACHE_FILE = "/usr/src/myapp/warning_cache.txt";
    private static final String EDGE_DEVICE_URL = System.getenv("EDGE_DEVICE_URL");


    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8089), 0);
        server.createContext("/data", new DataHandler());
        server.setExecutor(null); // creates a default executor
        server.start();
        log("Cloud component HTTP server started on port 8089");
        log("Edge device URL: " + EDGE_DEVICE_URL);


        // Start retry mechanism in a separate thread
        new Thread(CloudComponent::retryCachedMessages).start();
    }

    private static void log(String message) {
        try (FileWriter fw = new FileWriter(LOG_FILE, true);
             PrintWriter pw = new PrintWriter(fw)) {
            pw.println(message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class DataHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), "utf-8"));
                StringBuilder requestBody = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    requestBody.append(line);
                }

                String requestData = requestBody.toString();
                log("Received data: " + requestData);

                // Process the received data (this is where you can add your logic to handle the data)
                // Ensure no exception is thrown here
                SensorData data = SensorData.fromJson(requestData);
                if (data != null && data.getTemperature() > 25.0) {
                    String warningMessage = String.format("Warning: Average temperature %.2f exceeds 25 degrees.", data.getTemperature());
                    if (!sendWarningToEdgeDevice(warningMessage)) {
                        cacheWarningMessage(warningMessage);
                    }
                }

                String response = "Data processed successfully";
                exchange.sendResponseHeaders(200, response.getBytes().length);
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            } else {
                exchange.sendResponseHeaders(405, -1); // Method Not Allowed
            }
        }
    }

    private static boolean sendWarningToEdgeDevice(String warningMessage) {
        try {
            URL url = new URL(EDGE_DEVICE_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "text/plain"); // Set content type to text/plain

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = warningMessage.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                log("Warning sent to edge device successfully.");
                return true;
            } else {
                log("Failed to send warning to edge device. Response code: " + responseCode);
                return false;
            }
        } catch (IOException e) {
            log("Error sending warning to edge device: " + e.getMessage());
            return false;
        }
    }

    private static void cacheWarningMessage(String warningMessage) {
        try (FileWriter fw = new FileWriter(CACHE_FILE, true);
             PrintWriter pw = new PrintWriter(fw)) {
            String timeStamp = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date());
            pw.println(timeStamp + " " + warningMessage);
            log("Cached warning message: " + warningMessage);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void retryCachedMessages() {
        File file = new File(CACHE_FILE);

        while (true) {
            try {
                TimeUnit.SECONDS.sleep(5); // Retry every 60 seconds
                List<String> lines = new ArrayList<>();

                try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        lines.add(line);
                    }
                }

                List<String> remainingLines = new ArrayList<>();
                for (String line : lines) {
                    String[] parts = line.split(" ", 2);
                    String timeStamp = parts[0];
                    String warningMessage = parts[1] + " (from cache, sent at " + timeStamp + ")";

                    if (!sendWarningMessage(warningMessage)) {
                        remainingLines.add(line); // Keep in cache if sending fails
                    }
                }

                try (PrintWriter pw = new PrintWriter(new FileWriter(file, false))) {
                    for (String remainingLine : remainingLines) {
                        pw.println(remainingLine);
                    }
                }
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private static boolean sendWarningMessage(String warningMessage) {
        try {
            URL url = new URL(EDGE_DEVICE_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "text/plain");

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = warningMessage.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            return responseCode == 200;
        } catch (IOException e) {
            return false;
        }
    }

    static class SensorData {
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
}
