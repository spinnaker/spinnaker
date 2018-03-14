import metricStoreConfigStore from '../metricStoreConfig.service';
import DatadogMetricConfigurer, { queryFinder } from './metricConfigurer';

metricStoreConfigStore.register({
  name: 'datadog',
  metricConfigurer: DatadogMetricConfigurer,
  queryFinder,
});
