import type { IMetricsServiceMetadata } from 'kayenta/domain/IMetricsServiceMetadata';

import { REST } from '@spinnaker/core';

export const listMetricsServiceMetadata = (
  filter?: string,
  metricsAccountName?: string,
): Promise<IMetricsServiceMetadata[]> =>
  Promise.resolve(
    REST('/v2/canaries/metadata/metricsService').query({ filter, metricsAccountName }).get<IMetricsServiceMetadata[]>(),
  );
