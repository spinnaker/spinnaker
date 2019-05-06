/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.clouddriver.titus.client.model;

import com.netflix.titus.grpc.protogen.ContainerHealthProvider;
import com.netflix.titus.grpc.protogen.Day;
import com.netflix.titus.grpc.protogen.JobDisruptionBudget;
import com.netflix.titus.grpc.protogen.TimeWindow;

public class DisruptionBudgetHelper {

  public static JobDisruptionBudget convertJobDisruptionBudget(DisruptionBudget budget) {
    JobDisruptionBudget.Builder builder = JobDisruptionBudget.newBuilder();
    if (budget.getAvailabilityPercentageLimit() != null) {
      builder.setAvailabilityPercentageLimit(
        JobDisruptionBudget.AvailabilityPercentageLimit.newBuilder().setPercentageOfHealthyContainers(
          budget.availabilityPercentageLimit.getPercentageOfHealthyContainers()
        ).build()
      );
    }
    if (budget.getContainerHealthProviders() != null && !budget.getContainerHealthProviders().isEmpty()) {
      budget.getContainerHealthProviders().forEach(chp ->
        builder.addContainerHealthProviders(ContainerHealthProvider.newBuilder().setName(chp.getName()).build()));
    }

    if (budget.getSelfManaged() != null) {
      builder.setSelfManaged(
        JobDisruptionBudget.SelfManaged.newBuilder().setRelocationTimeMs(
          budget.getSelfManaged().getRelocationTimeMs()
        ).build()
      );
    }

    if (budget.getRatePercentagePerHour() != null) {
      builder.setRatePercentagePerHour(
        JobDisruptionBudget.RatePercentagePerHour.newBuilder().setMaxPercentageOfContainersRelocatedInHour(
          budget.getRatePercentagePerHour().getMaxPercentageOfContainersRelocatedInHour()
        ).build()
      );
    }

    if (budget.getRatePerInterval() != null) {
      builder.setRatePerInterval(
        JobDisruptionBudget.RatePerInterval.newBuilder()
          .setIntervalMs(budget.getRatePerInterval().getIntervalMs())
          .setLimitPerInterval(budget.getRatePerInterval().getLimitPerInterval())
          .build()
      );
    }

    if (budget.getRatePercentagePerInterval() != null) {
      builder.setRatePercentagePerInterval(
        JobDisruptionBudget.RatePercentagePerInterval.newBuilder()
          .setIntervalMs(budget.getRatePercentagePerInterval().getIntervalMs())
          .setPercentageLimitPerInterval(budget.getRatePercentagePerInterval().getPercentageLimitPerInterval())
          .build()
      );
    }

    if (budget.getRelocationLimit() != null) {
      builder.setRelocationLimit(
        JobDisruptionBudget.RelocationLimit.newBuilder().setLimit(budget.getRelocationLimit().getLimit())
      );
    }

    if (budget.getTimeWindows() != null && !budget.getTimeWindows().isEmpty()) {
      budget.getTimeWindows().forEach(tw -> {
        TimeWindow.Builder timeWindowBuilder = TimeWindow.newBuilder();
        tw.getDays().forEach(day -> timeWindowBuilder.addDays(convertDay(day)));
        tw.getHourlyTimeWindows().forEach(htw -> {
          timeWindowBuilder.addHourlyTimeWindows(
            TimeWindow.HourlyTimeWindow.newBuilder().setEndHour(
              htw.getEndHour()).setStartHour(htw.getStartHour()
            ).build()
          );
        });
        timeWindowBuilder.setTimeZone(tw.getTimeZone());
        builder.addTimeWindows(timeWindowBuilder.build());
      });
    }

    if (budget.getUnhealthyTasksLimit() != null) {
      builder.setUnhealthyTasksLimit(
        JobDisruptionBudget.UnhealthyTasksLimit.newBuilder().setLimitOfUnhealthyContainers(
          budget.getUnhealthyTasksLimit().getLimitOfUnhealthyContainers()
        ).build()
      );
    }

    return builder.build();
  }

  private static Day convertDay(String day) {
    switch (day) {
      case "Monday":
        return Day.Monday;
      case "Tuesday":
        return Day.Tuesday;
      case "Wednesday":
        return Day.Wednesday;
      case "Thursday":
        return Day.Thursday;
      case "Friday":
        return Day.Friday;
      case "Saturday":
        return Day.Saturday;
      default:
        return Day.Sunday;
    }
  }
}
