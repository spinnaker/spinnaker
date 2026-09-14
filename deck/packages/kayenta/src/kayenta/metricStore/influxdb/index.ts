import InfluxDbMetricConfigurer, { queryFinder } from './metricConfigurer';
import metricStoreConfigStore from '../metricStoreConfig.service';

metricStoreConfigStore.register({
  name: 'influxdb',
  metricConfigurer: InfluxDbMetricConfigurer,
  queryFinder,
  useTemplates: true,
});
