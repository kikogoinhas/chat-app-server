package chat.app.server;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nimbusds.jwt.SignedJWT;
import io.lettuce.core.api.StatefulRedisConnection;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.security.authentication.AuthenticationRequest;
import io.micronaut.security.authentication.AuthenticationResponse;
import io.micronaut.security.authentication.provider.HttpRequestReactiveAuthenticationProvider;
import io.micronaut.security.token.jwt.signature.jwks.JwksSignature;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;
import java.io.IOException;
import javax.security.sasl.AuthenticationException;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

/** SessionAuthenticationProvider */
@Singleton
public class SessionAuthenticationProvider<A>
    implements HttpRequestReactiveAuthenticationProvider<A> {

  public static String COOKIE_KEY = "CHAT_APP_SID";

  private ObjectMapper mapper = ObjectMapper.getDefault();

  private JwksSignature jwksSignature;

  private StatefulRedisConnection<String, String> connection;

  public SessionAuthenticationProvider(
      StatefulRedisConnection<String, String> connection, JwksSignature jwksSignature) {
    this.connection = connection;
    this.jwksSignature = jwksSignature;
  }

  @Override
  public @NonNull Publisher<AuthenticationResponse> authenticate(
      @Nullable HttpRequest<A> requestContext,
      @NonNull AuthenticationRequest<String, String> authRequest) {

    Cookie cookie = requestContext.getCookies().get(COOKIE_KEY);

    if (cookie == null) {
      return Mono.just(AuthenticationResponse.failure());
    }

    return getSessionValue(cookie)
        .map(Session::accessToken)
        .flatMap(this::validateToken)
        .flatMap(this::getUsername)
        .log()
        .map(AuthenticationResponse::success)
        .or(Mono.just(AuthenticationResponse.failure()));
  }

  private Mono<Session> getSessionValue(Cookie cookie) {
    var commands = connection.async();

    return Mono.fromCompletionStage(() -> commands.get(cookie.getValue()))
        .flatMap(
            value ->
                Mono.deferContextual(
                    ctx -> {
                      try {
                        return Mono.just(mapper.readValue(value, Session.class));
                      } catch (IOException e) {
                        return Mono.error(AuthenticationResponse.exception(e.getMessage()));
                      }
                    }));
  }

  private Mono<SignedJWT> validateToken(String accessToken) {
    try {
      var signedJWT = SignedJWT.parse(accessToken);
      var isValid = jwksSignature.verify(signedJWT);
      if (isValid) {
        return Mono.just(signedJWT);
      }

      return Mono.error(new AuthenticationException("JWT Signature not valid"));
    } catch (Exception e) {
      return Mono.error(AuthenticationResponse.exception(e.getMessage()));
    }
  }

  private Mono<String> getUsername(SignedJWT jwt) {
    try {
      return Mono.just((String) jwt.getPayload().toJSONObject().get("email")).log();
    } catch (Exception e) {
      return Mono.error(AuthenticationResponse.exception(e.getMessage()));
    }
  }

  @Serdeable
  record Session(@JsonProperty("access_token") String accessToken) {}
}
