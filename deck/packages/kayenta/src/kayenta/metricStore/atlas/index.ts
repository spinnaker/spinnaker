import AtlasMetricConfigurer, { queryFinder } from './atlasMetricConfigurer';
import metricStoreConfigStore from '../metricStoreConfig.service';

metricStoreConfigStore.register({
  name: 'atlas',
  metricConfigurer: AtlasMetricConfigurer,
  queryFinder,
});
