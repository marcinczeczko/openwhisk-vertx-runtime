package io.marcinczeczko.whisk.vertx.action;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class VertxProxy {

  private Vertx vertx;

  private String deploymentId = null;

  private SecurityManager sm;

  private long runTimeoutMs;

  public static void main(String[] args) throws Exception {
    new VertxProxy().start(8080, 3, 3000);
  }

  private void start(int port, int startWaitSec, long runTimeoutMs) throws Exception {
    this.runTimeoutMs = runTimeoutMs;
    vertx = Vertx.vertx();
    Router router = Router.router(vertx);

    sm = System.getSecurityManager();

    router.route().handler(BodyHandler.create());
    router.post("/init").handler(this::init);
    router.post("/init").handler(this::writeResponse);

    router.post("/run").handler(this::run);
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

  private Future<Path> saveJar(byte[] jar) {
    Future<Path> result = Future.future();
    try {
      File file = File.createTempFile("useraction", ".jar");

      vertx.fileSystem().writeFile(file.getAbsolutePath(), Buffer.buffer(jar), res -> {
        if (res.failed()) {
          result.fail(res.cause());
        } else {
          result.complete(file.toPath());
        }
      });

    } catch (IOException e) {
      return Future.failedFuture(e);
    }

    return result;
  }

  private void init(RoutingContext ctx) {
    if (deploymentId != null) {
      String errorMessage = "Cannot initialize the action more than once.";
      System.err.println(errorMessage);
      ctx.put("error", true);
      ctx.put("error_message", "Cannot initialize the action more than once.");
      ctx.next();
      return;
    }

    if (ctx.getBody().length() > 0 && ctx.getBodyAsJson().containsKey("value")) {
      JsonObject message = ctx.getBodyAsJson().getJsonObject("value");

      String verticle = message.getString("main");
      byte[] jar = message.getBinary("code");

      saveJar(jar).setHandler(ar -> {
        if (ar.failed()) {
          ctx.put("error", true);
          ctx.put("error_cause", ar.cause());
          ctx.next();
        } else {
          Path jarPath = ar.result();
          if (!jarPath.toFile().exists()) {
            ctx.put("error", true);
            ctx.put("error_message",
                String.format("%s file does not exist", jarPath.toAbsolutePath()));
            ctx.next();
          }

          // Jar file saved, try to deploy the verticle
          DeploymentOptions options = new DeploymentOptions()
              .setIsolationGroup("vertx_action_group")
              .setExtraClasspath(Collections.singletonList(jarPath.toAbsolutePath().toString()));

          vertx.deployVerticle(verticle, options, deployed -> {
            if (deployed.failed()) {
              ctx.put("error", true);
              ctx.put("error_cause", deployed.cause());
              ctx.next();
            } else {
              deploymentId = deployed.result();
              ctx.next();
            }
          });
        }
      });
    } else {
      ctx.put("error", true);
      ctx.put("error_message", "Missing main/no code to execute.");
      ctx.next();
    }
  }

  private void run(RoutingContext ctx) {
    if (deploymentId == null) {
      ctx.put("error", true);
      ctx.put("error_message", "Cannot invoke an uninitialized action.");
      ctx.next();
      return;
    }

    JsonObject input = ctx.getBodyAsJson();

    JsonObject actionArg = input.getJsonObject("value", new JsonObject());

    System.setSecurityManager(new WhiskSecurityManager());

    vertx.eventBus().<JsonObject>send("actionInvoke", actionArg,
        owHeaders(input).setSendTimeout(runTimeoutMs), reply -> {
          if (reply.failed()) {
            ctx.put("error", true);
            ctx.put("error_message", "An error has occured while invoking the action");
            ctx.put("error_cause", reply.cause());
            ctx.next();
          } else {
            JsonObject body = reply.result().body();
            if (body == null) {
              ctx.put("error", true);
              ctx.put("error_message", "The action returned null");
              ctx.next();
            }
            ctx.put("result", body);
            ctx.next();
          }
        });
  }

  private void writeResponse(RoutingContext ctx) {
    System.setSecurityManager(sm);

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

  private DeliveryOptions owHeaders(JsonObject actionInput) {
    // Wrap OW variables into eventBus headers
    MultiMap owVariables = MultiMap.caseInsensitiveMultiMap();
    for (String field : new String[]{"api_host", "api_key", "namespace", "action_name", "activation_id",
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