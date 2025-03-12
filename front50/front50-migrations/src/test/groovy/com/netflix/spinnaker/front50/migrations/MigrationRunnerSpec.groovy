package com.netflix.spinnaker.front50.migrations

import com.netflix.spinnaker.front50.config.MigrationConfigTest
import org.spockframework.spring.SpringBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.ContextConfiguration
import spock.lang.Specification
import spock.lang.Subject

@ContextConfiguration(classes = [MigrationConfigTest, MigrationRunner])
class MigrationRunnerSpec extends Specification {
  @SpringBean(name = "myFirstMigration")
  Migration myFirstMigration = Mock()

  @SpringBean(name = "mySecondMigration")
  Migration mySecondMigration = Mock()

  @SpringBean(name = "disabledMigration")
  Migration disabledMigration = Mock()

  @Subject
  @Autowired
  MigrationRunner migrationRunner

  def setup() {
    myFirstMigration.isValid() >> true
    mySecondMigration.isValid() >> true
    disabledMigration.isValid() >> false
  }

  def "should run all enabled migrations"() {
    when:
    migrationRunner.run()

    then:
    1 * myFirstMigration.run()
    1 * mySecondMigration.run()
    0 * disabledMigration.run()
  }
}
