import metricStoreConfigStore from '../metricStoreConfig.service';
import AtlasMetricConfigurer from './atlasMetricConfigurer';
import { ICanaryMetricConfig } from 'kayenta/domain/ICanaryConfig';

metricStoreConfigStore.register({
  name: 'atlas',
  metricConfigurer: AtlasMetricConfigurer,
  queryFinder: (metric: ICanaryMetricConfig) => metric.query.q
});
