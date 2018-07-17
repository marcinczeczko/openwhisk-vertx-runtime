package example;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class SampleVertxWebAction extends AbstractVerticle {

  @Override
  public void start() {
    vertx.eventBus().<JsonObject>consumer("actionInvoke", message -> {
      JsonObject headers = new JsonObject();
      message.headers().entries().forEach(entry -> headers.put(entry.getKey(), entry.getValue()));

      JsonObject responseHeaders =
          new JsonObject()
              .put("Set-Cookie", new JsonArray()
                  .add("UserID=Jane; Max-Age=3600; Version=")
                  .add("SessionID=abcdefg123456; Path=/"))
              .put("Content-Type", "text/html");

      JsonObject result = new JsonObject()
          .put("statusCode", 200)
          .put("headers", responseHeaders)
          .put("body", String.format(
              "<html>"
                  + "<body>"
                  + "<h2>OW Variables</h2>"
                  + "<pre>%s</pre>"
                  + "<h2>Action Payload</h2>"
                  + "<pre>%s</pre>"
                  + "</body>"
                  + "</html>",
              headers.encodePrettily(), message.body().encodePrettily()));

      message.reply(result);
    });
  }
}