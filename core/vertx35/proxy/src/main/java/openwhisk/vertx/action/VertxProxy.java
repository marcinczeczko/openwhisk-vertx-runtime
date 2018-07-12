package openwhisk.vertx.action;

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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.MissingFormatArgumentException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class VertxProxy {

  private Vertx vertx;

  private String deploymentId = null;

  public static void main(String[] args) throws Exception {
    new VertxProxy().start(8080, 5);
  }

  private void start(int port, int startWaitSec) throws Exception {
    vertx = Vertx.vertx();
    Router router = Router.router(vertx);

    router.route().handler(BodyHandler.create());
    router.route().failureHandler(this::failureHandler);
    router.post("/init").handler(this::initHandler);
    router.post("/run").handler(this::runHandler);

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
    Throwable failure = ctx.failure();
    failure.printStackTrace(System.err);

    writeLogMarkers();

    ctx.response()
        .setStatusCode(HttpResponseStatus.BAD_GATEWAY.code())
        .end(new JsonObject()
            .put("error", "An error has occurred (see logs for details): " + failure)
            .encode());
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

  private static void writeLogMarkers() {
    System.out.println("XXX_THE_END_OF_A_WHISK_ACTIVATION_XXX");
    System.err.println("XXX_THE_END_OF_A_WHISK_ACTIVATION_XXX");
    System.out.flush();
    System.err.flush();
  }

  private void initHandler(RoutingContext ctx) {
    if (deploymentId != null) {
      String errorMessage = "Cannot initialize the action more than once.";
      System.err.println(errorMessage);
      ctx.fail(new IllegalStateException(errorMessage));
    }

    if (ctx.getBodyAsJson().containsKey("value")) {
      JsonObject message = ctx.getBodyAsJson().getJsonObject("value");

      String verticle = message.getString("main");
      byte[] jar = message.getBinary("code");

      saveJar(jar).setHandler(ar -> {
        if (ar.failed()) {
          ctx.fail(ar.cause());
        } else {
          Path jarPath = ar.result();
          if (!jarPath.toFile().exists()) {
            ctx.fail(new FileNotFoundException(
                String.format("%s file does not exist", jarPath.toAbsolutePath())));
          }

          // Jar file saved, try to deploy the verticle
          DeploymentOptions options = new DeploymentOptions()
              .setIsolationGroup("vertx_action_group")
              .setExtraClasspath(Collections.singletonList(jarPath.toAbsolutePath().toString()));

          vertx.deployVerticle(verticle, options, deployed -> {
            if (deployed.failed()) {
              ctx.fail(deployed.cause());
            } else {
              deploymentId = deployed.result();
              ctx.response().setStatusCode(HttpResponseStatus.OK.code()).end("OK");
            }
          });
        }
      });
    } else {
      ctx.fail(new MissingFormatArgumentException("Missing main/no code to execute."));
    }
  }

  private void runHandler(RoutingContext ctx) {
    if (deploymentId == null) {
      ctx.fail(new IllegalStateException("Cannot invoke an uninitialized action."));
    }

    JsonObject input = ctx.getBodyAsJson();
    JsonObject actionArg = input.getJsonObject("value", new JsonObject());

    vertx.eventBus().<JsonObject>send("actionInvoke", actionArg, owHeaders(input), reply -> {
      if (reply.failed()) {
        ctx.fail(new InvocationTargetException(reply.cause(),
            "An error has occured while invoking the action"));
      } else {
        JsonObject body = reply.result().body();
        if (body == null) {
          ctx.fail(new NullPointerException("The action returned null"));
        }
        ctx.response().setStatusCode(HttpResponseStatus.OK.code()).end(body.encode());
      }
    });
  }

  private DeliveryOptions owHeaders(JsonObject actionInput) {
    // Wrap OW variables into eventBus headers
    MultiMap owVariables = MultiMap.caseInsensitiveMultiMap();
    for (String field : new String[]{"api_key", "namespace", "action_name", "activation_id",
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