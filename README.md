# OpenWhisk runtimes for vertx

## Quick Vertx Action
TBD

### Create the Java Action
TBD

### Invoke the Java Action
TBD

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
TBD

#### Using container image to test
To use as docker action push to your own dockerhub account
```
docker tag whisk/vertx35action ${user_prefix}/vertx35action
docker push ${user_prefix}/vertx35action
```
Then create the action using your the image from dockerhub
```
wsk action update helloJava hello.jar --main Hello --docker ${user_prefix}/vertx35action
```
The `${user_prefix}` is usually your dockerhub user id.


# License
[Apache 2.0](LICENSE.txt)
