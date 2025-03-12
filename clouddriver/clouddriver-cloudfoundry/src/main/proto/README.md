Sourced from https://github.com/cloudfoundry/dropsonde-protocol

# Protocol Documentation
<a name="top"/>

## Table of Contents
* [envelope.proto](#envelope.proto)
 * [Envelope](#events.Envelope)
 * [Envelope.TagsEntry](#events.Envelope.TagsEntry)
 * [Envelope.EventType](#events.Envelope.EventType)
* [error.proto](#error.proto)
 * [Error](#events.Error)
* [http.proto](#http.proto)
 * [HttpStartStop](#events.HttpStartStop)
 * [Method](#events.Method)
 * [PeerType](#events.PeerType)
* [log.proto](#log.proto)
 * [LogMessage](#events.LogMessage)
 * [LogMessage.MessageType](#events.LogMessage.MessageType)
* [metric.proto](#metric.proto)
 * [ContainerMetric](#events.ContainerMetric)
 * [CounterEvent](#events.CounterEvent)
 * [ValueMetric](#events.ValueMetric)
* [uuid.proto](#uuid.proto)
 * [UUID](#events.UUID)
* [Scalar Value Types](#scalar-value-types)

<a name="envelope.proto"/>
<p align="right"><a href="#top">Top</a></p>

## envelope.proto


### Envelope
<a name="events.Envelope"/>
Envelope wraps an Event and adds metadata.

| Field | Type | Label | Description |
| ----- | ---- | ----- | ----------- |
| origin | [string](#string) | required | Unique description of the origin of this event. |
| eventType | [Envelope.EventType](#events.Envelope.EventType) | required | Type of wrapped event. Only the optional field corresponding to the value of eventType should be set. |
| timestamp | [int64](#int64) | optional | UNIX timestamp (in nanoseconds) event was wrapped in this Envelope. |
| deployment | [string](#string) | optional | Deployment name (used to uniquely identify source). |
| job | [string](#string) | optional | Job name (used to uniquely identify source). |
| index | [string](#string) | optional | Index of job (used to uniquely identify source). |
| ip | [string](#string) | optional | IP address (used to uniquely identify source). |
| tags | [Envelope.TagsEntry](#events.Envelope.TagsEntry) | repeated | key/value tags to include additional identifying information. |
| httpStartStop | [HttpStartStop](#events.HttpStartStop) | optional |  |
| logMessage | [LogMessage](#events.LogMessage) | optional |  |
| valueMetric | [ValueMetric](#events.ValueMetric) | optional |  |
| counterEvent | [CounterEvent](#events.CounterEvent) | optional |  |
| error | [Error](#events.Error) | optional |  |
| containerMetric | [ContainerMetric](#events.ContainerMetric) | optional |  |


### Envelope.TagsEntry
<a name="events.Envelope.TagsEntry"/>

| Field | Type | Label | Description |
| ----- | ---- | ----- | ----------- |
| key | [string](#string) | optional |  |
| value | [string](#string) | optional |  |



### Envelope.EventType
<a name="events.Envelope.EventType"/>
Type of the wrapped event.

| Name | Number | Description |
| ---- | ------ | ----------- |
| HttpStartStop | 4 |  |
| LogMessage | 5 |  |
| ValueMetric | 6 |  |
| CounterEvent | 7 |  |
| Error | 8 |  |
| ContainerMetric | 9 |  |


<p align="right"><a href="#top">Top</a></p>

## error.proto
<a name="error.proto"/>



### Error
<a name="events.Error"/>
An Error event represents an error in the originating process.

| Field | Type | Label | Description |
| ----- | ---- | ----- | ----------- |
| source | [string](#string) | required | Source of the error. This may or may not be the same as the Origin in the envelope. |
| code | [int32](#int32) | required | Numeric error code. This is provided for programmatic responses to the error. |
| message | [string](#string) | required | Error description (preferably human-readable). |


<p align="right"><a href="#top">Top</a></p>

## http.proto
<a name="http.proto"/>



### HttpStartStop
<a name="events.HttpStartStop"/>
An HttpStartStop event represents the whole lifecycle of an HTTP request.

| Field | Type | Label | Description |
| ----- | ---- | ----- | ----------- |
| startTimestamp | [int64](#int64) | required | UNIX timestamp (in nanoseconds) when the request was sent (by a client) or received (by a server). |
| stopTimestamp | [int64](#int64) | required | UNIX timestamp (in nanoseconds) when the request was received. |
| requestId | [UUID](#events.UUID) | required | ID for tracking lifecycle of request. |
| peerType | [PeerType](#events.PeerType) | required | Role of the emitting process in the request cycle. |
| method | [Method](#events.Method) | required | Method of the request. |
| uri | [string](#string) | required | Destination of the request. |
| remoteAddress | [string](#string) | required | Remote address of the request. (For a server, this should be the origin of the request.) |
| userAgent | [string](#string) | required | Contents of the UserAgent header on the request. |
| statusCode | [int32](#int32) | required | Status code returned with the response to the request. |
| contentLength | [int64](#int64) | required | Length of response (bytes). |
| applicationId | [UUID](#events.UUID) | optional | If this request was made in relation to an appliciation, this field should track that application's ID. |
| instanceIndex | [int32](#int32) | optional | Index of the application instance. |
| instanceId | [string](#string) | optional | ID of the application instance. |
| forwarded | [string](#string) | repeated | This contains http forwarded-for [x-forwarded-for] header from the request. |



### Method
<a name="events.Method"/>
HTTP method.

| Name | Number | Description |
| ---- | ------ | ----------- |
| GET | 1 |  |
| POST | 2 |  |
| PUT | 3 |  |
| DELETE | 4 |  |
| HEAD | 5 |  |
| ACL | 6 |  |
| BASELINE_CONTROL | 7 |  |
| BIND | 8 |  |
| CHECKIN | 9 |  |
| CHECKOUT | 10 |  |
| CONNECT | 11 |  |
| COPY | 12 |  |
| DEBUG | 13 |  |
| LABEL | 14 |  |
| LINK | 15 |  |
| LOCK | 16 |  |
| MERGE | 17 |  |
| MKACTIVITY | 18 |  |
| MKCALENDAR | 19 |  |
| MKCOL | 20 |  |
| MKREDIRECTREF | 21 |  |
| MKWORKSPACE | 22 |  |
| MOVE | 23 |  |
| OPTIONS | 24 |  |
| ORDERPATCH | 25 |  |
| PATCH | 26 |  |
| PRI | 27 |  |
| PROPFIND | 28 |  |
| PROPPATCH | 29 |  |
| REBIND | 30 |  |
| REPORT | 31 |  |
| SEARCH | 32 |  |
| SHOWMETHOD | 33 |  |
| SPACEJUMP | 34 |  |
| TEXTSEARCH | 35 |  |
| TRACE | 36 |  |
| TRACK | 37 |  |
| UNBIND | 38 |  |
| UNCHECKOUT | 39 |  |
| UNLINK | 40 |  |
| UNLOCK | 41 |  |
| UPDATE | 42 |  |
| UPDATEREDIRECTREF | 43 |  |
| VERSION_CONTROL | 44 |  |

### PeerType
<a name="events.PeerType"/>
Type of peer handling request.

| Name | Number | Description |
| ---- | ------ | ----------- |
| Client | 1 | Request is made by this process. |
| Server | 2 | Request is received by this process. |




<p align="right"><a href="#top">Top</a></p>

## log.proto
<a name="log.proto"/>



### LogMessage
<a name="events.LogMessage"/>
A LogMessage contains a &quot;log line&quot; and associated metadata.

| Field | Type | Label | Description |
| ----- | ---- | ----- | ----------- |
| message | [bytes](#bytes) | required | Bytes of the log message. (Note that it is not required to be a single line.) |
| message_type | [LogMessage.MessageType](#events.LogMessage.MessageType) | required | Type of the message (OUT or ERR). |
| timestamp | [int64](#int64) | required | UNIX timestamp (in nanoseconds) when the log was written. |
| app_id | [string](#string) | optional | Application that emitted the message (or to which the application is related). |
| source_type | [string](#string) | optional | Source of the message. For Cloud Foundry, this can be &quot;APP&quot;, &quot;RTR&quot;, &quot;DEA&quot;, &quot;STG&quot;, etc. |
| source_instance | [string](#string) | optional | Instance that emitted the message. |



### LogMessage.MessageType
<a name="events.LogMessage.MessageType"/>
MessageType stores the destination of the message (corresponding to STDOUT or STDERR).

| Name | Number | Description |
| ---- | ------ | ----------- |
| OUT | 1 |  |
| ERR | 2 |  |




<p align="right"><a href="#top">Top</a></p>

## metric.proto
<a name="metric.proto"/>



### ContainerMetric
<a name="events.ContainerMetric"/>
A ContainerMetric records resource usage of an app in a container.

| Field | Type | Label | Description |
| ----- | ---- | ----- | ----------- |
| applicationId | [string](#string) | required | ID of the contained application. |
| instanceIndex | [int32](#int32) | required | Instance index of the contained application. (This, with applicationId, should uniquely identify a container.) |
| cpuPercentage | [double](#double) | required | CPU based on number of cores. |
| memoryBytes | [uint64](#uint64) | required | Bytes of memory used. |
| diskBytes | [uint64](#uint64) | required | Bytes of disk used. |
| memoryBytesQuota | [uint64](#uint64) | optional | Maximum bytes of memory allocated to container. |
| diskBytesQuota | [uint64](#uint64) | optional | Maximum bytes of disk allocated to container. |


### CounterEvent
<a name="events.CounterEvent"/>
A CounterEvent represents the increment of a counter. It contains only the change in the value; it is the responsibility of downstream consumers to maintain the value of the counter.

| Field | Type | Label | Description |
| ----- | ---- | ----- | ----------- |
| name | [string](#string) | required | Name of the counter. Must be consistent for downstream consumers to associate events semantically. |
| delta | [uint64](#uint64) | required | Amount by which to increment the counter. |
| total | [uint64](#uint64) | optional | Total value of the counter. This will be overridden by Metron, which internally tracks the total of each named Counter it receives. |


### ValueMetric
<a name="events.ValueMetric"/>
A ValueMetric indicates the value of a metric at an instant in time.

| Field | Type | Label | Description |
| ----- | ---- | ----- | ----------- |
| name | [string](#string) | required | Name of the metric. Must be consistent for downstream consumers to associate events semantically. |
| value | [double](#double) | required | Value at the time of event emission. |
| unit | [string](#string) | required | Unit of the metric. Please see http://metrics20.org/spec/#units for ideas; SI units/prefixes are recommended where applicable. Should be consistent for the life of the metric (consumers are expected to report, but not interpret, prefixes). |






<p align="right"><a href="#top">Top</a></p>

## uuid.proto
<a name="uuid.proto"/>



### UUID
<a name="events.UUID"/>
Type representing a 128-bit UUID.

The bytes of the UUID should be packed in little-endian **byte** (not bit) order. For example, the UUID `f47ac10b-58cc-4372-a567-0e02b2c3d479` should be encoded as `UUID{ low: 0x7243cc580bc17af4, high: 0x79d4c3b2020e67a5 }`

| Field | Type | Label | Description |
| ----- | ---- | ----- | ----------- |
| low | [uint64](#uint64) | required |  |
| high | [uint64](#uint64) | required |  |







## Scalar Value Types
<a name="scalar-value-types"/>

| .proto Type | Notes | C++ Type | Java Type | Python Type |
| ----------- | ----- | -------- | --------- | ----------- |
| <a name="double"/> double |  | double | double | float |
| <a name="float"/> float |  | float | float | float |
| <a name="int32"/> int32 | Uses variable-length encoding. Inefficient for encoding negative numbers – if your field is likely to have negative values, use sint32 instead. | int32 | int | int |
| <a name="int64"/> int64 | Uses variable-length encoding. Inefficient for encoding negative numbers – if your field is likely to have negative values, use sint64 instead. | int64 | long | int/long |
| <a name="uint32"/> uint32 | Uses variable-length encoding. | uint32 | int | int/long |
| <a name="uint64"/> uint64 | Uses variable-length encoding. | uint64 | long | int/long |
| <a name="sint32"/> sint32 | Uses variable-length encoding. Signed int value. These more efficiently encode negative numbers than regular int32s. | int32 | int | int |
| <a name="sint64"/> sint64 | Uses variable-length encoding. Signed int value. These more efficiently encode negative numbers than regular int64s. | int64 | long | int/long |
| <a name="fixed32"/> fixed32 | Always four bytes. More efficient than uint32 if values are often greater than 2^28. | uint32 | int | int |
| <a name="fixed64"/> fixed64 | Always eight bytes. More efficient than uint64 if values are often greater than 2^56. | uint64 | long | int/long |
| <a name="sfixed32"/> sfixed32 | Always four bytes. | int32 | int | int |
| <a name="sfixed64"/> sfixed64 | Always eight bytes. | int64 | long | int/long |
| <a name="bool"/> bool |  | bool | boolean | boolean |
| <a name="string"/> string | A string must always contain UTF-8 encoded or 7-bit ASCII text. | string | String | str/unicode |
| <a name="bytes"/> bytes | May contain any arbitrary sequence of bytes. | string | ByteString | str |

<p align="right"><a href="#top">Top</a></p>
