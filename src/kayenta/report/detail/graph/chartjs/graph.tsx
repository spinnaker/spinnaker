import * as React from 'react';
import { Chart, ChartLegendItem } from 'chart.js';

import { IMetricSetPairGraphProps } from '../metricSetPairGraph.service';
import { buildChartConfig } from './chartConfigFactory';

export interface ChartLegendItemCallback {
  (e: MouseEvent, legendItem: ChartLegendItem): void;
}

export default class ChartJSGraph extends React.Component<IMetricSetPairGraphProps> {
  private canvas: HTMLCanvasElement;
  public chart: Chart;

  public legendOnClickHideAxis: ChartLegendItemCallback = (e: MouseEvent, legendItem: ChartLegendItem): void => {
    const xAxes = this.chart.config.options.scales.xAxes;

    xAxes.forEach(axis => {
      if (axis.scaleLabel.labelString === legendItem.text) {
        axis.display = !axis.display;
      }
    });

    //call the default handler now
    Chart.defaults.global.legend.onClick.call(this, e, legendItem);
  };

  public componentDidMount(): void {
    const context = this.canvas.getContext('2d');
    const { metricSetPair, type } = this.props;
    this.chart = new Chart.Chart(context, buildChartConfig(metricSetPair, type, this.legendOnClickHideAxis));
  }

  public render() {
    return <canvas ref={canvas => (this.canvas = canvas)} />;
  }
}
