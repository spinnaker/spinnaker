import * as React from 'react';

import { ICanaryMetricConfig } from 'kayenta/domain';
import { buildDelegateService } from 'kayenta/service/delegateFactory';

export interface IMetricStoreConfig {
  name: string;
  metricConfigurer: React.ComponentClass;
  queryFinder: (metric: ICanaryMetricConfig) => string;
  useTemplates?: boolean;
}

export default buildDelegateService<IMetricStoreConfig>();
