import { ReactInjector } from '@spinnaker/core';
import { IMetricsServiceMetadata } from 'kayenta/domain/IMetricsServiceMetadata';

export const listMetricsServiceMetadata = (filter?: string): Promise<IMetricsServiceMetadata[]> =>
  ReactInjector.API.one('v2/canaries/metadata/metricsService').withParams({ filter }).get();
