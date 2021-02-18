import { ITitusServerGroup } from 'titus/domain';

import { IServerGroupDetailsSectionProps } from '@spinnaker/core';

export interface ITitusServerGroupDetailsSectionProps extends IServerGroupDetailsSectionProps {
  serverGroup: ITitusServerGroup;
}
