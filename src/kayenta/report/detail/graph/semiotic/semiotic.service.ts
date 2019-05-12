import { IMetricSetPairGraphProps } from '../metricSetPairGraph.service';

export interface ISemioticChartProps extends IMetricSetPairGraphProps {
  parentWidth: number;
}

export interface IMargin {
  top?: number;
  bottom?: number;
  left?: number;
  right?: number;
}

export interface ITooltip {
  content: JSX.Element;
  x: number;
  y: number;
}

export interface ISummaryStatistics {
  [prop: string]: ISummaryStatisticsValue;
}

export interface ISummaryStatisticsValue {
  value: number;
  label: string;
}

export interface ITimeSeriesDataSets {
  value: number;
  label: string;
}
