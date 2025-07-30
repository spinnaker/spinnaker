import type { IServerGroupDetailsSectionProps } from '@spinnaker/core';
import type { IKubernetesServerGroupView } from '../../../interfaces';

export interface IKubernetesServerGroupDetailsSectionProps extends IServerGroupDetailsSectionProps {
  serverGroup: IKubernetesServerGroupView;
}
