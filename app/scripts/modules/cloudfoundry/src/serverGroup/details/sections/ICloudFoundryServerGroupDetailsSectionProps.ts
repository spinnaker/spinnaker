import { IServerGroupDetailsSectionProps } from '@spinnaker/core';

import { ICloudFoundryServerGroup } from 'cloudfoundry/domain';

export interface ICloudFoundryServerGroupDetailsSectionProps extends IServerGroupDetailsSectionProps {
  serverGroup: ICloudFoundryServerGroup;
}
