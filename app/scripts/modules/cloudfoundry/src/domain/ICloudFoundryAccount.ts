import { IAccountDetails } from '@spinnaker/core';

import { ICloudFoundryDomain, ICloudFoundrySpace } from 'cloudfoundry/domain';

export interface ICloudFoundryAccount extends IAccountDetails {
  domains?: ICloudFoundryDomain[];
  spaces?: ICloudFoundrySpace[];
}
