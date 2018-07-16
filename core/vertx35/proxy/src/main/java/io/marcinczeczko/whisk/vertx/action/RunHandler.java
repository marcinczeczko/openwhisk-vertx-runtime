package io.marcinczeczko.whisk.vertx.action;

import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class RunHandler extends ProxyHandler {

  private Vertx vertx;
  private long runTimeoutMs;

  public RunHandler(Vertx vertx, long runTimeoutMs) {
    this.vertx = vertx;
    this.runTimeoutMs = runTimeoutMs;
  }

  @Override
  public void handle(RoutingContext ctx) {
    if (!isActionDeployed(ctx)) {
      signalError(ctx, "Cannot invoke an uninitialized action.");
      return;
    }

    JsonObject input = ctx.getBodyAsJson();
    JsonObject actionArg = input.getJsonObject("value", new JsonObject());
    System.setSecurityManager(new WhiskSecurityManager());

    vertx.eventBus().<JsonObject>send("actionInvoke", actionArg,
        owHeaders(input).setSendTimeout(runTimeoutMs), reply -> {
          if (reply.failed()) {
            signalError(ctx, "An error has occured while invoking the action", reply.cause());
          } else {
            JsonObject body = reply.result().body();
            if (body == null) {
              signalError(ctx, "The action returned null");
            }
            signalSuccess(ctx, body);
          }
        });
  }

  private DeliveryOptions owHeaders(JsonObject actionInput) {
    // Wrap OW variables into eventBus headers
    MultiMap owVariables = MultiMap.caseInsensitiveMultiMap();
    for (String field : new String[]{"api_host", "api_key", "namespace", "action_name",
        "activation_id",
        "deadline"}) {
      try {
        owVariables
            .set(String.format("__OW_%s", field.toUpperCase()), actionInput.getString(field));
      } catch (Exception e) {
      }
    }

    return new DeliveryOptions().setHeaders(owVariables);
  }
}
