import metricStoreConfigStore from '../metricStoreConfig.service';
import AtlasMetricConfigurer from './atlasMetricConfigurer';

metricStoreConfigStore.register({
  name: 'atlas',
  metricConfigurer: AtlasMetricConfigurer,
});
