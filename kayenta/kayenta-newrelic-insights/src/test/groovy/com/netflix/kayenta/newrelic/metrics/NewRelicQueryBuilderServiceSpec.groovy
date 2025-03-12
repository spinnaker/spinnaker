package com.netflix.kayenta.newrelic.metrics


import com.netflix.kayenta.canary.CanaryConfig
import com.netflix.kayenta.canary.CanaryMetricConfig
import com.netflix.kayenta.canary.providers.metrics.NewRelicCanaryMetricSetQueryConfig
import com.netflix.kayenta.newrelic.canary.NewRelicCanaryScope
import com.netflix.kayenta.newrelic.canary.NewRelicCanaryScopeFactory
import com.netflix.kayenta.newrelic.config.NewRelicScopeConfiguration
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Instant
import java.time.temporal.ChronoUnit

class NewRelicQueryBuilderServiceSpec extends Specification {

  @Shared
  NewRelicQueryBuilderService queryBuilder = new NewRelicQueryBuilderService()

  @Shared
  Instant start = Instant.now()

  @Shared
  Instant end = start.plus(60, ChronoUnit.MINUTES)

  @Shared
  Map<String, String> templates = [:] // Not sure if this is relevant to New Relic not adding tests for this as of now.

  @Shared
  def testCases = [
    [
      description  : "select is supplied but the word SELECT is omitted",
      select       : "count(*) FROM Transaction",
      expectedQuery:
        "SELECT count(*) " +
          "FROM Transaction " +
          "TIMESERIES 60 seconds " +
          "SINCE ${start.epochSecond} UNTIL ${end.epochSecond} " +
          "WHERE asg_name LIKE 'myservice-prod-v01' " +
          "AND region LIKE 'us-west-2'"
    ],
    [
      description  : "select is supplied",
      select       : "SELECT count(*) FROM Transaction",
      expectedQuery:
        "SELECT count(*) " +
          "FROM Transaction " +
          "TIMESERIES 60 seconds " +
          "SINCE ${start.epochSecond} UNTIL ${end.epochSecond} " +
          "WHERE asg_name LIKE 'myservice-prod-v01' " +
          "AND region LIKE 'us-west-2'"
    ],
    [
      description  : "select and q are supplied",
      select       : "SELECT count(*) FROM Transaction",
      q            : "httpStatusCode LIKE '5%'",
      expectedQuery:
        "SELECT count(*) " +
          "FROM Transaction " +
          "TIMESERIES 60 seconds " +
          "SINCE ${start.epochSecond} UNTIL ${end.epochSecond} " +
          "WHERE httpStatusCode LIKE '5%' " +
          "AND asg_name LIKE 'myservice-prod-v01' " +
          "AND region LIKE 'us-west-2'"
    ],
    [
      description  : "scope step is set to 0",
      select       : "SELECT count(*) FROM Transaction",
      step: 0,
      expectedQuery:
        "SELECT count(*) " +
          "FROM Transaction " +
          "TIMESERIES MAX " +
          "SINCE ${start.epochSecond} UNTIL ${end.epochSecond} " +
          "WHERE asg_name LIKE 'myservice-prod-v01' " +
          "AND region LIKE 'us-west-2'"
    ],
    [
      description  : "extended scope parameters are supplied",
      select       : "SELECT count(*) FROM Transaction",
      extraExtendedScopeParameters: [
        '_scope_key': 'foo',
        "_location_key": 'bar',
        '_i_should_be_ignored': "some value that should be ignored",
        'some_new_key': 'some_value'
      ],
      expectedQuery:
        "SELECT count(*) " +
          "FROM Transaction " +
          "TIMESERIES 60 seconds " +
          "SINCE ${start.epochSecond} UNTIL ${end.epochSecond} " +
          "WHERE some_new_key LIKE 'some_value' " +
          "AND foo LIKE 'myservice-prod-v01' " +
          "AND bar LIKE 'us-west-2'"
    ],
    [
      description         : "an inline template is supplied",
      customInlineTemplate:
        "SELECT count(*) " +
          "FROM Transaction " +
          "TIMESERIES 60 seconds " +
          "SINCE \${startEpochSeconds} UNTIL \${endEpochSeconds} " +
          "WHERE httpStatusCode LIKE '5%' " +
          "AND autoScalingGroupName LIKE '\${scope}' " +
          "AND region LIKE '\${location}'",
      expectedQuery       :
        "SELECT count(*) " +
          "FROM Transaction " +
          "TIMESERIES 60 seconds " +
          "SINCE ${start.epochSecond} UNTIL ${end.epochSecond} " +
          "WHERE httpStatusCode LIKE '5%' " +
          "AND autoScalingGroupName LIKE 'myservice-prod-v01' " +
          "AND region LIKE 'us-west-2'"
    ],
    [
      description: "an inline template and extended scope params are supplied",
      extraExtendedScopeParameters: [
        someKey: 'someValue'
      ],
      customInlineTemplate:
        "SELECT count(*) " +
          "FROM Transaction " +
          "TIMESERIES 60 seconds " +
          "SINCE \${startEpochSeconds} UNTIL \${endEpochSeconds} " +
          "WHERE httpStatusCode LIKE '5%' " +
          "AND someKeyThatIsSetDuringDeployment LIKE '\${someKey}' " +
          "AND autoScalingGroupName LIKE '\${scope}' " +
          "AND region LIKE '\${location}'",
      expectedQuery       :
        "SELECT count(*) " +
          "FROM Transaction " +
          "TIMESERIES 60 seconds " +
          "SINCE ${start.epochSecond} UNTIL ${end.epochSecond} " +
          "WHERE httpStatusCode LIKE '5%' " +
          "AND someKeyThatIsSetDuringDeployment LIKE 'someValue' " +
          "AND autoScalingGroupName LIKE 'myservice-prod-v01' " +
          "AND region LIKE 'us-west-2'"
    ]
  ]

  @Unroll
  void "When #useCase then the NewRelicQueryBuilderService generates the expected query"() {
    given:
    NewRelicCanaryMetricSetQueryConfig queryConfig =
      NewRelicCanaryMetricSetQueryConfig.builder()
        .select(select as String)
        .q(q as String)
        .customFilterTemplate(customFilterTemplate as String)
        .customInlineTemplate(customInlineTemplate as String)
        .build()
    CanaryMetricConfig canaryMetricConfig =
      CanaryMetricConfig.builder()
        .query(queryConfig)
        .build()
    CanaryConfig canaryConfig =
      CanaryConfig.builder()
        .templates(templates)
        .metric(canaryMetricConfig)
        .build()
    NewRelicCanaryScope newRelicCanaryScope = new NewRelicCanaryScope()
    newRelicCanaryScope.setScope('myservice-prod-v01')
    newRelicCanaryScope.setLocation('us-west-2')
    newRelicCanaryScope.setStep(Optional.ofNullable(step as Long).orElse(60))
    newRelicCanaryScope.setStart(start)
    newRelicCanaryScope.setEnd(end)

    NewRelicScopeConfiguration scopeConfiguration = NewRelicScopeConfiguration.builder()
      .defaultScopeKey('asg_name')
      .defaultLocationKey('region')
      .build()

    def extendedScopeParameters = [:] as Map<String, String>

    Optional.ofNullable(extraExtendedScopeParameters).ifPresent({
      extendedScopeParameters.putAll(it as Map<String, String>)
    })


    Optional.ofNullable(scopeKeyOveride).ifPresent({ key ->
      extendedScopeParameters[NewRelicCanaryScopeFactory.SCOPE_KEY_KEY] = key as String
      newRelicCanaryScope.setScopeKey(key as String)
    })

    Optional.ofNullable(locationKeyOverride).ifPresent({ key ->
      extendedScopeParameters[NewRelicCanaryScopeFactory.LOCATION_KEY_KEY] = key as String
      newRelicCanaryScope.setLocationKey(key as String)
    })

    newRelicCanaryScope.setExtendedScopeParams(extendedScopeParameters)

    when:
    String query = queryBuilder.buildQuery(canaryConfig, newRelicCanaryScope, queryConfig, scopeConfiguration)

    then:
    query == expectedQuery

    where:
    [
      useCase,
      expectedQuery,
      select,
      q,
      customFilterTemplate,
      customInlineTemplate,
      step,
      scopeKeyOveride,
      locationKeyOverride,
      extraExtendedScopeParameters
    ] << testCases.collect { testCase ->
      return [
        testCase.description,
        testCase.expectedQuery,
        testCase.select,
        testCase.q,
        testCase.customFilterTemplate,
        testCase.customInlineTemplate,
        testCase.step,
        testCase.scopeKey = testCase?.extraExtendedScopeParameters?."${NewRelicCanaryScopeFactory.SCOPE_KEY_KEY}",
        testCase.locationKey = testCase?.extraExtendedScopeParameters?."${NewRelicCanaryScopeFactory.LOCATION_KEY_KEY}",
        testCase.extraExtendedScopeParameters,
      ]
    }

  }

}
