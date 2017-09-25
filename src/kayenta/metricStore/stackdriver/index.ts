import { get } from 'lodash';
import metricStoreConfigStore from '../metricStoreConfig.service';
import StackdriverMetricConfigurer from './metricConfigurer';
import { ICanaryMetricConfig } from '../../domain/ICanaryConfig';

metricStoreConfigStore.register({
  name: 'stackdriver',
  metricConfigurer: StackdriverMetricConfigurer,
  queryFinder: (metric: ICanaryMetricConfig) => get(metric, 'query.metricType', '').split('/').slice(1).join('/'),
});
