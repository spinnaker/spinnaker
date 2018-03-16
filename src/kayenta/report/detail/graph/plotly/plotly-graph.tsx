import * as React from 'react';
import * as Plotly from 'plotly.js';
import autoBindMethods from 'class-autobind-decorator';

import { IMetricSetPair } from 'kayenta/domain/IMetricSetPair';
import { GraphType, IMetricSetPairGraphProps } from '../metricSetPairGraph.service';

const BASELINE_COLOR = '#0075dc';
const CANARY_COLOR = '#993f00';

function renderHistogram(container: HTMLElement, metricSetPair: IMetricSetPair) {

  const noNan = (n: any) => n !== null && n !== 'NaN';
  const baselineData = metricSetPair.values.control.filter(noNan);
  const canaryData   = metricSetPair.values.experiment.filter(noNan);

  const baselineHisto = {
    name: 'Baseline',
    showlegend: true,
    x: baselineData,
    histnorm: 'count',
    type: 'histogram',
    opacity: 0.4,
    marker: {
      color: BASELINE_COLOR,
    },
    autobinx: true,
    xbins: {}
  };
  const canaryHisto = {
    name: 'Canary',
    x: canaryData,
    histnorm: 'count',
    type: 'histogram',
    opacity: 0.5,
    marker: {
      color: CANARY_COLOR,
    },
    autobinx: true,
    xbins: {}
  };

  const layout = {
    autosize: false,
    width: 830,
    height: 415,
    margin: {
      l: 40,
      r: 40,
      b: 40,
      t: 40,
      pad: 4,
    },
    barmode: 'overlay',
    bargroupgap: 0.01,
    plot_bgcolor: '#f5f5f5',
    paper_bgcolor: '#f5f5f5'
  };

  const promise = Plotly.newPlot(container, [ canaryHisto as any, baselineHisto as any], layout);
  promise.then((plot: any) => {
    const baselineBins = plot.data[0].xbins;
    const canaryBins = plot.data[1].xbins;
    if (baselineBins.start !== canaryBins.start
      || baselineBins.end !== canaryBins.end
      || baselineBins.size !== canaryBins.size) {
      // bins are different sizes which is messy for visual comparison; align them and replot
      const start = Math.min(baselineBins.start, canaryBins.start);
      const end = Math.max(baselineBins.end, canaryBins.end);
      const size = (baselineBins.size + canaryBins.size) / 2;
      baselineHisto.autobinx = false;
      baselineHisto.xbins = { start, end, size };
      canaryHisto.autobinx = false;
      canaryHisto.xbins = { start, end, size };

      Plotly.newPlot(container, [ baselineHisto as any, canaryHisto as any ], layout);
    }
  });

}


@autoBindMethods
export default class PlotlyGraph extends React.Component<IMetricSetPairGraphProps> {

  private container: HTMLElement;

  public renderGraph(container: HTMLElement): void {
    if (!container) {
      if (this.container) {
        Plotly.purge(this.container);
        this.container = null;
      }
      return;
    }

    this.container = container;

    const { metricSetPair, type } = this.props;
    if (type === GraphType.Histogram) {
      renderHistogram(container, metricSetPair);
    }
  }

  public render() {
    return (
      <div id="plotly" ref={this.renderGraph}/>
    );
  }
}
