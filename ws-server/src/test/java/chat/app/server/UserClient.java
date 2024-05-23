package chat.app.server;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import chat.app.server.models.Conversation;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.websocket.annotation.ClientWebSocket;
import io.micronaut.websocket.annotation.OnMessage;

@Requires(property = "spec.name", value = "PrivateChatHandlerTest")
@ClientWebSocket("/ws/chat/{id}")
public abstract class UserClient implements AutoCloseable {
    private final Deque<Conversation.Message.Content> messageHistory = new ConcurrentLinkedDeque<>();

    public Conversation.Message.Content getLatestMessage() {
        return messageHistory.peekLast();
    }

    public List<Conversation.Message.Content> getMessagesChronologically() {
        return new ArrayList<>(messageHistory);
    }

    @OnMessage
    void onMessage(Conversation.Message.Content content) {
        messageHistory.add(content);
    }

    abstract void send(@NonNull Conversation.Message.Content message);
}
