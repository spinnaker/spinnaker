import GraphiteMetricConfigurer, { queryFinder } from './metricConfigurer';
import metricStoreConfigStore from '../metricStoreConfig.service';

metricStoreConfigStore.register({
  name: 'graphite',
  metricConfigurer: GraphiteMetricConfigurer,
  queryFinder,
});
