import * as React from 'react';
import { scaleUtc } from 'd3-scale';
import { XYFrame } from 'semiotic';
import * as moment from 'moment-timezone';
import { curveStepAfter } from 'd3-shape';

import { ISemioticChartProps, IMargin } from './semiotic.service';
import { vizConfig } from './config';
import './differenceArea.less';
import * as utils from './utils';
import CustomAxisTickLabel from './customAxisTickLabel';
import SecondaryTSXAxis from './secondaryTSXAxis';

interface IDataPoint {
  timestampMillis: number;
  value: number;
}

interface IChartDataSet {
  label: string;
  color: string;
  coordinates: IDataPoint[];
}

interface IInterimDataSet {
  timestamp: number;
  canary: number | string;
  baseline: number | string;
}

interface IDifferenceAreaProps extends ISemioticChartProps {
  millisBaselineSet: number[];
}

/*
* Supplemental visualization in the time series view to highlight
* Canary difference to baseline at any given timestamp
*/
export default class DifferenceArea extends React.Component<IDifferenceAreaProps> {
  private margin: IMargin = {
    left: 60,
    right: 20,
  };

  private chartHeight = 40; // chart height not including axes height
  private headerHeight = 17;

  private getChartData = () => {
    const { metricSetPair, millisBaselineSet } = this.props;
    const {
      values: { experiment, control },
      scopes,
    } = metricSetPair;

    const stepMillis = scopes.control.stepMillis;
    const maxDataPoints = Math.max(experiment.length, control.length);

    // Align data sets in case canary & baseline have different lengths and/or starting time
    const intDataSet: IInterimDataSet[] = Array(maxDataPoints)
      .fill(0)
      .map((_, i) => {
        const e = experiment[i];
        const c = control[i];
        return {
          timestamp: scopes.control.startTimeMillis + i * stepMillis,
          canary: e,
          baseline: c,
        };
      })
      // filter based on the timestamps of the line chart
      .filter((ds: IInterimDataSet) => millisBaselineSet.includes(ds.timestamp));

    const baselineReferenceDataPoints: IDataPoint[] = intDataSet.map((ds: IInterimDataSet) => ({
      timestampMillis: ds.timestamp,
      value: 0,
    }));

    const differenceDataPoints: IDataPoint[] = intDataSet.map((ds: IInterimDataSet) => ({
      timestampMillis: ds.timestamp,
      value: typeof ds.canary === 'number' && typeof ds.baseline === 'number' ? ds.canary - ds.baseline : 0,
    }));

    return {
      chartData: [
        {
          label: 'difference',
          color: vizConfig.colors.canary,
          coordinates: differenceDataPoints,
        },
        {
          label: 'baselineReference',
          color: vizConfig.colors.baseline,
          coordinates: baselineReferenceDataPoints,
        },
      ],
    };
  };

  private getSecondaryAxis = (millisOffset: number, millisBaselineSet: number[]) => {
    const { parentWidth } = this.props;
    const millisSetCanary = millisBaselineSet.map((ms: number) => ms + millisOffset);

    return (
      <SecondaryTSXAxis
        margin={{ left: this.margin.left, right: this.margin.right, top: 0, bottom: 0 }}
        width={parentWidth}
        millisSet={millisSetCanary}
        axisLabel={'canary'}
        bottomOffset={0}
      />
    );
  };

  private getXAxisTotalHeight = (shouldUseSecondaryXAxis: boolean) => {
    const { axisTickLineHeight, axisTickLabelHeight, axisLabelHeight } = vizConfig.timeSeries;

    return shouldUseSecondaryXAxis
      ? 2 * (axisLabelHeight + axisTickLabelHeight) + axisTickLineHeight
      : axisTickLabelHeight;
  };

  public render() {
    const { metricSetPair, parentWidth, millisBaselineSet } = this.props;

    /*
    * Generate the data needed for the graph components
    */
    const { scopes } = metricSetPair;
    const { chartData } = this.getChartData();
    const millisOffset = scopes.experiment.startTimeMillis - scopes.control.startTimeMillis;
    const shouldUseSecondaryXAxis = millisOffset !== 0;

    /*
    * Build the visualization components
    */
    const xAxisTotalHeight = this.getXAxisTotalHeight(shouldUseSecondaryXAxis);
    const computedConfig = {
      lines: chartData,
      size: [parentWidth, this.chartHeight + xAxisTotalHeight],
      margin: { ...this.margin, top: 0, bottom: xAxisTotalHeight },
      lineType: {
        type: 'area',
        interpolator: curveStepAfter,
      },
      lineStyle: (ds: IChartDataSet) =>
        ds.label === 'difference'
          ? {
              fill: ds.color,
              fillOpacity: 0.6,
            }
          : {
              stroke: ds.color,
              strokeOpacity: 1,
              strokeWidth: 2,
              strokeDasharray: 5,
            },
      xAccessor: (d: IDataPoint) => moment(d.timestampMillis).toDate(),
      yAccessor: 'value',
      xScaleType: scaleUtc(),
      axes: [
        {
          orient: 'left',
          tickFormat: () => `\u0394 = 0`, // Δ = 0
          tickValues: [0],
        },
        {
          orient: 'bottom',
          tickValues: utils.calculateDateTimeTicks(millisBaselineSet),
          tickFormat: (d: number) => <CustomAxisTickLabel millis={d} />,
          label: shouldUseSecondaryXAxis ? 'Baseline' : undefined,
          className: shouldUseSecondaryXAxis ? 'baseline-dual-axis' : '',
        },
      ],
      xExtent: [millisBaselineSet[0], millisBaselineSet[millisBaselineSet.length - 1]],
    };

    return (
      <div className="difference-area">
        <div className="chart-title" style={{ height: this.headerHeight }}>
          Canary Value Differences from Baseline
        </div>
        <XYFrame {...computedConfig} />
        {shouldUseSecondaryXAxis ? this.getSecondaryAxis(millisOffset, millisBaselineSet) : null}
      </div>
    );
  }
}
