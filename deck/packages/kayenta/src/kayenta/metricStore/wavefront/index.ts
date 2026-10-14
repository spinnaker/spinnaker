import WavefrontMetricConfigurer, { queryFinder } from './metricConfigurer';
import metricStoreConfigStore from '../metricStoreConfig.service';

metricStoreConfigStore.register({
  name: 'wavefront',
  metricConfigurer: WavefrontMetricConfigurer,
  queryFinder,
  useTemplates: true,
});
