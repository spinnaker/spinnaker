import DatadogMetricConfigurer, { queryFinder } from './metricConfigurer';
import metricStoreConfigStore from '../metricStoreConfig.service';

metricStoreConfigStore.register({
  name: 'datadog',
  metricConfigurer: DatadogMetricConfigurer,
  queryFinder,
  useTemplates: true,
});
