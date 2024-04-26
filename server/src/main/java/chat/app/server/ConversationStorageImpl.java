package chat.app.server;

import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import chat.app.server.models.Conversation;
import chat.app.server.models.Conversation.Message;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

@Singleton
public class ConversationStorageImpl implements ConversationStorage {

    ConcurrentHashMap<String, Conversation> conversations = new ConcurrentHashMap<>();

    @Override
    public Mono<Conversation> getConversation(String id) {

        if (conversations.containsKey(id)) {
            var conversation = conversations.get(id);
            return Mono.just(conversation);
        }

        return Mono.empty();
    }

    @Override
    public Mono<Conversation> createConversation(Conversation conversation) {
        if (conversations.containsKey(conversation.id())) {
            return Mono.error(new Throwable("Conversation already exists"));
        }

        conversations.put(conversation.id(), conversation);

        return Mono.just(conversation);
    }

    @Override
    public Mono<Conversation> pushMessage(String conversationId, Message message) {
        return getConversation(conversationId).map(c -> new Conversation(
                c.id(),
                c.users(),
                Stream.concat(c.messages().stream(), Stream.of(message)).toList()));
    }

}
