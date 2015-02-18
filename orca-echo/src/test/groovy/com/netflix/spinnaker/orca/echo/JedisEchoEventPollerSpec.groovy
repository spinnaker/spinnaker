package com.netflix.spinnaker.orca.echo

import java.time.Clock
import java.time.Instant
import java.time.format.DateTimeFormatter
import redis.clients.jedis.JedisCommands
import retrofit.client.Header
import retrofit.client.Response
import retrofit.mime.TypedString
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import static com.netflix.spinnaker.orca.echo.JedisEchoEventPoller.LAST_CHECK_KEY
import static java.time.temporal.ChronoField.MILLI_OF_SECOND

class JedisEchoEventPollerSpec extends Specification {

  @Shared random = new Random()

  def jedis = Mock(JedisCommands)
  def echoService = Mock(EchoService)
  @Subject eventPoller = new JedisEchoEventPoller(jedis, echoService)

  @Shared formatter = DateTimeFormatter.RFC_1123_DATE_TIME
  @Shared responseTimestamp = Instant.now()
                                     .with(MILLI_OF_SECOND, 0)
                                     .atZone(Clock.systemUTC().zone)
  @Shared response = new Response(
    "http://echo",
    200, "OK",
    [new Header("Date", formatter.format(responseTimestamp))],
    new TypedString("")
  )

  def "retrieves events since epoch if no last check timestamp is found"() {
    given:
    jedis.get(LAST_CHECK_KEY) >> null

    when:
    eventPoller.getEvents(eventType)

    then:
    1 * echoService.getEvents(eventType, 0L) >> response

    where:
    eventType = "foo"
  }

  def "retrieves events since the last check timestamp if it exists"() {
    given:
    jedis.get(LAST_CHECK_KEY) >> timestamp.toString()

    when:
    eventPoller.getEvents(eventType)

    then:
    1 * echoService.getEvents(eventType, timestamp) >> response

    where:
    eventType = "foo"
    timestamp = random.nextLong()
  }

  def "stores the timestamp from the response"() {
    given:
    echoService.getEvents(*_) >> response
    jedis.get(LAST_CHECK_KEY) >> null

    when:
    eventPoller.getEvents(eventType)

    then:
    1 * jedis.set(LAST_CHECK_KEY, expected)

    where:
    eventType = "foo"
    expected = responseTimestamp.toInstant()
                                .toEpochMilli()
                                .toString()
  }
}
