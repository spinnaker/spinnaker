import { IMetricsServiceMetadata } from 'kayenta/domain/IMetricsServiceMetadata';

import { REST } from '@spinnaker/core';

export const listMetricsServiceMetadata = (
  filter?: string,
  metricsAccountName?: string,
): PromiseLike<IMetricsServiceMetadata[]> =>
  REST('/v2/canaries/metadata/metricsService').query({ filter, metricsAccountName }).get();
