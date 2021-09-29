import type { IServerGroupDetailsSectionProps } from '@spinnaker/core';
import type { ICloudFoundryServerGroup } from '../../../domain';

export interface ICloudFoundryServerGroupDetailsSectionProps extends IServerGroupDetailsSectionProps {
  serverGroup: ICloudFoundryServerGroup;
}
