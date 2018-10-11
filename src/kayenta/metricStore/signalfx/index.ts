import metricStoreConfigStore from '../metricStoreConfig.service';
import SignalFxMetricConfigurer, { queryFinder } from './metricConfigurer';

metricStoreConfigStore.register({
  name: 'signalfx',
  metricConfigurer: SignalFxMetricConfigurer,
  queryFinder,
});
