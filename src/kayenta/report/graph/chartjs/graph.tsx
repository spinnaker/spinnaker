import * as React from 'react';

import { IMetricSetPairGraphProps } from '../metricSetPairGraph.service';

// TODO: actually use ChartJS to render a graph.
export default class ChartJSGraph extends React.Component<IMetricSetPairGraphProps> {
  public render() {
    return <pre>{JSON.stringify(this.props.metricSetPair, null, 2)}</pre>;
  }
}
