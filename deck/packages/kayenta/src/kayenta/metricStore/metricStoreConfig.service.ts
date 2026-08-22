import type * as React from 'react';

import type { ICanaryMetricConfig } from '../domain';
import { buildDelegateService } from '../service/delegateFactory';

export interface IMetricStoreConfig {
  name: string;
  metricConfigurer: React.ComponentType;
  queryFinder: (metric: ICanaryMetricConfig) => string;
  useTemplates?: boolean;
}

export default buildDelegateService<IMetricStoreConfig>();
