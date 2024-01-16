package org.example;

import lombok.extern.log4j.Log4j2;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * This is a simple program that sends HTTP requests to a list of URLs in a non-blocking manner.
 */
@Log4j2
public class Main {
    public static void main(String[] args) {
        List<String> urls = List.of(
                "http://localhost:8080/slow-hello", // takes 5 seconds
                "http://localhost:8080/hello"       // takes 1 second
        );

        try (HttpClient httpClient = new HttpClient()) {
            Deque<Event> eventQueue = new ArrayDeque<>();
            urls.forEach(url -> eventQueue.offer(new Event(url)));

            while (!Thread.currentThread().isInterrupted()) {
                Event event = eventQueue.poll();
                if (event != null) {
                    httpClient.get(
                            event.url(),
                            response -> log.info(response.toString())
                    );
                }
                httpClient.processCompletedChannels();
            }
        }
    }
}
