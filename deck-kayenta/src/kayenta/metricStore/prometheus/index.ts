import { ICanaryMetricConfig } from 'kayenta/domain/ICanaryConfig';
import { get } from 'lodash';

import PrometheusMetricConfigurer from './metricConfigurer';
import metricStoreConfigStore from '../metricStoreConfig.service';

metricStoreConfigStore.register({
  name: 'prometheus',
  metricConfigurer: PrometheusMetricConfigurer,
  queryFinder: (metric: ICanaryMetricConfig) => get(metric, 'query.metricName', ''),
  useTemplates: true,
});
