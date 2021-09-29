import { defaults } from 'lodash';

import type { IInstanceStorage, IProviderSettings } from '@spinnaker/core';
import { SETTINGS } from '@spinnaker/core';

export interface IGCEProviderSettings extends IProviderSettings {
  defaults: {
    account?: string;
    region?: string;
    zone?: string;
    instanceTypeStorage?: IInstanceStorage;
  };
  feature: {
    predictiveAutoscaling?: boolean;
    statefulMigsEnabled?: boolean;
  };
}

export const GCEProviderSettings: IGCEProviderSettings = defaults(SETTINGS.providers.gce || {}, {
  defaults: {},
  feature: {},
  resetToOriginal: SETTINGS.resetProvider('gce'),
});
