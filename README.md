# OpenWhisk runtimes for Vert.x 3.5

## Running example actions

### Example Vert.x action
Build example action
```
./gradlew examples:action:jar
```

Create action
```
wsk action update exampleAction examples/action/build/libs/action.jar --main example.SampleVertxAction --docker ${user_prefix}/vertx35action
```

Run the action
```
wsk action invoke --result exampleAction --param name World
```

```json
  {
      "greeting": "Hello World from Vert.x!"
  }
```

### Example Web Vert.x Action
```
./gradlew examples:webAction:jar
```

Create action
```
wsk action update exampleWebAction examples/webAction/build/libs/webAction.jar --main example.SampleVertxWebAction --docker ${user_prefix}/vertx35action --web true
```

Get the URL of the web action
```
wsk action get exampleWebAction --url 
```

```
ok: got action exampleWebAction
http://<WSK_URL>/api/v1/web/guest/default/exampleWebAction
```

Set a request for an action
```
curl -i http://<WSK_URL>/api/v1/web/guest/default/exampleWebAction
```

Result
```
HTTP/1.1 200 OK
X-Request-ID: Wm2qsq15gwz8WXFhM4blprPv5iVFMTd5
Access-Control-Allow-Origin: *
Access-Control-Allow-Methods: OPTIONS, GET, DELETE, POST, PUT, HEAD, PATCH
Access-Control-Allow-Headers: Authorization, Content-Type
Set-Cookie: UserID=Jane; Max-Age=3600; Version=
Set-Cookie: SessionID=asdfgh123456; Path=/
Server: akka-http/10.1.1
Date: Mon, 16 Jul 2018 09:23:43 GMT
Content-Type: text/html; charset=UTF-8
Content-Length: 40

<html><body><h3>hello</h3></body></html>%
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
