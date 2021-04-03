import { GraphType, metricSetPairGraphService } from '../metricSetPairGraph.service';
import SemioticGraph from './semioticGraph';

const supportedGraphTypes: GraphType[] = [GraphType.TimeSeries, GraphType.Histogram, GraphType.BoxPlot];
// Semiotic component registration
metricSetPairGraphService.register({
  name: 'semiotic',
  handlesGraphType: (type) => supportedGraphTypes.includes(type),
  getGraph: () => SemioticGraph,
});
