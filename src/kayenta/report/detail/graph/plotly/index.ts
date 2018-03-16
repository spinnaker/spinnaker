import { GraphType, metricSetPairGraphService } from '../metricSetPairGraph.service';
import PlotlyGraph from './plotly-graph';

metricSetPairGraphService.register({
  name: 'plotly',
  handlesGraphType: type => [GraphType.Histogram].includes(type),
  getGraph: () => PlotlyGraph,
});
