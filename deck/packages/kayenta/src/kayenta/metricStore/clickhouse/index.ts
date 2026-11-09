import ClickhouseMetricConfigurer, { queryFinder } from './metricConfigurer';
import metricStoreConfigStore from '../metricStoreConfig.service';

metricStoreConfigStore.register({
  name: 'clickhouse',
  metricConfigurer: ClickhouseMetricConfigurer,
  queryFinder,
  useTemplates: true,
});
