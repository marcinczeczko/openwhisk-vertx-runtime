package example;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.ReplyFailure;
import io.vertx.core.json.JsonObject;

public class ExampleVerticle extends AbstractVerticle {

  @Override
  public void start() {
    vertx.eventBus().<JsonObject>consumer("actionInvoke", message -> {
      try {
        throw new Exception("noooooo");
      } catch (Exception ex) {
        message.fail(ReplyFailure.RECIPIENT_FAILURE.toInt(), ex.getMessage());
      }
    });
  }
}