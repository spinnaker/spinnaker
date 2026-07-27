import type * as React from 'react';

import type { ICanaryAnalysisResult, IMetricSetPair } from '../../../domain';
import { buildDelegateService } from '../../../service/delegateFactory';

// e.g., amplitude vs. time, histogram, etc.
export enum GraphType {
  TimeSeries = 'Time Series',
  Histogram = 'Histogram',
  BoxPlot = 'Beeswarm Box Plot',
}

export interface IMetricSetPairGraphProps {
  type: GraphType;
  metricSetPair: IMetricSetPair;
  result: ICanaryAnalysisResult;
}

export interface IMetricSetPairGraph {
  /*
   * Name of the graph implementation, referenced in settings.js.
   * */
  name: string;

  /*
   * Returns top-level graph component class.
   * */
  getGraph: () => React.ComponentType<IMetricSetPairGraphProps>;

  /*
   * Returns true if the graph implementation supports a given graph type.
   * */
  handlesGraphType: (type: GraphType) => boolean;
}

export const metricSetPairGraphService = buildDelegateService<IMetricSetPairGraph>();
