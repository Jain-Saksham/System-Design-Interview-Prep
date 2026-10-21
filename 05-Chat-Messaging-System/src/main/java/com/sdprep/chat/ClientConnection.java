package com.sdprep.chat;

/**
 * A connected device. Production: a WebSocket held open by a gateway server; {@code deliver}
 * writes a frame to the socket. Here it's an in-memory callback.
 */
@FunctionalInterface
public interface ClientConnection {

    void deliver(Message message);
}
