package chat.app.server.models;

import io.micronaut.serde.annotation.Serdeable;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.List;

/** Conversation */
public record Conversation(String id, List<User> users, List<Message> messages) {

  public record Message(User user, Content content) {

    @Serdeable
    public static class Content {
      private String mediaType;
      private byte[] data;

      public Content(String mediaType, byte[] data) {
        this.mediaType = mediaType;
        this.data = data;
      }

      public String getMediaType() {
        return mediaType;
      }

      public byte[] getData() {
        return data;
      }
    }

    public static class TextContent {
      public static Content of(String data) {
        return new Content("text/plain", Charset.defaultCharset().encode(data).array());
      }

      public static String from(Content content) {
        if (!content.getMediaType().equals("text/plain")) {
          throw new IllegalArgumentException(
              String.format("Invalid text media type %s", content.getMediaType()));
        }

        return Charset.defaultCharset().decode(ByteBuffer.wrap(content.getData())).toString();
      }
    }
  }
}
