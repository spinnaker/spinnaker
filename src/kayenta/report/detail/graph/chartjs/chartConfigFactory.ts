import { ChartConfiguration, ChartPoint } from 'chart.js';
import * as moment from 'moment-timezone';

import { SETTINGS } from '@spinnaker/core';
import { IMetricSetPair, IMetricSetScope } from 'kayenta/domain/IMetricSetPair';
import { GraphType } from '../metricSetPairGraph.service';
import { ChartLegendItemCallback } from './graph';

const { defaultTimeZone } = SETTINGS;

// TODO(dpeach): remove this after https://github.com/chartjs/Chart.js/pull/4843
// has been merged and released.
moment.tz.setDefault(defaultTimeZone);

const BASELINE_COLOR_HEX = '#983f00';
const CANARY_COLOR_HEX = '#0175dc';

const buildChartPoints = (values: number[], scope: IMetricSetScope): ChartPoint[] => {
  if (!values) {
    return [];
  }

  return values
    .map((y, i) => {
      if (typeof y !== 'number') {
        return null;
      }

      const dataPointMillis = scope.startTimeMillis + scope.stepMillis * i;
      return {
        x: dataPointMillis,
        y,
      };
    })
    .filter(point => !!point);
};

export const buildChartConfig = (
  metricSetPair: IMetricSetPair,
  type: GraphType,
  legendCallback: ChartLegendItemCallback,
): ChartConfiguration => {
  if (type !== GraphType.AmplitudeVsTime) {
    // only one type for now.
    return null;
  }

  const controlData = buildChartPoints(metricSetPair.values.control, metricSetPair.scopes.control);
  const experimentData = buildChartPoints(metricSetPair.values.experiment, metricSetPair.scopes.experiment);

  //if the experiment and control have different start times add a second axis
  if (controlData.length > 0 && experimentData.length > 0 && controlData[0].x !== experimentData[0].x) {
    return buildDualAxisLineChartConfig(controlData, experimentData, legendCallback);
  } else {
    return buildDefaultLineChartConfig(controlData, experimentData);
  }
};

export const buildDefaultLineChartConfig = (
  controlData: ChartPoint[],
  experimentData: ChartPoint[],
): ChartConfiguration => {
  return {
    type: 'line',
    data: {
      datasets: [
        {
          borderColor: BASELINE_COLOR_HEX,
          borderWidth: 1,
          pointRadius: 0,
          backgroundColor: 'transparent',
          data: controlData,
          label: 'Baseline',
          steppedLine: true,
        },
        {
          borderColor: CANARY_COLOR_HEX,
          borderWidth: 1,
          pointRadius: 0,
          backgroundColor: 'transparent',
          data: experimentData,
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
        labels: {
          fontSize: 10,
          padding: 5,
        },
      },
      scales: {
        xAxes: [
          {
            type: 'time',
            distribution: 'series',
          } as any,
        ], // bad typings.
      },
    },
  };
};

export const buildDualAxisLineChartConfig = (
  controlData: ChartPoint[],
  experimentData: ChartPoint[],
  legendCallback: ChartLegendItemCallback,
): ChartConfiguration => {
  return {
    type: 'line',
    data: {
      datasets: [
        {
          borderColor: BASELINE_COLOR_HEX,
          borderWidth: 1,
          pointRadius: 0,
          backgroundColor: 'transparent',
          data: controlData,
          label: 'Baseline',
          xAxisID: 'baseline-axis',
          steppedLine: true,
        },
        {
          borderColor: CANARY_COLOR_HEX,
          borderWidth: 1,
          pointRadius: 0,
          backgroundColor: 'transparent',
          data: experimentData,
          label: 'Canary',
          xAxisID: 'canary-axis',
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
        onClick: legendCallback,
        labels: {
          fontSize: 15,
          padding: 5,
        },
      },
      scales: {
        xAxes: [
          {
            id: 'canary-axis',
            label: 'Canary',
            type: 'time',
            distribution: 'series',
            scaleLabel: {
              display: true,
              labelString: 'Canary',
            },
            time: {
              min: experimentData[0].x,
              max: experimentData[experimentData.length - 1].x,
            },
            ticks: {
              fontColor: CANARY_COLOR_HEX,
            },
          } as any,
          {
            id: 'baseline-axis',
            label: 'Baseline',
            type: 'time',
            distribution: 'series',
            scaleLabel: {
              display: true,
              labelString: 'Baseline',
            },
            time: {
              min: controlData[0].x,
              max: controlData[controlData.length - 1].x,
            },
            ticks: {
              fontColor: BASELINE_COLOR_HEX,
            },
          } as any,
        ], // bad typings.
      },
    },
  };
};
