import * as React from 'react';
import { IExecution } from '@spinnaker/core';

import { ICanaryMetricConfig, IMetricSetPair } from 'kayenta/domain';
import { buildDelegateService } from 'kayenta/service/delegateFactory';

export interface IMetricResultScopeProps {
  metricConfig: ICanaryMetricConfig
  metricSetPair: IMetricSetPair;
  run: IExecution;
}

export interface IMetricStoreConfig {
  name: string;
  metricConfigurer: React.ComponentClass;
  queryFinder: (metric: ICanaryMetricConfig) => string;
  metricResultScope?: React.ComponentClass<IMetricResultScopeProps>;
}

export default buildDelegateService<IMetricStoreConfig>();
