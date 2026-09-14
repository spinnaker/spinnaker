import { get } from 'lodash';

import type { ICanaryMetricConfig } from '../../domain/ICanaryConfig';
import ClickhouseMetricConfigurer from './metricConfigurer';
import metricStoreConfigStore from '../metricStoreConfig.service';

metricStoreConfigStore.register({
  name: 'clickhouse',
  metricConfigurer: ClickhouseMetricConfigurer,
  queryFinder: (metric: ICanaryMetricConfig) =>
    get(metric, 'query.customInlineTemplate', '') || get(metric, 'query.customFilterTemplate', ''),
  useTemplates: true,
});
