import metricStoreConfigStore from '../metricStoreConfig.service';
import GraphiteMetricConfigurer, { queryFinder } from './metricConfigurer';

metricStoreConfigStore.register({
  name: 'graphite',
  metricConfigurer: GraphiteMetricConfigurer,
  queryFinder,
});
