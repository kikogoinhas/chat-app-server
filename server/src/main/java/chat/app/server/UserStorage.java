package chat.app.server;

import chat.app.server.models.User;
import reactor.core.publisher.Mono;

/**
 * UserStorage
 */
public interface UserStorage {

    public Mono<User> getUser(String username);

    public Mono<User> addUser(String username);
}
