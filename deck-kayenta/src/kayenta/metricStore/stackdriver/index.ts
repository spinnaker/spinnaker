import { ICanaryMetricConfig } from 'kayenta/domain/ICanaryConfig';
import { get } from 'lodash';

import StackdriverMetricConfigurer from './metricConfigurer';
import metricStoreConfigStore from '../metricStoreConfig.service';

metricStoreConfigStore.register({
  name: 'stackdriver',
  metricConfigurer: StackdriverMetricConfigurer,
  queryFinder: (metric: ICanaryMetricConfig) => get(metric, 'query.metricType', ''),
  useTemplates: true,
});
