import { get } from 'lodash';
import metricStoreConfigStore from '../metricStoreConfig.service';
import PrometheusMetricConfigurer from './metricConfigurer';
import { ICanaryMetricConfig } from 'kayenta/domain/ICanaryConfig';

metricStoreConfigStore.register({
  name: 'prometheus',
  metricConfigurer: PrometheusMetricConfigurer,
  queryFinder: (metric: ICanaryMetricConfig) => get(metric, 'query.metricName', ''),
  useTemplates: true,
});
