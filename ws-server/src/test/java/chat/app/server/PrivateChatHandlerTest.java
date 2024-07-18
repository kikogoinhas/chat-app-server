package chat.app.server;

import chat.app.server.models.Conversation;
import chat.app.server.models.Conversation.Message;
import com.redis.testcontainers.RedisContainer;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.uri.UriBuilder;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.websocket.WebSocketClient;
import jakarta.inject.Inject;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import reactor.core.publisher.Flux;

/** PrivateChatHandlerTest */
@Property(name = "spec.name", value = "PrivateChatHandlerTest")
@MicronautTest
public class PrivateChatHandlerTest {

  @Inject UserStorage userStorage;

  @Inject ConversationStorage conversationStorage;

  @Inject EmbeddedServer server;

  @Inject WebSocketClient webSocketClient;

  @Container
  public RedisContainer redis =
      new RedisContainer(RedisContainer.DEFAULT_IMAGE_NAME.withTag(RedisContainer.DEFAULT_TAG))
          .withExposedPorts(6379);

  UserClient getUserClient(String conversationId, BasicAuthCredentials credentials) {
    var uri =
        UriBuilder.of("ws://localhost")
            .port(server.getPort())
            .path("ws")
            .path("chat")
            .path("{id}")
            .expand(Map.of("id", conversationId));

    var request = HttpRequest.GET(uri);

    if (credentials != null) {
      request.header("Authorization", credentials.toHeaderValue());
    }

    return Flux.from(webSocketClient.connect(UserClient.class, request)).blockFirst();
  }

  static record BasicAuthCredentials(String username, String password) {
    public String toHeaderValue() {
      var credentialsBuffer =
          Charset.defaultCharset().encode(String.format("%s:%s", username, password));
      var base64CredentialsBuffer = Base64.getEncoder().encode(credentialsBuffer);
      var headerValue = Charset.defaultCharset().decode(base64CredentialsBuffer).toString();
      return String.format("Basic %s", headerValue);
    }
  }

  @Test
  void testSendMessage() {
    var aliceCredentials = new BasicAuthCredentials("Alice", "password");
    var bobCredentials = new BasicAuthCredentials("Bob", "password");

    var alice = userStorage.addUser(aliceCredentials.username()).block();
    var bob = userStorage.addUser(bobCredentials.username()).block();

    var conversation =
        conversationStorage
            .createConversation(new Conversation("AB", List.of(alice, bob), List.of()))
            .block();

    var aliceClient = getUserClient(conversation.id(), aliceCredentials);
    var bobClient = getUserClient(conversation.id(), bobCredentials);

    aliceClient.send(Message.TextContent.of("Hello Bob!"));
    bobClient.send(Message.TextContent.of("Hello Alice!"));

    Awaitility.waitAtMost(Duration.ofSeconds(60))
        .until(
            () -> {
              return bobClient.getMessagesChronologically().size() == 2
                  && aliceClient.getMessagesChronologically().size() == 2;
            });
  }
}
