package org.example;

import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.function.Consumer;

@Log4j2
public class HttpClient implements AutoCloseable {

    private final Selector selector;

    public HttpClient() {
        try {
            selector = Selector.open();
        } catch (IOException e) {
            log.error("An exception occurred while opening a selector", e);
            throw new RuntimeException(e);
        }
    }

    public void get(String url, Consumer<Response> handler) {
        log.info("Sending a request to {}...", url);

        URL parsedUrl;
        try {
            parsedUrl = new URL(url);
        } catch (MalformedURLException e) {
            log.error("The provided URL is not correct.", e);
            throw new IllegalArgumentException(e);
        }

        if (!"http".equals(parsedUrl.getProtocol())) {
            throw new IllegalArgumentException("The protocol is not supported");
        }

        int port = parsedUrl.getPort();
        if (port == -1) {
            port = 80;
        }

        try {
            SocketChannel socketChannel = SocketChannel.open(new InetSocketAddress(parsedUrl.getHost(), port));
            socketChannel.configureBlocking(false);
            socketChannel.register(selector, SelectionKey.OP_READ, new Attachment(url, handler));

            String request = buildHttpRequest(parsedUrl.getHost(), parsedUrl.getPath());
            socketChannel.write(ByteBuffer.wrap(request.getBytes()));
        } catch (IOException e) {
            log.error("An exception occurred while creating a socket channel", e);
            throw new RuntimeException(e);
        }
    }

    public void processCompletedChannels() {
        try {
            if (selector.selectNow() == 0) {
                return;
            }
            var iterator = selector.selectedKeys().iterator();

            while (iterator.hasNext()) {
                var selectionKey = iterator.next();
                iterator.remove();
                SocketChannel channel = (SocketChannel) selectionKey.channel();
                String content = readResponse(channel);
                channel.close();
                Attachment handler = (Attachment) selectionKey.attachment();
                handler.handler().accept(new Response(handler.url(), content));
            }
        } catch (IOException e) {
            log.error("An exception occurred while processing socket channels", e);
            throw new RuntimeException(e);
        }
    }

    private String readResponse(SocketChannel socketChannel) {
        try {
            ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
            int size = socketChannel.read(byteBuffer);
            if (size == 0) {
                return null;
            }
            return new String(byteBuffer.array(), 0, byteBuffer.position());
        } catch (IOException e) {
            log.error("An exception occurred while reading the response", e);
            throw new RuntimeException(e);
        }
    }

    private String buildHttpRequest(String host, String path) {
        return "GET " + path + " HTTP/1.1\n" +
                "Host: " + host + "\n" +
                "\n";
    }

    @SneakyThrows
    @Override
    public void close() {
        selector.close();
    }

    private record Attachment(
            String url,
            Consumer<Response> handler
    ) {}
}
