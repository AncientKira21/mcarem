package com.ancientkira.mca.client.gui.immersive_library;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.ancientkira.mca.Config;
import com.ancientkira.mca.MCA;
import com.ancientkira.mca.client.gui.immersive_library.responses.ErrorResponse;
import com.ancientkira.mca.client.gui.immersive_library.responses.Response;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

public class Api {
    private static final Gson gson = new GsonBuilder()
            .registerTypeAdapterFactory(new RecordTypeAdapterFactory())
            .create();

    public static Response request(HttpMethod httpMethod, Class<? extends Response> expectedAnswer, String url) {
        return request(httpMethod, expectedAnswer, url, null, null);
    }

    public static Response request(HttpMethod httpMethod, Class<? extends Response> expectedAnswer, String url, Map<String, String> queryParams) {
        return request(httpMethod, expectedAnswer, url, queryParams, null);
    }

    public static Response request(HttpMethod httpMethod, Class<? extends Response> expectedAnswer, String url, Map<String, String> queryParams, Map<String, String> body) {
        try {
            boolean versionedPath = url.startsWith("v2/");
            String fullUrl = Config.getInstance().immersiveLibraryUrl + (versionedPath ? "/" : "/v1/") + url;

            // Append query params
            if (queryParams != null) {
                fullUrl = queryParams.keySet().stream()
                        .map(key -> key + "=" + URLEncoder.encode(queryParams.get(key), StandardCharsets.UTF_8))
                        .collect(Collectors.joining("&", fullUrl + "?", ""));
            }

            HttpURLConnection con = (HttpURLConnection) URI.create(fullUrl).toURL().openConnection();

            con.setConnectTimeout(10_000);
            con.setReadTimeout(20_000);
            // Set request method
            con.setRequestMethod(httpMethod.name());

            // Set request headers
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("Accept-Encoding", "gzip");
            con.setRequestProperty("Accept", "application/json");

            // Authenticate
            if (Auth.hasToken()) {
                con.setRequestProperty("Authorization", "Bearer " + Auth.getToken());
            }

            // Set request body
            if (body != null && !body.isEmpty()) {
                con.setDoOutput(true);
                Gson gson = new Gson();
                String jsonBody = gson.toJson(body);
                con.getOutputStream().write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }

            // Read the status first and always release the response stream/connection.
            try {
                int status = con.getResponseCode();
                java.io.InputStream stream = status >= 400 ? con.getErrorStream() : con.getInputStream();
                if (stream == null) return new ErrorResponse(status, "Empty library response");
                String response;
                try (java.io.InputStream input = "gzip".equals(con.getContentEncoding()) ? new GZIPInputStream(stream) : stream) {
                    response = IOUtils.toString(input, StandardCharsets.UTF_8);
                }
                if (status >= 400) {
                    JsonObject object = gson.fromJson(response, JsonObject.class);
                    return new ErrorResponse(status, object != null && object.has("message") ? object.get("message").getAsString() : response);
                }
                return gson.fromJson(response, expectedAnswer);
            } finally { con.disconnect(); }
        } catch (IOException e) {
            MCA.LOGGER.warn(e);
            return new ErrorResponse(-1, e.toString());
        } catch (Exception e) {
            MCA.LOGGER.error(e);
            return new ErrorResponse(-1, e.toString());
        }
    }

    public enum HttpMethod {
        POST, GET, DELETE, PUT
    }
}
