import { IScalingPolicy, ITargetTrackingConfiguration } from '@spinnaker/amazon';

export interface ITitusPolicy extends IScalingPolicy {
  targetPolicyDescriptor?: ITargetTrackingConfiguration;
}
