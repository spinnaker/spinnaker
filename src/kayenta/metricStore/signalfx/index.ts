import SignalFxMetricConfigurer, { queryFinder } from './metricConfigurer';
import metricStoreConfigStore from '../metricStoreConfig.service';

metricStoreConfigStore.register({
  name: 'signalfx',
  metricConfigurer: SignalFxMetricConfigurer,
  queryFinder,
});
