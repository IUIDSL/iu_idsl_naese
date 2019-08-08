# `NAESE`: Native American Ethnobotany Semantic Explorer

<img height="80" src="naese_war/src/main/webapp/images/banner.jpg">

 * Data Semantics project by Stefan Furrer and Jeremy Yang.
 * Built with RDF, Sparql, Jena.
 * Project for Data Semantics class at Indiana University taught by Professor Ying Ding in 2014. 
 * Webapp for exploration of the [NAE database](http://naeb.brit.org/) (2014 version).

## Dependencies

* Java 1.8
* Maven 3.5+
* Jena.
* [`iu_idsl_util`](https://github.com/IUIDSL/iu_idsl_util), [`iu_idsl_jena`](https://github.com/IUIDSL/iu_idsl_jena)
 
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

---

<img height="400" src="naese_war/src/main/webapp/images/NAESE_architecture.jpg">
