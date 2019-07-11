import { IServerGroupDetailsSectionProps } from '@spinnaker/core';
import { ITitusServerGroup } from 'titus/domain';

export interface ITitusServerGroupDetailsSectionProps extends IServerGroupDetailsSectionProps {
  serverGroup: ITitusServerGroup;
}
