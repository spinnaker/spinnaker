import metricStoreConfigStore from '../metricStoreConfig.service';
import AtlasMetricConfigurer, { queryFinder } from './atlasMetricConfigurer';

metricStoreConfigStore.register({
  name: 'atlas',
  metricConfigurer: AtlasMetricConfigurer,
  queryFinder
});
