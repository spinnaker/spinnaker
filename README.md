Echo
====

Echo is an event history service for Spinnaker.

Echo is responsible for receiving and routing events to other services.

Running Echo
------------

Echo is a Spring Boot application backed by Gradle. To launch this on your local machine, call `./gradlew bootRun`

To run on a server, call `./gradlew build` and execute the jar file generated in the build/libs directory.

Sending Events to Echo
----------------------

The echo-core module defines a endpoint at the root level and expects an application/json request body:

#### POST /

```
    {
        "details": {
            "source" : "myapp",
            "type" : "build"
        },
        "content": {
            "build-date": "2014-08-26",
            "project-type": "jenkins"
        }
    }
```

The details block of the event definition expects a source ( the application sending the event ) and an event type.

The content block contains an arbitrary map of attributes.

The search module in echo is able to search within all the content blocks of one particular event type.

Event Propagation in Echo
-------------------------

When an event is received by Echo, it will forward the event to beans within the application context that implement the EchoEventListener interface.

You can extend the functionality of echo-core by providing your own event listeners and adding them to the application context.

The best example in this project is the echo-elasticsearch module, which routes echo events into an elastic search / jest backed web service and provides endpoints to query events.

There is also a basic cassandra-backed example in the echo-cassandra module.

Search for events in Echo
-------------------------

For most basic operations, there is a provided event search endpoint:

#### 1. GET /search/events/{startDate}

The start date format expected is of the form `new Date().time` in Groovy.

This end point accepts a few optional request parameters, to trigger these, add them as query strings

For example, to specify an end date, call /search/events/0?end=1409081771116

The following optional parameters are enabled:

| Query Parameter | Description | Type | Default | Example Value |
|-----------------|----------------------------------------------------------------------|------------------------------------|-------------|---------------|
| end | specifies an end date | Date in the format new Date().time | none | 1409081771116 |
| source | event source, when specified, returns only events from that source | String | all sources | igor |
| type | event type, when specified, returns only types that match the type | String | all events | build |
| full | if true, returns the entire content block for the event.                                                         | Boolean | false | true |
|     | If not found, only returns a few details for the event.                                                          |        |      |     |
| from | used for pagination, specifies the first index of elements to return | Number | 0 | 10 |
| size | number of records to return | Number | 0 | 50 |

#### 2. GET /search/get/{contentId}

This endpoint allows you to get full details for a particular content id. The contentId corresponds to the id returned in the non-full version of the event list.

#### 3. POST /search/direct/{source}/{type}

This endpoint allows you to issue a direct elastic search query for events that match the source and type.

For example, if the event issued is of source kato and type shrink_asg, we can issue a command to get all the items that belong to application spinnaker by calling

/search/direct/kato/shrink_asg
```
{
    "query" : {
        "term" : { "application": "spinnaker" }
    }
}
```

The format is the same as the [query DSL in elastic search](http://www.elasticsearch.org/guide/en/elasticsearch/reference/current/query-dsl.html)

#### 4. POST /search/direct/metadata

This endpoint allows you to search within the details block of all events. The query format is the same as above.
