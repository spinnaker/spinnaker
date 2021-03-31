import { ICanaryMetricConfig } from 'kayenta/domain';
import { buildDelegateService } from 'kayenta/service/delegateFactory';
import * as React from 'react';

export interface IMetricStoreConfig {
  name: string;
  metricConfigurer: React.ComponentClass;
  queryFinder: (metric: ICanaryMetricConfig) => string;
  useTemplates?: boolean;
}

export default buildDelegateService<IMetricStoreConfig>();
