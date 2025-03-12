package com.netflix.spinnaker.keel.serialization;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.datatype.jsr310.ser.InstantSerializerBase;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

/**
 * This class overloads the default Serialization instance in the JavaTimeModule to a precision
 * level that the database can process via str_to_date string conversion It's needed because we do
 * JSON serialization to a column and then the column gets used to generate another field via string
 * conversion. Specifically * triggered_at * dismissed_at are "generated" columns. NOTE this should
 * ONLY impact running on Linux as the system clock on Linux systems returns a higher precision in
 * Java 17+. It's also impactful as ISO_INSTANT has NO precision limitation giving inconsistent
 * string output as a result
 *
 * <p>See
 *
 * <ul>
 *   <li>https://stackoverflow.com/questions/74781495/changes-in-instant-now-between-java-11-and-java-17-in-aws-ubuntu-standard-6-0
 *   <li>https://stackoverflow.com/a/38042457 for more information.
 * </ul>
 *
 * <p>NOTE we're going to 6 digits to match <a
 * href="https://www.w3schools.com/sql/func_mysql_str_to_date.asp">Mysql str_to_date function
 * limits</a> on microseconds parsing. This is used in the SQL definition: <code>
 *  add column triggered_at datetime(3) generated always as (str_to_date(json->>'$.triggeredAt', '%Y-%m-%dT%T.%fZ'))
 * </code> column in the 20210616-create-dismissible-notifications.yml liquibase change set.
 */
public class PrecisionSqlSerializer extends InstantSerializerBase<Instant> {
  public PrecisionSqlSerializer() {
    super(
        Instant.class,
        Instant::toEpochMilli,
        Instant::getEpochSecond,
        Instant::getNano,
        new DateTimeFormatterBuilder().appendInstant(6).toFormatter());
  }

  @Override
  protected InstantSerializerBase<?> withFormat(
      Boolean aBoolean, DateTimeFormatter dateTimeFormatter, JsonFormat.Shape shape) {
    return this;
  }
}
