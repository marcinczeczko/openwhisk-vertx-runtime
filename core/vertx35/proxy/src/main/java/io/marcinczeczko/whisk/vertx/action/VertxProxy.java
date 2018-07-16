package io.marcinczeczko.whisk.vertx.action;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class VertxProxy {

  public final static String ACTION_DEPLOYED_FLAG = "actionDeployed";

  private Vertx vertx;

  private SecurityManager sm;

  private boolean actionDeployed = false;

  public static void main(String[] args) throws Exception {
    new VertxProxy().start(8080, 3, 3000);
  }

  private void start(int port, int startWaitSec, long runTimeoutMs) throws Exception {
    vertx = Vertx.vertx();
    Router router = Router.router(vertx);

    sm = System.getSecurityManager();

    router.route().handler(ctx -> {
      if (actionDeployed) {
        ctx.put(ACTION_DEPLOYED_FLAG, true);
      }
      ctx.next();
    });
    router.route().handler(BodyHandler.create());
    router.route().failureHandler(this::failureHandler);
    router.post("/init").handler(new InitHandler(vertx));
    router.post("/init").handler(this::writeResponse);

    router.post("/run").handler(new RunHandler(vertx, runTimeoutMs));
    router.post("/run").handler(this::writeResponse);

    CompletableFuture<HttpServer> server = new CompletableFuture<>();

    vertx.createHttpServer()
        .requestHandler(router::accept)
        .listen(port, ar -> {
          if (ar.succeeded()) {
            server.complete(ar.result());
          } else {
            server.completeExceptionally(ar.cause());
          }
        });

    server.get(startWaitSec, TimeUnit.SECONDS);
  }

  private void failureHandler(RoutingContext ctx) {
    ctx.put("error", true);
    ctx.put("error_cause", ctx.failure());
    writeResponse(ctx);
  }

  private void writeResponse(RoutingContext ctx) {
    System.setSecurityManager(sm);

    if (ctx.get(ACTION_DEPLOYED_FLAG) != null) {
      actionDeployed = true;
    }

    Boolean error = ctx.get("error");
    if (error != null) {
      String message = ctx.get("error_message");
      Throwable failure = ctx.get("error_cause");
      if (failure != null) {
        failure.printStackTrace(System.err);
      }

      ctx.response()
          .setStatusCode(HttpResponseStatus.BAD_GATEWAY.code())
          .end(new JsonObject()
              .put("error",
                  failure != null ? "An error has occurred (see logs for details): " + failure
                      : message)
              .encode());

    } else {
      JsonObject result = ctx.get("result");
      ctx.response().setStatusCode(HttpResponseStatus.OK.code())
          .end(result != null ? result.encode() : "OK");
    }
  }
}