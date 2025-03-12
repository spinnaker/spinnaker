import type * as React from 'react';
import type { Application, IServerGroup } from '@spinnaker/core';
import { overridableComponent } from '@spinnaker/core';

export interface ITitusCustomScalingPolicyProps {
  application: Application;
  serverGroup: IServerGroup;
}

export const TitusCustomScalingPolicy: React.ComponentType<ITitusCustomScalingPolicyProps> = overridableComponent(
  () => null,
  'titus.serverGroup.details.customScaling',
);
