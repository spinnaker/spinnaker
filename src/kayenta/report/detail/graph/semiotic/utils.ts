import { format } from 'd3-format';
import * as moment from 'moment-timezone';
import { scaleUtc } from 'd3-scale';
import { quantile } from 'd3-array';
import { ISummaryStatistics } from './semiotic.service';

/*
Formatter for any metric values. If the value is not a valid number (typically NaN),
we want to display "N/A" to highlight that there was no valid measurement.
Else, the format is made as close as possible to Atlas graph formats:
SI notation is used when the exponent is between -24 and 24 (inclusive),
and exponential notation otherwise. Both with 3 digits of precision
*/
export const formatMetricValue = (value: any) => {
  if (typeof value !== 'number' || isNaN(value)) {
    return 'N/A';
  } else if (Math.abs(value) >= Math.pow(10, 25)) {
    return format('-.3~e')(value);
  } else {
    return format('-.3~s')(value);
  }
};

/*
Formatter for timestamps. If the timestamp value is a date boundary (e.g. 04-01-2019 00:00:00),
an additional date label is returned, otherwise only a single label showing hour & minute is returned
*/
export const dateTimeTickFormatter = (d: number) => {
  const m = moment(d);
  if (
    m
      .clone()
      .startOf('day')
      .unix() === m.unix()
  ) {
    return [m.format('HH:mm'), m.format('MMM DD')];
  } else {
    return [m.format('HH:mm')];
  }
};

// function to choose the ideal tick values on the x-axis of a timeseries
// D3 has a smart tick generator, e.g. it'll tend to show midnight if the data crosses
// date boundaries. This way we can label date change as date, and other ticks as HH:mm
export const calculateDateTimeTicks = (millisSet: number[]) => {
  const minMillis = millisSet[0];
  const maxMillis = millisSet[millisSet.length - 1];

  /*
  * since d3 scale doesn't support custom timezone, we have to:
  * 1)shift the UTC domain based on the tz,
  * 2)have d3 calculate the ideal tick values as usual, and
  * 3)shift back the result
  */
  const offsetMillis = moment(minMillis).utcOffset() * 60000;
  const minMillisShifted = minMillis + offsetMillis;
  const maxMillisShifted = maxMillis + offsetMillis;
  const scale = scaleUtc().domain([new Date(minMillisShifted), new Date(maxMillisShifted)]);
  const ticks = scale.ticks(6).map((d: Date) => new Date(d.valueOf() - offsetMillis));
  return ticks;
};

export const calculateSummaryStatistics = (values: number[]): ISummaryStatistics => {
  const output = {
    min: {
      value: quantile(values, 0.0),
      label: 'Minimum',
    },
    q1area: {
      value: quantile(values, 0.25),
      label: '25th %-ile',
    },
    median: {
      value: quantile(values, 0.5),
      label: 'Median',
    },
    q3area: {
      value: quantile(values, 0.75),
      label: '75th %-ile',
    },
    max: {
      value: quantile(values, 1.0),
      label: 'Maximum',
    },
  };
  return output;
};
