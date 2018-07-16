package example;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;

public class SampleVertxAction extends AbstractVerticle {

  @Override
  public void start() {
    vertx.eventBus().<JsonObject>consumer("actionInvoke", message -> {
      String name = "stranger";
      if (message.body().containsKey("name")) {
        name = message.body().getString("name");
      }
      message.reply(new JsonObject().put("greeting", "Hello " + name + " from Vert.x!"));
    });
  }
}