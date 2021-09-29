import type { IServerGroupDetailsSectionProps } from '@spinnaker/core';
import type { ITitusServerGroup } from '../../../domain';

export interface ITitusServerGroupDetailsSectionProps extends IServerGroupDetailsSectionProps {
  serverGroup: ITitusServerGroup;
}
