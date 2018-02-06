import {
  GraphType,
  metricSetPairGraphService
} from '../metricSetPairGraph.service';
import ChartJSGraph from './graph';

metricSetPairGraphService.register({
  name: 'chartjs',
  handlesGraphType: type => [GraphType.AmplitudeVsTime].includes(type),
  getGraph: () => ChartJSGraph,
});
