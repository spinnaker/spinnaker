import metricStoreConfigStore from '../metricStoreConfig.service';
import NewRelicMetricConfigurer, { queryFinder } from './metricConfigurer';

metricStoreConfigStore.register({
  name: 'newrelic',
  metricConfigurer: NewRelicMetricConfigurer,
  queryFinder,
});
