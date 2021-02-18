import { ICloudFoundryServerGroup } from 'cloudfoundry/domain';

import { IServerGroupDetailsSectionProps } from '@spinnaker/core';

export interface ICloudFoundryServerGroupDetailsSectionProps extends IServerGroupDetailsSectionProps {
  serverGroup: ICloudFoundryServerGroup;
}
