package chat.app.server;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.redis.testcontainers.RedisContainer;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.http.simple.SimpleHttpRequest;
import io.micronaut.security.authentication.AuthenticationRequest;
import io.micronaut.security.token.jwt.signature.jwks.JwksSignature;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.testcontainers.containers.wait.strategy.WaitStrategy;
import org.testcontainers.containers.wait.strategy.WaitStrategyTarget;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;

/** SessionAuthenticationProviderTest */
@MicronautTest
@Testcontainers
class SessionAuthenticationProviderTest {
  private static WaitStrategy waitUntilConnect =
      new WaitStrategy() {

        @Override
        public void waitUntilReady(WaitStrategyTarget waitStrategyTarget) {
          RedisClient client = RedisClient.create(container.getRedisURI());
          var success = false;
          while (!success) {
            try {

              client.connect();
              success = true;
              client.close();
            } catch (Exception e) {
            }
          }
        }

        @Override
        public WaitStrategy withStartupTimeout(Duration startupTimeout) {
          // TODO Auto-generated method stub
          throw new UnsupportedOperationException("Unimplemented method 'withStartupTimeout'");
        }
      };

  @Container
  private static RedisContainer container =
      new RedisContainer(RedisContainer.DEFAULT_IMAGE_NAME.withTag(RedisContainer.DEFAULT_TAG))
          .waitingFor(waitUntilConnect);

  @Inject private ObjectMapper mapper;

  private static String COOKIE_VALUE = "123";
  private RedisClient client;

  @BeforeEach
  void beforeEach() {
    client = RedisClient.create(container.getRedisURI());
  }

  StatefulRedisConnection<String, String> createSession(SignedJWT jwt) throws IOException {
    StatefulRedisConnection<String, String> connection = client.connect();
    RedisCommands<String, String> commands = connection.sync();

    var session = new SessionAuthenticationProvider.Session(jwt.serialize());
    var value = mapper.writeValueAsString(session);

    commands.set(COOKIE_VALUE, value);
    return connection;
  }

  @Test
  void should_return_principal() throws IOException, JOSEException {
    var header = new JWSHeader.Builder(JWSAlgorithm.RS256).build();
    var issueTime = Instant.now();
    var expirationTime = issueTime.plusSeconds(60);

    var email = "test@test.com";

    var claimSet =
        new JWTClaimsSet.Builder()
            .issuer("test")
            .subject("test")
            .audience("test")
            .issueTime(Date.from(issueTime))
            .expirationTime(Date.from(expirationTime))
            .claim("email", email)
            .build();

    var signedJWT = new SignedJWT(header, claimSet);
    var keys = new RSAKeyGenerator(2048).algorithm(JWSAlgorithm.RS256).generate();

    signedJWT.sign(new RSASSASigner(keys.toRSAKey()));

    var connection = createSession(signedJWT);
    HttpRequest<String> request =
        new SimpleHttpRequest<String>(HttpMethod.GET, "http://localhost/conversations", "")
            .cookie(Cookie.of(SessionAuthenticationProvider.COOKIE_KEY, COOKIE_VALUE));

    @SuppressWarnings("unchecked")
    AuthenticationRequest<String, String> authRequest = mock(AuthenticationRequest.class);

    JwksSignature jwksSignature = mock(JwksSignature.class);
    when(jwksSignature.verify(Mockito.any())).then(invovation -> true);

    var provider = new SessionAuthenticationProvider<String>(connection, jwksSignature);
    var result = Mono.from(provider.authenticate(request, authRequest)).block();
    var actual = result.getAuthentication().map(i -> i.getName()).get();

    Assert.assertEquals(email, actual);
  }
}
