import metricStoreConfigStore from '../metricStoreConfig.service';
import StackdriverMetricConfigurer from './metricConfigurer';

metricStoreConfigStore.register({
  name: 'stackdriver',
  metricConfigurer: StackdriverMetricConfigurer,
});
