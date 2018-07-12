package me.czeczek.openwhisk;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;

public class MyVerticle extends AbstractVerticle {

    @Override
    public void start() {
        vertx.eventBus().<JsonObject>consumer("actionInvoke", message -> {
            JsonObject input = message.body();
            String name = input.getString("name", "stranger");
            message.reply(new JsonObject()
                .put("echo", message.body())
                .put("greeting", "welcome to Vert.x, " + name));
        });
    }
}