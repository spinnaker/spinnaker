package com.netflix.spinnaker.kork.web.exceptions;

import com.netflix.spinnaker.kork.api.exceptions.ExceptionDetails;
import com.netflix.spinnaker.kork.api.exceptions.ExceptionSummary;
import com.netflix.spinnaker.kork.api.exceptions.ExceptionSummary.TraceDetail;
import com.netflix.spinnaker.kork.api.exceptions.ExceptionSummary.TraceDetail.TraceDetailBuilder;
import com.netflix.spinnaker.kork.exceptions.HasAdditionalAttributes;
import com.netflix.spinnaker.kork.exceptions.SpinnakerException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Builds an {@link ExceptionSummary} object from a given Exception. This object is meant to help
 * provide context to end-users to help resolve issues, while still offering enough detail for
 * operators and developers to trace internal bugs.
 */
public class ExceptionSummaryService {

  private final ExceptionMessageDecorator exceptionMessageDecorator;

  public ExceptionSummaryService(ExceptionMessageDecorator exceptionMessageDecorator) {
    this.exceptionMessageDecorator = exceptionMessageDecorator;
  }

  /**
   * Provides the {@link ExceptionSummary} given the thrown exception.
   *
   * @param throwable {@link Throwable}
   * @return {@link ExceptionSummary}
   */
  public ExceptionSummary summary(
      Throwable throwable, @Nullable ExceptionDetails exceptionDetails) {
    List<TraceDetail> details = new ArrayList<>();

    Throwable cause = throwable;
    do {
      details.add(createTraceDetail(cause, exceptionDetails));
      cause = cause.getCause();
    } while (cause != null);

    List<TraceDetail> reversedDetails = new ArrayList<>(details);
    Collections.reverse(reversedDetails);

    Boolean retryable =
        reversedDetails.stream()
            .filter(d -> d.getRetryable() != null)
            .findFirst()
            .map(TraceDetail::getRetryable)
            .orElse(null);

    return ExceptionSummary.builder()
        .cause(details.get(details.size() - 1).getMessage())
        .message(details.get(0).getMessage())
        .details(reversedDetails)
        .retryable(retryable)
        .build();
  }

  public ExceptionSummary summary(Throwable throwable) {
    return summary(throwable, null);
  }

  private TraceDetail createTraceDetail(
      Throwable throwable, @Nullable ExceptionDetails exceptionDetails) {
    TraceDetailBuilder detailBuilder = TraceDetail.builder().message(throwable.getMessage());

    if (throwable instanceof SpinnakerException) {
      SpinnakerException spinnakerException = (SpinnakerException) throwable;

      detailBuilder
          .userMessage(
              exceptionMessageDecorator.decorate(
                  throwable, spinnakerException.getUserMessage(), exceptionDetails))
          .retryable(spinnakerException.getRetryable());
    }
    if (throwable instanceof HasAdditionalAttributes) {
      detailBuilder.additionalAttributes(
          ((HasAdditionalAttributes) throwable).getAdditionalAttributes());
    }

    return detailBuilder.build();
  }
}
