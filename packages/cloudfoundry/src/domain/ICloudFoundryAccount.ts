import { IAccountDetails } from '@spinnaker/core';

import { ICloudFoundryDomain } from './ICloudFoundryLoadBalancer';
import { ICloudFoundrySpace } from './ICloudFoundrySpace';

export interface ICloudFoundryAccount extends IAccountDetails {
  domains?: ICloudFoundryDomain[];
  spaces?: ICloudFoundrySpace[];
}
