import type { IAccountDetails } from '@spinnaker/core';

import type { ICloudFoundryDomain } from './ICloudFoundryLoadBalancer';
import type { ICloudFoundrySpace } from './ICloudFoundrySpace';

export interface ICloudFoundryAccount extends IAccountDetails {
  domains?: ICloudFoundryDomain[];
  spaces?: ICloudFoundrySpace[];
}
