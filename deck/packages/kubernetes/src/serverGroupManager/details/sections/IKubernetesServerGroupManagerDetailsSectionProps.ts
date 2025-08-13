import type { IManifest, IServerGroupManagerDetailsProps } from '@spinnaker/core';
import type { IKubernetesServerGroupManager } from '../../../interfaces';

export interface IKubernetesServerGroupManagerDetailsSectionProps extends IServerGroupManagerDetailsProps {
  serverGroupManager: IKubernetesServerGroupManager;
  manifest: IManifest;
}
