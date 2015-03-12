package com.netflix.spinnaker.orca.echo

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.stereotype.Component
import redis.clients.jedis.JedisCommands
import retrofit.client.Response
import static com.google.common.net.HttpHeaders.DATE
import static java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME
import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME

/**
 * Fetches events from Echo and caches the time of the last check in Redis.
 */
@Component
@ConditionalOnBean(JedisCommands)
@Slf4j
@CompileStatic
class JedisEchoEventPoller implements EchoEventPoller {

  public static final String LAST_CHECK_KEY = "echo:event:last-check"

  private final JedisCommands jedis
  private final EchoService echoService

  @Autowired
  JedisEchoEventPoller(JedisCommands jedis, EchoService echoService) {
    this.jedis = jedis
    this.echoService = echoService
  }

  @Override
  Response getEvents(String type) {
    def lastCheck = jedis.get(LAST_CHECK_KEY)?.toLong()
    log.info "Looking for echo events of type $type since ${asLocalTime(lastCheck)}"
    def response = echoService.getEvents(type, lastCheck ?: 0L)
    jedis.set LAST_CHECK_KEY, dateHeaderFrom(response).toString()
    return response
  }

  private static Long dateHeaderFrom(Response response) {
    def header = response.headers.find { it.name == DATE }
    log.info "Got echo response generated at $header.value"
    ZonedDateTime.from(RFC_1123_DATE_TIME.parse(header.value))
                 .toInstant()
                 .toEpochMilli()
  }

  public static final ZoneId TZ = ZoneId.of("America/Los_Angeles")

  private String asLocalTime(Long lastCheck) {
    if (lastCheck) {
      Instant.ofEpochMilli(lastCheck)
             .atZone(TZ)
             .format(ISO_OFFSET_DATE_TIME)
    } else {
      "like, whenever"
    }
  }
}
