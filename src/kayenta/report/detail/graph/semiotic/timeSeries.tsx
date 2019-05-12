import * as React from 'react';
import { scaleUtc } from 'd3-scale';
import { MinimapXYFrame, XYFrame, IXYFrameProps, IMinimapProps, IXYFrameHoverBaseArgs } from 'semiotic';
import * as moment from 'moment-timezone';
import { SETTINGS } from '@spinnaker/core';
const { defaultTimeZone } = SETTINGS;
import { curveStepAfter } from 'd3-shape';
import * as classNames from 'classnames';
import { chain } from 'lodash';

import { IMetricSetPair } from 'kayenta/domain/IMetricSetPair';
import * as utils from './utils';
import Tooltip from './tooltip';
import ChartHeader from './chartHeader';
import ChartLegend from './chartLegend';
import { ISemioticChartProps, IMargin, ITooltip } from './semiotic.service';
import './timeSeries.less';
import { vizConfig } from './config';
import CircleIcon from './circleIcon';
import DifferenceArea from './differenceArea';
import SecondaryTSXAxis from './secondaryTSXAxis';
import CustomAxisTickLabel from './customAxisTickLabel';

moment.tz.setDefault(defaultTimeZone);

interface IDataPoint {
  timestampMillis: number;
  value: number | null;
  timestampMillisBaseline: number;
  timestampMillisMain: number;
}

interface IChartDataSet {
  color: string;
  label: string;
  coordinates: IDataPoint[];
  coordinatesUnfiltered: IDataPoint[];
}

interface IChartData {
  dataSets: IChartDataSet[];
  xExtentMain: number[];
  millisSetMain: number[];
  millisSetBaselineAll: number[];
}

interface ITimeSeriesState {
  tooltip: ITooltip;
  userBrushExtent: Date[] | null;
  showGroup: { [group: string]: boolean };
  graphs: { [graph: string]: JSX.Element };
}

interface ITooltipDataPoint {
  color: string;
  label: string;
  value: number | null;
  ts: number;
}

interface IDataSetsAttributes {
  startTimeMillisOffset: number;
  maxDataCount: number;
  shouldDisplayMinimap: boolean;
  shouldUseSecondaryXAxis: boolean;
}

interface IInterimDataSet {
  hasValidData: boolean;
  timestampMillisBaseline: number;
  timestampMillisMain: number;
  valueList: number[];
  timestampMillisList: number[];
}

/*There are 3 vizualisations inside this component:
1. The main Timeseries chart
2. The minimap chart for users to zoom/ pan the main timeseries. Only show this if there are many data points
3. The difference chart that displays the diff between canary and baseline
*/
export default class TimeSeries extends React.Component<ISemioticChartProps, ITimeSeriesState> {
  public state: ITimeSeriesState = {
    tooltip: null,
    userBrushExtent: null,
    showGroup: {
      baseline: true,
      canary: true,
    },
    graphs: {
      // a standalone timeseries chart if data points are few, otherwise it also includes a brushable/ zoomable minimap
      line: null,
      // graph that shows the delta between canary and baseline
      differenceArea: null,
    },
  };

  private mainMinimapHeight = 320; // total height of the main & minimap (if applicable)

  private marginMain: IMargin = {
    top: 10,
    left: 60,
    right: 20,
  };

  private marginMinimap: IMargin = {
    top: 0,
    bottom: 0,
    left: 60,
    right: 20,
  };

  public componentDidMount() {
    this.createGraphs();
  }

  // Only reconstruct the graph components when necessary
  public componentDidUpdate(prevProps: ISemioticChartProps, prevState: ITimeSeriesState) {
    const { metricSetPair, parentWidth } = this.props;
    const { userBrushExtent, showGroup } = this.state;
    if (
      metricSetPair !== prevProps.metricSetPair ||
      parentWidth !== prevProps.parentWidth ||
      userBrushExtent !== prevState.userBrushExtent ||
      showGroup !== prevState.showGroup
    ) {
      this.createGraphs();
    }
  }

  /*
  *  Generate chart Data
  * In cases where the start Millis is different betwen canary and baseline, we want to:
  * 1) If data point count is different, extend the shorter dataset to match the longer one (i.e. match the x extent)
  * 2) normalize canary timestamp to match baseline timestamps (needed for the tooltip's
  * voronoi overlay logic in semiotic to work properly)
  */
  private getChartData = (dataSetsAttr: IDataSetsAttributes, metricSetPair: IMetricSetPair) => {
    const { maxDataCount } = dataSetsAttr;
    const { showGroup } = this.state;
    const { dataGroupMap, colors } = vizConfig;
    const { scopes, values } = metricSetPair;
    const { userBrushExtent } = this.state;
    const groups = ['baseline', 'canary'];
    const isOnlyCanarySelected = showGroup.canary && !showGroup.baseline;
    const stepMillis = scopes.control.stepMillis;

    /*
    * Deriving timestamps for the main line chart: use baseline's ts if baseline group or both groups are selected,
    * else use canary ts as reference (since we only show 1 x-axis)
    * Minimap should still consider both groups (hence using baseline's ts as reference)
    * as we want to maintain brush state when user toggles groups
    * Finally we also trim out timestamps with invalid values at both ends of the dataset
    */
    const intDataSet: IInterimDataSet[] = chain(Array(maxDataCount).fill(0))
      .map((_, i) => {
        const valueList = groups.map((g: string) => values[dataGroupMap[g]][i]);
        const timestampMillisList = groups.map((g: string) => scopes[dataGroupMap[g]].startTimeMillis + i * stepMillis);

        return {
          hasValidData: valueList.some((v: number | string) => typeof v === 'number'),
          timestampMillisBaseline: timestampMillisList[0],
          timestampMillisMain: isOnlyCanarySelected ? timestampMillisList[1] : timestampMillisList[0],
          valueList,
          timestampMillisList,
        };
      })
      .dropRightWhile((ds: IInterimDataSet) => !ds.hasValidData)
      .dropWhile((ds: IInterimDataSet) => !ds.hasValidData)
      .value();

    const dataSets: IChartDataSet[] = groups.map((g: string, i: number) => {
      const dataPoints = intDataSet.map((ds: IInterimDataSet) => {
        return {
          timestampMillis: ds.timestampMillisList[i], // original ts for this data point
          timestampMillisBaseline: ds.timestampMillisBaseline,
          timestampMillisMain: ds.timestampMillisMain,
          value: ds.valueList[i],
        };
      });

      return {
        label: g,
        color: colors[g],
        coordinates: dataPoints.filter(d => typeof d.value === 'number'),
        coordinatesUnfiltered: dataPoints,
      };
    });

    const millisSetBaselineAll = intDataSet.map((ds: IInterimDataSet) => ds.timestampMillisBaseline);

    // For the main line chart, filter by brush if applicable
    const intDataSetMain = !userBrushExtent
      ? intDataSet
      : intDataSet.filter((ds: IInterimDataSet) => {
          return (
            ds.timestampMillisBaseline >= userBrushExtent[0].valueOf() &&
            ds.timestampMillisBaseline <= userBrushExtent[1].valueOf()
          );
        });

    const millisSetMain = intDataSetMain.map((ds: IInterimDataSet) => ds.timestampMillisMain);

    return {
      dataSets, // actual datasets supplied to semiotic,
      millisSetBaselineAll,
      millisSetMain,
      xExtentMain: [millisSetMain[0], millisSetMain[millisSetMain.length - 1]],
    };
  };

  // common chart props that are used for both the main XYFrame and Minimap (if applicable)
  private createCommonChartProps = () => {
    return {
      lineType: {
        type: 'line',
        interpolator: curveStepAfter,
      },
      lineStyle: (ds: IChartDataSet) => ({
        stroke: ds.color,
        strokeWidth: 2,
        strokeOpacity: 0.8,
      }),
      yAccessor: 'value',
      xScaleType: scaleUtc(),
      baseMarkProps: { transitionDuration: { default: 200, fill: 200 } },
    };
  };

  private createLineChartProps = (
    chartData: IChartData,
    dataSetsAttributes: IDataSetsAttributes,
    commonChartProps: IXYFrameProps<IChartDataSet, IDataPoint>,
  ) => {
    const { axisTickLineHeight, axisTickLabelHeight, axisLabelHeight, minimapHeight } = vizConfig.timeSeries;

    const { parentWidth } = this.props;
    const { showGroup } = this.state;

    const { shouldUseSecondaryXAxis, shouldDisplayMinimap } = dataSetsAttributes;
    const { dataSets, millisSetMain, xExtentMain } = chartData;

    // if secondary axis is needed, we need more bottom margin to fit both axes
    const totalXAxisHeight = shouldUseSecondaryXAxis
      ? 2 * (axisTickLabelHeight + axisLabelHeight) + axisTickLineHeight
      : axisTickLabelHeight;

    const lineChartProps = {
      ...commonChartProps,
      lines: dataSets.filter((ds: IChartDataSet) => showGroup[ds.label]), // only show selected groups
      hoverAnnotation: [
        {
          type: 'x',
          disable: ['connector', 'note'],
        },
        {
          type: 'vertical-points',
          threshold: 0.1,
          r: () => 5,
        },
      ],
      customHoverBehavior: this.createChartHoverHandler(dataSets, dataSetsAttributes),
      xExtent: xExtentMain,
      axes: [
        {
          orient: 'left',
          label: 'metric value',
          tickFormat: (d: number) => utils.formatMetricValue(d),
        },
        {
          orient: 'bottom',
          label: shouldUseSecondaryXAxis ? 'Baseline' : undefined,
          tickValues: utils.calculateDateTimeTicks(millisSetMain),
          tickFormat: (d: number) => <CustomAxisTickLabel millis={d} />,
          className: shouldUseSecondaryXAxis ? 'baseline-dual-axis' : '',
        },
      ],
      margin: { ...this.marginMain, bottom: totalXAxisHeight },
      matte: true,
      size: [parentWidth, shouldDisplayMinimap ? this.mainMinimapHeight - minimapHeight : this.mainMinimapHeight],
      xAccessor: (d: IDataPoint) => moment(d.timestampMillisMain).toDate(),
    };

    if (shouldDisplayMinimap) {
      return {
        ...lineChartProps,
        minimap: {
          ...commonChartProps,
          lines: dataSets,
          yBrushable: false,
          brushEnd: this.onBrushEnd,
          size: [parentWidth, minimapHeight],
          axes: [
            {
              orient: 'left',
              tickFormat: (): void => null,
            },
            {
              orient: 'bottom',
            },
          ],
          margin: this.marginMinimap,
          xAccessor: (d: IDataPoint) => moment(d.timestampMillisBaseline).toDate(),
        } as IMinimapProps<IChartDataSet, IDataPoint>,
      };
    } else {
      return lineChartProps as IXYFrameProps<IChartDataSet, IDataPoint>;
    }
  };

  // construct the graph JSX components and store them as states
  private createGraphs = () => {
    const { metricSetPair, parentWidth } = this.props;
    const { showGroup } = this.state;
    const { minimapDataPointsThreshold, minimapHeight } = vizConfig.timeSeries;

    /*
    * Generate the data needed for the graph components
    */
    const baselineStartTimeMillis = metricSetPair.scopes.control.startTimeMillis;
    const canaryStartTimeMillis = metricSetPair.scopes.experiment.startTimeMillis;
    const isStartTimeMillisEqual = baselineStartTimeMillis === canaryStartTimeMillis;

    // Top level attributes to determine how data is formatted & which chart components to include
    const dataSetsAttributes = {
      startTimeMillisOffset: canaryStartTimeMillis - baselineStartTimeMillis,
      maxDataCount: Math.max(metricSetPair.values.control.length, metricSetPair.values.experiment.length),
      shouldDisplayMinimap: metricSetPair.values.control.length > minimapDataPointsThreshold,
      shouldUseSecondaryXAxis: !isStartTimeMillisEqual && showGroup.canary && showGroup.baseline,
    };

    const chartData = this.getChartData(dataSetsAttributes, metricSetPair);

    /*
    * Build the visualization components
    */
    const commonChartProps = this.createCommonChartProps();
    const lineChartProps = this.createLineChartProps(chartData, dataSetsAttributes, commonChartProps);

    const line = (
      <>
        <div className="time-series-chart">
          {dataSetsAttributes.shouldDisplayMinimap ? (
            <MinimapXYFrame {...lineChartProps} />
          ) : (
            <XYFrame {...lineChartProps} />
          )}
        </div>
        {dataSetsAttributes.shouldUseSecondaryXAxis ? (
          <SecondaryTSXAxis
            margin={{ left: this.marginMain.left, right: this.marginMain.right, top: 0, bottom: 0 }}
            width={parentWidth}
            millisSet={chartData.millisSetMain.map((ms: number) => ms + dataSetsAttributes.startTimeMillisOffset)}
            axisLabel="canary"
            bottomOffset={dataSetsAttributes.shouldDisplayMinimap ? minimapHeight : 0}
          />
        ) : null}
        {dataSetsAttributes.shouldDisplayMinimap ? (
          <div className="zoom-icon">
            <i className="fas fa-search-plus" />
          </div>
        ) : null}
      </>
    );

    const differenceArea =
      showGroup.baseline && showGroup.canary ? (
        <DifferenceArea {...this.props} millisBaselineSet={chartData.millisSetBaselineAll} />
      ) : null;

    this.setState({
      graphs: {
        line,
        differenceArea,
      },
    });
  };

  private onLegendClickHandler = (group: string) => {
    const showGroup = this.state.showGroup;
    this.setState({
      showGroup: { ...showGroup, [group]: !showGroup[group] },
    });
  };

  // function factory to create a custom hover handler function based on the datasets
  private createChartHoverHandler = (dataSets: IChartDataSet[], dataSetsAttr: IDataSetsAttributes) => {
    const { shouldUseSecondaryXAxis } = dataSetsAttr;
    return (d: (IXYFrameHoverBaseArgs<IDataPoint> & IDataPoint) | undefined) => {
      if (d && d.timestampMillisMain) {
        const tooltipData = dataSets.map(
          (ds: IChartDataSet): ITooltipDataPoint => {
            const coord = ds.coordinatesUnfiltered.find(
              (c: IDataPoint) => c.timestampMillisMain === d.timestampMillisMain,
            );
            return {
              color: ds.color,
              label: ds.label,
              ts: coord.timestampMillis,
              value: coord.value,
            };
          },
        );

        let tooltipRows = tooltipData
          .concat()
          .sort((a: ITooltipDataPoint, b: ITooltipDataPoint) => b.value - a.value)
          .map((o: ITooltipDataPoint) => {
            // if there's a ts offset, timestamp should be displayed for each group
            const tsRow = shouldUseSecondaryXAxis ? (
              <div className="tooltip-ts">{moment(o.ts).format('YYYY-MM-DD HH:mm:ss z')}</div>
            ) : null;

            return (
              <div key={o.label} className={classNames({ 'tooltip-dual-axis-row': shouldUseSecondaryXAxis })}>
                {tsRow}
                <div id={o.label}>
                  <CircleIcon group={o.label} />
                  <span>{`${o.label}: `}</span>
                  <span>{utils.formatMetricValue(o.value)}</span>
                </div>
              </div>
            );
          });

        if (tooltipData.length === 2) {
          const canaryMinusBaseline = tooltipData[1].value - tooltipData[0].value;
          tooltipRows = tooltipRows.concat([
            <div id="diff" key="diff" className="tooltip-row">
              <span>Canary - Baseline: </span>
              <span>{utils.formatMetricValue(canaryMinusBaseline)}</span>
            </div>,
          ]);
        }

        const tooltipContent = (
          <div>
            {/* if no dual axes, display timestamp row at the top level */}
            {shouldUseSecondaryXAxis ? null : (
              <div key="ts" className="tooltip-ts">
                {moment(tooltipData[0].ts).format('YYYY-MM-DD HH:mm:ss z')}
              </div>
            )}
            {tooltipRows}
          </div>
        );
        this.setState({
          tooltip: {
            content: tooltipContent,
            x: d.voronoiX + this.marginMain.left,
            y: d.voronoiY + this.marginMain.top,
          },
        });
      } else {
        this.setState({ tooltip: null });
      }
    };
  };

  // Handle user brush action event from semiotic
  private onBrushEnd = (e: Date[]) => {
    this.setState({
      userBrushExtent: e,
    });
  };

  public render() {
    const { metricSetPair } = this.props;
    const { showGroup, tooltip } = this.state;
    const { line, differenceArea } = this.state.graphs;

    return (
      <div className="time-series">
        <ChartHeader metric={metricSetPair.name} />
        <ChartLegend showGroup={showGroup} isClickable={true} onClickHandler={this.onLegendClickHandler} />
        <div className="graph-container">
          {line}
          <Tooltip {...tooltip} />
        </div>
        {differenceArea}
      </div>
    );
  }
}
