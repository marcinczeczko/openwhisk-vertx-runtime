package io.marcinczeczko.whisk.vertx.action;

import static io.marcinczeczko.whisk.vertx.action.VertxProxy.ACTION_DEPLOYED_FLAG;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;

public class InitHandler extends ProxyHandler {

  private final Vertx vertx;

  public InitHandler(Vertx vertx) {
    this.vertx = vertx;
  }

  @Override
  public void handle(RoutingContext ctx) {
    if (isActionDeployed(ctx)) {
      String errorMessage = "Cannot initialize the action more than once.";
      System.err.println(errorMessage);
      signalError(ctx, errorMessage);
      return;
    }

    if (ctx.getBody().length() > 0 && ctx.getBodyAsJson().containsKey("value")) {
      JsonObject message = ctx.getBodyAsJson().getJsonObject("value");

      String verticle = message.getString("main");
      byte[] jar = message.getBinary("code");

      saveJar(jar).setHandler(ar -> {
        if (ar.failed()) {
          signalError(ctx, ar.cause());
        } else {
          Path jarPath = ar.result();
          if (!jarPath.toFile().exists()) {
            signalError(ctx, String.format("%s file does not exist", jarPath.toAbsolutePath()));
          }

          // Jar file saved, try to deploy the verticle
          DeploymentOptions options = new DeploymentOptions()
              .setIsolationGroup("vertx_action_group")
              .setExtraClasspath(Collections.singletonList(jarPath.toAbsolutePath().toString()));

          vertx.deployVerticle(verticle, options, deployed -> {
            if (deployed.failed()) {
              signalError(ctx, deployed.cause());
            } else {
              ctx.put(ACTION_DEPLOYED_FLAG, true);
              signalSuccess(ctx);
            }
          });
        }
      });
    } else {
      signalError(ctx, "Missing main/no code to execute.");
    }
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
}
