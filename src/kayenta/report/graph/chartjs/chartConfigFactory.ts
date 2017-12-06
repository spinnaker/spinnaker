import { ChartConfiguration, ChartPoint } from 'chart.js';
import * as moment from 'moment-timezone';

import { SETTINGS } from '@spinnaker/core';
import { IMetricSetPair, IMetricSetScope } from 'kayenta/domain/IMetricSetPair';
import { GraphType } from '../metricSetPairGraph.service';

const { defaultTimeZone } = SETTINGS;

// TODO(dpeach): remove this after https://github.com/chartjs/Chart.js/pull/4843
// has been merged and released.
moment.tz.setDefault(defaultTimeZone);

const buildChartPoints = (values: number[], scope: IMetricSetScope): ChartPoint[] => {
  if (!values) {
    return [];
  }

  return values.map((y, i) => {
    if (typeof y !== 'number') {
      return null;
    }

    const dataPointMillis = scope.startTimeMillis + (scope.stepMillis * i);
    return {
      x: dataPointMillis,
      y,
    };
  }).filter(point => !!point);
};

export const buildChartConfig = (metricSetPair: IMetricSetPair, type: GraphType): ChartConfiguration => {
  if (type !== GraphType.AmplitudeVsTime) {
    // only one type for now.
    return null;
  }

  return {
    type: 'line',
    data: {
      datasets: [
        {
          borderColor: '#983f00',
          borderWidth: 1,
          pointRadius: 0,
          backgroundColor: 'transparent',
          data: buildChartPoints(metricSetPair.values.control, metricSetPair.scopes.control),
          label: 'Baseline',
          steppedLine: true,
        },
        {
          borderColor: '#0175dc',
          borderWidth: 1,
          pointRadius: 0,
          backgroundColor: 'transparent',
          data: buildChartPoints(metricSetPair.values.experiment, metricSetPair.scopes.experiment),
          label: 'Canary',
          steppedLine: true,
        },
      ],
    },
    options: {
      animation: {
        duration: 0,
      },
      legend: {
        position: 'bottom',
      },
      scales: {
        xAxes: [{
          type: 'time',
          distribution: 'series'
        } as any], // bad typings.
      },
    },
  };
};
