import { get } from 'lodash';

import type { ICanaryMetricConfig } from '../../domain/ICanaryConfig';
import PrometheusMetricConfigurer from './metricConfigurer';
import metricStoreConfigStore from '../metricStoreConfig.service';

metricStoreConfigStore.register({
  name: 'prometheus',
  metricConfigurer: PrometheusMetricConfigurer,
  queryFinder: (metric: ICanaryMetricConfig) => get(metric, 'query.metricName', ''),
  useTemplates: true,
});
