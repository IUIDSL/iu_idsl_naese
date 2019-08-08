# `NAESE`: Native American Ethnobotany Semantic Explorer

<img height="80" src="naese_war/src/main/webapp/images/banner.jpg">

 * Data Semantics project by Stefan Furrer and Jeremy Yang.
 * Built with RDF, Sparql, Jena.

## Dependencies

* Java 1.8
* Maven 3.5+
* Jena and other open source libraries.
 
## Compiling

```
mvn clean install
```

## Testing with Jetty

<http://localhost:8081/naese/naese>

```
mvn --projects naese_war jetty:run
```

## Deploying `NAESE`

```
mvn --projects naese_war tomcat7:deploy
```

or

```
mvn --projects naese_war tomcat7:redeploy
```

## Docker

See scripts for building and deploying.
See <https://hub.docker.com/r/jeremyjyang/naese>.

## Architecture

<img height="400" src="naese_war/src/main/webapp/images/NAESE_architecture.jpg">
