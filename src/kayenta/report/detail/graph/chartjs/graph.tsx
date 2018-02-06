import * as React from 'react';
import { Chart } from 'chart.js';

import { IMetricSetPairGraphProps } from '../metricSetPairGraph.service';
import { buildChartConfig } from './chartConfigFactory';

export default class ChartJSGraph extends React.Component<IMetricSetPairGraphProps> {
  private canvas: HTMLCanvasElement;
  private chart: Chart;

  public componentDidMount(): void {
    const context = this.canvas.getContext('2d');
    const { metricSetPair, type } = this.props;
    this.chart = new Chart.Chart(context, buildChartConfig(metricSetPair, type));
  }

  public render() {
    return <canvas ref={canvas => this.canvas = canvas}/>;
  }
}
