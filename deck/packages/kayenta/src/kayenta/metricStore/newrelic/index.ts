import NewRelicMetricConfigurer, { queryFinder } from './metricConfigurer';
import metricStoreConfigStore from '../metricStoreConfig.service';

metricStoreConfigStore.register({
  name: 'newrelic',
  metricConfigurer: NewRelicMetricConfigurer,
  queryFinder,
});
