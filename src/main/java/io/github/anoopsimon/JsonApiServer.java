package io.github.anoopsimon;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

public class JsonApiServer {

    HttpServer server;
    public static void main(String[] args) throws IOException {
        new JsonApiServer().start();
    }

    public void stop() {
        if(server!=null)
        server.stop(0);
    }


        public void start() throws IOException {
            String jsonContent = new String(Files.readAllBytes(Paths.get(System.getProperty("user.dir"),"/src/main/java/io/github/anoopsimon/data/mock_data.json")));


//        if (args.length < 1) {
//            System.out.println("Usage: java -jar io.github.anoopsimon.JsonApiServer.jar <path_to_json>");
//            return;
//        }

        //String jsonFilePath = args[0];
        //String jsonContent = new String(Files.readAllBytes(Paths.get(jsonFilePath)));
        //JSONObject apiData = new JSONObject(jsonContent);

        JSONObject apiData = new JSONObject(jsonContent);

        int serverPort = apiData.getInt("serverPort");
        JSONObject endpoints = apiData.getJSONObject("endpoints");

         server = HttpServer.create(new InetSocketAddress(serverPort), 0);
        endpoints.keySet().forEach(path -> {
            JSONArray methodsConfig = endpoints.getJSONArray(path);
            server.createContext(path, new AdvancedMethodHandler(methodsConfig));
        });

        server.createContext("/api-docs", new ApiDocsHandler(endpoints));
        server.setExecutor(null);
        server.start();
        System.out.println("Server started on port " + serverPort);
    }

    static class ApiDocsHandler implements HttpHandler {
        private final JSONObject endpoints;

        public ApiDocsHandler(JSONObject endpoints) {
            this.endpoints = endpoints;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response = generateHtmlDocumentation(endpoints);
            exchange.getResponseHeaders().add("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, response.length());
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }

        private String generateHtmlDocumentation(JSONObject endpoints) {
            StringBuilder html = new StringBuilder();
            html.append("<html><head><title>API Documentation</title></head><body>");
            html.append("<h1>API Endpoints</h1><table border='1'><tr><th>Endpoint</th><th>Method</th><th>Response Code</th><th>Required Headers</th><th>Required Body</th></tr>");

            for (String path : endpoints.keySet()) {
                JSONArray methods = endpoints.getJSONArray(path);
                for (int i = 0; i < methods.length(); i++) {
                    JSONObject methodConfig = methods.getJSONObject(i);
                    html.append("<tr>")
                            .append("<td>").append(path).append("</td>")
                            .append("<td>").append(methodConfig.getString("method")).append("</td>")
                            .append("<td>").append(methodConfig.getInt("responseCode")).append("</td>")
                            .append("<td>").append(methodConfig.getJSONArray("requiredHeaders").join(", ").replace("\"", "")).append("</td>")
                            .append("<td>").append(methodConfig.has("requiredBody") ? methodConfig.getJSONObject("requiredBody").toString() : "None").append("</td>")
                            .append("</tr>");
                }
            }

            html.append("</table></body></html>");
            return html.toString();
        }
    }
    static class AdvancedMethodHandler implements HttpHandler {
        private final JSONArray methodsConfig;

        public AdvancedMethodHandler(JSONArray methodsConfig) {
            this.methodsConfig = methodsConfig;
        }
        private boolean areRequiredHeadersPresent(HttpExchange exchange, JSONObject methodConfig) {
            return methodConfig.getJSONArray("requiredHeaders").toList().stream()
                    .allMatch(header -> exchange.getRequestHeaders().containsKey((String) header));
        }

        private boolean isHeaderRequired(JSONObject methodConfig){
            Object e= methodConfig.get("requiredHeaders");
            System.out.println("Headers Required :" + e!= null);

            return e != null;

        }

        private boolean isRequestBodyValid(HttpExchange exchange, JSONObject requiredBody) {
            try {
                InputStream is = exchange.getRequestBody();
                String requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                JSONObject bodyJson = new JSONObject(requestBody);

                for (String key : requiredBody.keySet()) {
                    if (!bodyJson.has(key) || !(bodyJson.get(key) instanceof String)) {
                        return false;
                    }
                }
                return true;
            } catch (JSONException e) {
                return false;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        private void sendJsonResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            sendResponse(exchange, statusCode, response);
        }

        private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
            exchange.sendResponseHeaders(statusCode, response.length());
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String requestMethod = exchange.getRequestMethod();
            for (int i = 0; i < methodsConfig.length(); i++) {
                JSONObject methodConfig = methodsConfig.getJSONObject(i);
                if (methodConfig.getString("method").equals(requestMethod))
                {
                    if (methodConfig.has("requiredHeaders") && !areRequiredHeadersPresent(exchange, methodConfig)) {
                        sendResponse(exchange, 400, "Required headers missing");
                        return;
                    }

                    if ("POST".equals(requestMethod) && methodConfig.has("requiredBody")) {
                        if (!isRequestBodyValid(exchange, methodConfig.getJSONObject("requiredBody"))) {
                            sendResponse(exchange, 400, "Invalid request body");
                            return;
                        }
                    }

                    sendJsonResponse(exchange, methodConfig.getInt("responseCode"),
                            methodConfig.get("response").toString());
                    return;
                }
            }
            sendResponse(exchange, 405, "Method Not Allowed");
        }

    }
}
