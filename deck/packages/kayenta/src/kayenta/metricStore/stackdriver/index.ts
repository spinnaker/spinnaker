import { get } from 'lodash';

import type { ICanaryMetricConfig } from '../../domain/ICanaryConfig';
import StackdriverMetricConfigurer from './metricConfigurer';
import metricStoreConfigStore from '../metricStoreConfig.service';

metricStoreConfigStore.register({
  name: 'stackdriver',
  metricConfigurer: StackdriverMetricConfigurer,
  queryFinder: (metric: ICanaryMetricConfig) => get(metric, 'query.metricType', ''),
  useTemplates: true,
});
