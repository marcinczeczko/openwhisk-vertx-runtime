package io.marcinczeczko.whisk.vertx.action;

import static io.marcinczeczko.whisk.vertx.action.VertxProxy.ACTION_DEPLOYED_FLAG;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public abstract class ProxyHandler implements Handler<RoutingContext> {

  abstract public void handle(RoutingContext ctx);

  protected boolean isActionDeployed(RoutingContext ctx) {
    return ctx.get(ACTION_DEPLOYED_FLAG) != null;
  }

  protected void signalError(RoutingContext ctx, String message) {
    signalError(ctx, message, null);
  }

  protected void signalError(RoutingContext ctx, Throwable cause) {
    signalError(ctx, null, cause);
  }

  protected void signalError(RoutingContext ctx, String message, Throwable cause) {
    ctx.put("error", true);
    if (message != null) {
      ctx.put("error_message", message);
    }
    if (cause != null) {
      ctx.put("error_cause", cause);
    }
    ctx.next();
  }

  protected void signalSuccess(RoutingContext ctx) {
    signalSuccess(ctx, null);
  }

  protected void signalSuccess(RoutingContext ctx, JsonObject result) {
    if (result != null) {
      ctx.put("result", result);
    }
    ctx.next();
  }
}
