import { REST } from '@spinnaker/core';

import type { IMetricsServiceMetadata } from '../domain/IMetricsServiceMetadata';

export const listMetricsServiceMetadata = (
  filter?: string,
  metricsAccountName?: string,
): Promise<IMetricsServiceMetadata[]> =>
  Promise.resolve(
    REST('/v2/canaries/metadata/metricsService').query({ filter, metricsAccountName }).get<IMetricsServiceMetadata[]>(),
  );
