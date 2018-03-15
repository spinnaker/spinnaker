package com.netflix.kayenta.datadog.service;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@Data
public class DatadogTimeSeries {
  private List<DatadogSeriesEntry> series;

  @Data
  static public class DatadogSeriesEntry {
    private String scope;
    private Long start;
    private Long interval;
    private Long end;
    private List<List<Number>> pointlist;

    // Datadog returns an array of timestamp/value pairs; the pairs are
    // ordered, but may not be sequential (ie. may be a sparse result)
    // Since Kayenta's MetricSet is storing a simple array, we need to
    // convert this sparse list to a full array, and make sure we slot the
    // values into the correct array indices.
    @JsonIgnore
    private List<Double> adjustedPointList;
    @JsonIgnore
    private List<Double> getAdjustedPointList() {
      if ((this.adjustedPointList != null) && (this.adjustedPointList.size() != 0)) {
        // Already computed, just return.
        return this.adjustedPointList;
      }

      // Start at <start> time and index zero.
      this.adjustedPointList = new ArrayList<Double>();
      int idx = 0;
      for (Long time = start; time <= end; time += this.interval * 1000) {
        List<Number> point = pointlist.get(idx);

        // If the point at this index matches the timestamp at this position,
        // add the value to the array and advance the index.
        if (point.get(0).longValue() == time) {
          this.adjustedPointList.add(point.get(1).doubleValue());
          idx++;
        } else {
          // Otherwise, put in a NaN to represent the "gap" in the Datadog
          // data.
          this.adjustedPointList.add(Double.NaN);
        }
      }

      return this.adjustedPointList;
    }

    @JsonIgnore
    public Stream<Double> getDataPoints() {
      return this.getAdjustedPointList().stream();
    }
  }
}
