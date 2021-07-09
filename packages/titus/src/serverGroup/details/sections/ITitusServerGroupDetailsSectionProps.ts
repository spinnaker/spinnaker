import { IServerGroupDetailsSectionProps } from '@spinnaker/core';
import { ITitusServerGroup } from '../../../domain';

export interface ITitusServerGroupDetailsSectionProps extends IServerGroupDetailsSectionProps {
  serverGroup: ITitusServerGroup;
}
