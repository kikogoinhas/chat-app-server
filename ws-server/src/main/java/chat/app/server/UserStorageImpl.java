package chat.app.server;

import java.util.concurrent.ConcurrentHashMap;

import chat.app.server.models.User;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

@Singleton
public class UserStorageImpl implements UserStorage {

    ConcurrentHashMap<String, User> users = new ConcurrentHashMap<>();

    @Override
    public Mono<User> getUser(String username) {
        if (users.containsKey(username)) {
            var user = users.get(username);
            return Mono.just(user);
        }

        return Mono.error(new Throwable("User does not exist"));

    }

    @Override
    public Mono<User> addUser(String username) {
        if (users.containsKey(username)) {
            return Mono.error(new Throwable("User already exists"));
        }

        var user = new User(username);
        users.put(username, user);

        return Mono.just(user);
    }
}
