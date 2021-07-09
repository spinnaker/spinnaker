import * as React from 'react';
import { Application, IServerGroup, overridableComponent } from '@spinnaker/core';

export interface ITitusCustomScalingPolicyProps {
  application: Application;
  serverGroup: IServerGroup;
}

export const TitusCustomScalingPolicy: React.ComponentType<ITitusCustomScalingPolicyProps> = overridableComponent(
  () => null,
  'titus.serverGroup.details.customScaling',
);
