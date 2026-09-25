package com.prerequix.net;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Thin HTTP client wrapper that performs a GET request to a remote JSON URL
 * and returns the raw response body as a {@link String}.
 *
 * <p>Uses the {@code java.net.http.HttpClient} introduced in Java 11.
 * Configured with a 10-second connect/read timeout and automatic redirect following.
 *
 * <p><b>Networking requirement:</b> This class satisfies the HTTP-request demonstration.
 * The companion {@link RemoteCourseParser} handles JSON parsing of the fetched data.
 */
public class CourseApiClient {

    /** URL of the sample courses JSON hosted on GitHub. */
    public static final String SAMPLE_COURSES_URL =
            "https://raw.githubusercontent.com/intejarhasnathprodhanalif/PreRequix/main/sample_courses.json";

    private final HttpClient httpClient;

    public CourseApiClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Sends an HTTP GET request to {@code url} and returns the response body.
     *
     * @param url the remote endpoint to fetch from
     * @return raw JSON string body
     * @throws IOException          on network error
     * @throws InterruptedException if the thread is interrupted while waiting
     * @throws RuntimeException     if the server returns a non-200 status code
     */
    public String fetchJson(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .header("User-Agent", "PreRequix-App/1.0")
                .GET()
                .build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new RuntimeException(
                    "HTTP request failed: " + status + " " + url);
        }

        return response.body();
    }

    /**
     * Convenience method: fetches the default sample courses JSON from GitHub.
     */
    public String fetchSampleCourses() throws IOException, InterruptedException {
        return fetchJson(SAMPLE_COURSES_URL);
    }
}
