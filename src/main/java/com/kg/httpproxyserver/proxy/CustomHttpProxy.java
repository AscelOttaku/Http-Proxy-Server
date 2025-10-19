package com.kg.httpproxyserver.proxy;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import jakarta.websocket.OnClose;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

@Slf4j
public class CustomHttpProxy {
    private final int remotePortNumber;
    private ServerSocket serverSocket;

    public CustomHttpProxy(int port) {
        remotePortNumber = port;
        initialize();
    }

    private void initialize() {
        try (ServerSocket serverSocket = new ServerSocket(remotePortNumber)) {
            log.info("Starting proxy server on port {}", remotePortNumber);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                log.info("Client is connected {}", clientSocket.getInetAddress());
                log.info("Incoming request from {}", clientSocket.getRemoteSocketAddress().toString());

                Runnable runnable = () -> handleClient(clientSocket);
                new Thread(runnable).start();
            }
        } catch (IOException e) {
            log.error("Proxy Server Error", e);
        }
    }

    private void handleClient(Socket clientSocket) {
        try (InputStream clientIn = clientSocket.getInputStream();
             OutputStream clientOut = clientSocket.getOutputStream()) {

            // Read the client's request
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] requestProportion = new byte[4096];
            int len;

            while ((len = clientIn.read(requestProportion)) != -1) {
                buffer.write(requestProportion, 0, len);

                if (buffer.toString(StandardCharsets.UTF_8).contains("\r\n\r\n"))
                    break;
            }

            String request = buffer.toString();

            String hostName = extractHost(request);
            log.info("Host: {}", hostName);

            int port = extractDefaultPort();
            log.info("Port: {}", port);

            log.info("Destination url: {}", extractDestinationUrl(request));

            Map<String, Object> data = extractData(request);
            if (data != null) {
                var result = deleteBody(buffer);
                if (result == 0) log.info("Deleting body");

                data.putIfAbsent("txnId", System.currentTimeMillis());

                // Write new Body to buffer
                String jsonData = new Gson().toJson(data);
                buffer.write(jsonData.getBytes(StandardCharsets.UTF_8));
                updateContentLength(buffer, jsonData.length());
            }

            log.info("Data: {}", data);

            log.info("Ip address: {}", clientSocket.getInetAddress().getHostAddress());

            if (hostName == null) {
                log.error("Host name is null");
                return;
            }

            // Connect to the target server
            try (Socket destination = new Socket(hostName, port)) {
                OutputStream targetOut = destination.getOutputStream();
                InputStream targetIn = destination.getInputStream();

                // Send Data to destination url
                targetOut.write(buffer.toByteArray(), 0, buffer.size());
                targetOut.flush();

                // Send Data back to user
                byte[] responseProportion = new byte[4096];
                int bytesRead;
                while ((bytesRead = targetIn.read(responseProportion)) != -1) {
                    clientOut.write(responseProportion, 0, bytesRead);
                }
                clientOut.flush();
            }
        } catch (IOException e) {
            log.error("Proxy server error", e);
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                log.error("Proxy server error", e);
            }
        }
    }

    private static int deleteBody(ByteArrayOutputStream buffer) {
        byte[] fullRequest = buffer.toByteArray();

        String request = buffer.toString();
        int indexOfDataStart = request.indexOf("{");
        if (indexOfDataStart == -1) return -1;
        byte[] requestWithoutBody = Arrays.copyOfRange(fullRequest, 0, indexOfDataStart);

        buffer.reset();
        buffer.write(requestWithoutBody, 0, requestWithoutBody.length);
        return 0;
    }

    private static String extractHost(String request) {
        String hostHeader = "Host: ";
        int hostStartIndex = request.indexOf(hostHeader);
        if (hostStartIndex != -1) {
            int hostEndIndex = request.indexOf("\r\n", hostStartIndex);
            if (hostEndIndex != -1) {
                String hostName = request.substring(hostStartIndex + hostHeader.length(), hostEndIndex).trim();
                int portNumber = hostName.indexOf(':');
                if (portNumber != -1) {
                    return hostName.substring(0, portNumber);
                }
            }
        }
        return null;
    }

    private static int extractPort(String request) {
        String host = extractHost(request);
        if (host != null) {
            if (host.contains(":")) {
                String portString = host.substring(host.indexOf(":") + 1);
                try {
                    return Integer.parseInt(portString);
                } catch (NumberFormatException e) {
                    log.warn("Port written in correct format");
                }
            }
        }
        return 80;
    }

    private static Map<String, Object> extractData(String request) {
        String startBody = "{";
        String endBody = "}";
        int indexOf = request.indexOf(startBody);
        if (indexOf != -1) {
            int endBodyIndex = request.indexOf(endBody);
            String body = request.substring(indexOf, endBodyIndex + 1);

            TypeToken<Map<String, Object>> typeToken = new TypeToken<>() {};
            Map<String, Object> data = new Gson().fromJson(body, typeToken);

            // Need replace number that is .0
            data.replaceAll((key, value) -> {
                if (value instanceof Double d) {
                    if (d % 1 == 0)
                        return d.intValue();
                }
                return value;
            });
            return data;
        }
        return null;
    }

    private static int extractDefaultPort() {
        return 80;
    }

    private static String extractDestinationUrl(String request) {
        String url = "http";
        int indexOfUrl = request.indexOf(url);
        if (indexOfUrl != -1) {
            int endIndex = request.indexOf(' ', indexOfUrl);
            if (endIndex != -1) {
                return request.substring(indexOfUrl, endIndex);
            }
        }
        return null;
    }

    private void updateContentLength(ByteArrayOutputStream buffer, int newLength) throws IOException {
        String request = buffer.toString(StandardCharsets.UTF_8);

        // Разделяем заголовки и тело
        int headerEndIndex = request.indexOf("\r\n\r\n");
        if (headerEndIndex == -1) {
            return;
        }

        String headers = request.substring(0, headerEndIndex);

        // Заменяем Content-Length, если он есть, или добавляем его
        String updatedHeaders;
        if (headers.toLowerCase().contains("content-length:")) {
            updatedHeaders = headers.replaceAll("(?i)Content-Length: \\d+", "Content-Length: " + newLength);
        } else {
            updatedHeaders = headers + "\r\nContent-Length: " + newLength;
        }

        // Воссоздаём полный запрос (заголовки + тело)
        String newRequest = updatedHeaders + "\r\n\r\n";

        // Очищаем старый буфер и записываем обновлённые заголовки
        buffer.reset();
        buffer.write(newRequest.getBytes(StandardCharsets.UTF_8));
    }

}
