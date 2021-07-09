import { IServerGroupDetailsSectionProps } from '@spinnaker/core';
import { ICloudFoundryServerGroup } from '../../../domain';

export interface ICloudFoundryServerGroupDetailsSectionProps extends IServerGroupDetailsSectionProps {
  serverGroup: ICloudFoundryServerGroup;
}
