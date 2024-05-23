package chat.app.server;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import chat.app.server.models.Conversation;
import chat.app.server.models.Conversation.Message;
import chat.app.server.models.User;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.websocket.CloseReason;
import io.micronaut.websocket.WebSocketBroadcaster;
import io.micronaut.websocket.WebSocketSession;

import io.micronaut.websocket.annotation.OnClose;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.OnOpen;
import io.micronaut.websocket.annotation.ServerWebSocket;
import reactor.core.publisher.Mono;

/**
 * PrivateChatHandler
 */
@ServerWebSocket("/ws/chat/{id}")
@Prototype
public class PrivateChatHandler {

    private static final Logger LOG = LoggerFactory.getLogger(PrivateChatHandler.class);

    private final WebSocketBroadcaster broadcaster;

    private ConversationStorage conversationStorage;

    private UserStorage userStorage;

    public PrivateChatHandler(WebSocketBroadcaster broadcaster, ConversationStorage conversationStorage,
            UserStorage userStorage) {
        this.broadcaster = broadcaster;
        this.conversationStorage = conversationStorage;
        this.userStorage = userStorage;
    }

    @OnOpen
    public Mono<List<Message.Content>> onOpen(String id, WebSocketSession session) {
        log("onOpen", session, id);
        var principal = session.getUserPrincipal();

        if (principal.isEmpty()) {
            LOG.info(String.format("Principal is empty on session %s", session.getId()));
            session.close(CloseReason.INTERNAL_ERROR);
            return Mono.empty();
        }

        var username = principal.get().getName();

        return userStorage
                .getUser(username)
                .flatMap(user -> getUserMessages(id, user));
    }

    @OnMessage
    public Mono<Void> onMessage(
            String id,
            Conversation.Message.Content content,
            WebSocketSession session) {

        var username = session.getUserPrincipal().get().getName();

        return userStorage
                .getUser(username)
                .flatMap(user -> {
                    var message = new Conversation.Message(user, content);
                    return conversationStorage
                            .pushMessage(id, message)
                            .and(broadcaster.broadcast(message.content(),
                                    s -> s.getUriVariables().get("id", String.class).get().equals(id)));
                });
    }

    @OnClose
    public void onClose(
            String id,
            WebSocketSession session) {
        LOG.info(String.format("Closing session %s on conversation %s", session.getId(), id));
    }

    private Mono<List<Message.Content>> getUserMessages(String conversationId, User user) {
        return conversationStorage
                .getConversation(conversationId)
                .mapNotNull(c -> {
                    var index = c.users().indexOf(user);

                    if (index == -1) {
                        throw new Error(String.format("User %s does not belong to conversation", user.username()));
                    }

                    return c.messages().stream().map(m -> m.content());
                })
                .map(s -> s.toList());
    }

    private void log(String event, WebSocketSession session, String id) {
        LOG.info("* WebSocket: {} received for session {} with id {}",
                event, session.getId(), id);
    }

}
