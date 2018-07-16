package example;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class SampleVertxWebAction extends AbstractVerticle {

  @Override
  public void start() {
    vertx.eventBus().<JsonObject>consumer("actionInvoke", message -> {
      JsonObject result = new JsonObject()
          .put("headers",
              new JsonObject()
                  .put("Set-Cookie", new JsonArray()
                      .add("UserID=Jane; Max-Age=3600; Version=")
                      .add("SessionID=asdfgh123456; Path=/"))
                  .put("Content-Type", "text/html"))
          .put("statusCode", 200)
          .put("body", "<html><body><h3>hello</h3></body></html>");

      message.reply(result);
    });
  }
}