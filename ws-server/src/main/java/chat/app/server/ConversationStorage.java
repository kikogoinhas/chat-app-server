package chat.app.server;

import chat.app.server.models.Conversation;
import reactor.core.publisher.Mono;

/**
 * ConversationStorage
 */
public interface ConversationStorage {

    public Mono<Conversation> getConversation(String id);

    public Mono<Conversation> createConversation(Conversation conversation);

    public Mono<Conversation> pushMessage(String conversationId, Conversation.Message message);
}
