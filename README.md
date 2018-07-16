# OpenWhisk runtimes for Vert.x 3.5

## Quick Vertx Action
A Vertx action is a Java program that extends `AbstractVerticle` class of Vert.x and has the method `start` has the the structure as follows:
```java
import io.vertx.core.*;

public class SampleVertxAction extends AbstractVerticle {
  public void start() {
      vertx.eventBus().<JsonObject>consumer("actionInvoke", message -> {
        //process message and send reply to the message
      });
  }
}
```

For example, create a Java file called `HelloVertx.java` with the following content:

```java
import io.vertx.core.*;

public class HelloVertx extends AbstractVerticle {
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
```
In order to compile, test and archive Java files, you must have a [JDK 8](http://openjdk.java.net/install/) installed locally.

Then, compile `HelloVertx.java` into a JAR file `hello.jar` as follows:
```
javac HelloVertx.java
```
```
jar cvf hello.jar HelloVertx.class
```

**Note:** [vertx](https://github.com/eclipse/vert.x) must exist in your Java CLASSPATH when compiling the Java file.

### Create the Vertx Action
To use as a docker action:
```
wsk action update helloVertx hello.jar --main HelloVertx --docker openwhisk/vertx35action
```
This works on any deployment of Apache OpenWhisk

You need to specify the name of the main Verticle class using `--main`. An eligible main
class is one that extends `AbstractVerticle` and has `start` method as described above. If the
class is not in the default package, use the Java fully-qualified class name,
e.g., `--main com.example.MyVerticle`.

### Invoke the Vertx Action
Action invocation is the same for Vertx actions as it is for Java, Swift and JavaScript actions:

```
wsk action invoke --result helloVertx --param name World
```

```json
  {
      "greeting": "Hello World from Vert.x!"
  }
```

## Local development
```
./gradlew core:vertx35:distDocker
```
This will produce the image `whisk/vertx35action`

Build and Push image
```
docker login
./gradlew core:vertx35:distDocker -PdockerImagePrefix=${prefix-user} -PdockerRegistry=docker.io
```

### Testing
Install dependencies from the root directory on $OPENWHISK_HOME repository
```
pushd $OPENWHISK_HOME
./gradlew install
podd $OPENWHISK_HOME
```

Using gradle to run all tests
```
./gradlew :tests:test
```
Using gradle to run some tests
```
./gradlew :tests:test --tests *ActionContainerTests*
```
Using IntelliJ:
- Import project as gradle project.
- Make sure working directory is root of the project/repo

#### Using container image to test
To use as docker action push to your own dockerhub account
```
docker tag whisk/vertx35action ${user_prefix}/vertx35action
docker push ${user_prefix}/vertx35action
```
Then create the action using your the image from dockerhub
```
wsk action update helloVertx hello.jar --main HelloVertx --docker ${user_prefix}/vertx35action
```
The `${user_prefix}` is usually your dockerhub user id.

# License
[Apache 2.0](LICENSE.txt)
