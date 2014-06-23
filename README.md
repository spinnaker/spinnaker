About
-----

Mayo maintains the current state of application and deployable configuration. It keeps track of the presets, project and environment configuration.

Running Spring Boot
-------------------

* Start application:

  ```gradlew bootRun```

* Run codenarc:

  ```gradlew check```


Working with Cassandra
----------------------

* When running Mayo locally, an embedded cassandra instance is available at localhost:9160. This behavior can be disabled setting the variable `cassandra.embedded = false` in application.yml. This feature is implemented as part of [Kork](https://github.com/spinnaker/kork).

* The project relies heavily on the [Astyanax](https://github.com/Netflix/astyanax) library. 

* For simple endpoints such as presets, Mayo simply stores a compressed JSON blob into Cassandra columns. Look at [Writing Data](https://github.com/Netflix/astyanax/wiki/Writing-data) and [Reading Data](https://github.com/Netflix/astyanax/wiki/Reading-Data) in Astyanax for more details. 

* For more complex endpoints such as project configuration, Mayo stores a hierarchy using [CQL3 and Collection Support](https://github.com/Netflix/astyanax/wiki/Collections). 

* [The Apache Cassandra project](http://cassandra.apache.org/download/) contains a cli tool and a cqlsh file under the bin directory that is useful in testing cql commands. Make sure you download the lastest 1.2 release and not the 2.0.7 release and this is not yet supported within Netflix. 

* You can also use third party tools like the [WSOP2 Cassandra Explorer GUI](https://www.dropbox.com/s/m00uodj1ymkpdzb/wso2carbon-4.0.0-SNAPSHOT.zip) to look at your local deployment. 
