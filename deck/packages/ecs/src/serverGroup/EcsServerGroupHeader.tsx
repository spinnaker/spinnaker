import React from 'react';

import {
  Alerts,
  CloudProviderIcon,
  Health,
  LoadBalancers,
  MultiSelectCheckbox,
  NameUtils,
  overridesComponent,
  RunningTasks,
  SequenceAndBuildAndImages,
} from '@spinnaker/core';
import type { IServerGroup, IServerGroupHeaderProps } from '@spinnaker/core';

import { EcsRolloutStateBadge } from './EcsRolloutStateBadge';

interface IEcsServerGroup extends IServerGroup {
  isNative?: boolean;
  rolloutState?: string;
  rolloutStateReason?: string;
  taskDefinitionRevision?: number;
}

/**
 * Wraps core's SequenceAndBuildAndImages with the ECS-specific bits:
 * - a task-definition revision label (vNNN) when the server group has no sequence (ecs-native
 *   services are unversioned and roll task-definition revisions in place), and
 * - ECS's rollout-state badge, for ecs-native server groups only. Classic ECS payloads carry
 *   rolloutState too, but classic red/black creates a new versioned server group per deploy, so
 *   the card header stays unchanged for them.
 */
export class EcsSequenceAndBuildAndImages extends React.Component<IServerGroupHeaderProps> {
  public render() {
    const serverGroup = this.props.serverGroup as IEcsServerGroup;
    const hasSequence = !!NameUtils.getSequence(serverGroup.moniker.sequence);
    const { taskDefinitionRevision } = serverGroup;
    const revisionLabel =
      !hasSequence && taskDefinitionRevision != null ? `v${String(taskDefinitionRevision).padStart(3, '0')}` : null;
    const showRolloutState = serverGroup.isNative === true && !!serverGroup.rolloutState;

    return (
      <>
        {!!revisionLabel && <span className="server-group-sequence"> {revisionLabel}</span>}
        {showRolloutState && (
          <EcsRolloutStateBadge
            rolloutState={serverGroup.rolloutState}
            rolloutStateReason={serverGroup.rolloutStateReason}
          />
        )}
        <SequenceAndBuildAndImages {...this.props} />
      </>
    );
  }
}

export class EcsServerGroupHeader extends React.Component<IServerGroupHeaderProps> {
  public render() {
    const props = this.props;

    return (
      <div className="horizontal top server-group-title sticky-header-3">
        <div className="horizontal section-title flex-1">
          <MultiSelectCheckbox {...props} />
          <CloudProviderIcon {...props} />
          <EcsSequenceAndBuildAndImages {...props} />
          <Alerts {...props} />
        </div>

        <div className="horizontal center flex-none">
          <RunningTasks {...props} />
          <LoadBalancers {...props} />
          <Health {...props} />
        </div>
      </div>
    );
  }
}

export function registerEcsServerGroupHeader(provider: string): void {
  overridesComponent(EcsServerGroupHeader, 'serverGroups.pod.header', provider);
}
