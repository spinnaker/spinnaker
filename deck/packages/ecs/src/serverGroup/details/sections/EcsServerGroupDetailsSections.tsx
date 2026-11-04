import React from 'react';

import type { IServerGroupDetailsSectionProps } from '@spinnaker/core';
import { CollapsibleSection, FirewallLabels, HealthCounts } from '@spinnaker/core';

export function EcsDeploymentSection({ serverGroup }: IServerGroupDetailsSectionProps) {
  const sg = serverGroup as any;
  const taskDefinitionRevision = sg.taskDefinitionRevision;
  const rolloutState: string | undefined = sg.rolloutState;
  const rolloutStateReason: string | undefined = sg.rolloutStateReason;
  const deploymentId: string | undefined = sg.deploymentId;

  // These come from the server-group details payload, which clouddriver serializes from the full
  // EcsServerGroup (rollout fields flattened via @JsonAnyGetter). The values are cached, but the
  // ecs-native deploy stage runs a forceCacheRefresh AFTER ECS reports the rollout COMPLETED, so
  // the cache is fresh by the time a deploy finishes rather than lagging a caching cycle.

  // Omit the section entirely when there's nothing ECS-deployment-specific to show (e.g. a classic
  // ecs service with no revision and no rollout state).
  if (taskDefinitionRevision == null && rolloutState == null) {
    return null;
  }

  return (
    <CollapsibleSection heading="Deployment (ECS)" defaultExpanded={true}>
      <dl className="dl-horizontal dl-narrow">
        {taskDefinitionRevision != null && (
          <>
            <dt>Task Def Revision</dt>
            <dd>{taskDefinitionRevision}</dd>
          </>
        )}
        {rolloutState != null && (
          <>
            <dt>Rollout State</dt>
            <dd>{rolloutState}</dd>
          </>
        )}
        {rolloutStateReason != null && (
          <>
            <dt>Reason</dt>
            <dd>{rolloutStateReason}</dd>
          </>
        )}
        {deploymentId != null && (
          <>
            <dt>Deployment ID</dt>
            <dd>{deploymentId}</dd>
          </>
        )}
      </dl>
    </CollapsibleSection>
  );
}

export function EcsTaskDefinitionSection({ serverGroup }: IServerGroupDetailsSectionProps) {
  const taskDefinition = (serverGroup as any).taskDefinition || {};
  return (
    <CollapsibleSection heading="Task Definition" defaultExpanded={true}>
      <dl className="dl-horizontal dl-narrow">
        <dt>Task Name</dt>
        <dd>{taskDefinition.taskName}</dd>
        <dt>Container URL</dt>
        <dd>{taskDefinition.containerImage}</dd>
        <dt>Container IAM Profile</dt>
        <dd>{taskDefinition.iamRole}</dd>
        {taskDefinition.containerPort != null && (
          <>
            <dt>Container Port</dt>
            <dd>{taskDefinition.containerPort}</dd>
          </>
        )}
        <dt>Container CPU Units</dt>
        <dd>{taskDefinition.cpuUnits}</dd>
        {taskDefinition.memoryReservation != null && (
          <>
            <dt>Container Reserved Memory</dt>
            <dd>{taskDefinition.memoryReservation} MB</dd>
          </>
        )}
        {taskDefinition.memoryLimit != null && (
          <>
            <dt>Container Memory Limit</dt>
            <dd>{taskDefinition.memoryLimit} MB</dd>
          </>
        )}
      </dl>
    </CollapsibleSection>
  );
}

export function EcsEnvironmentVariablesSection({ serverGroup }: IServerGroupDetailsSectionProps) {
  const environmentVariables = (serverGroup as any).taskDefinition?.environmentVariables || [];
  return (
    <CollapsibleSection heading="Environment Variables">
      {environmentVariables.length === 0 ? (
        <div>This server group has no environment variables</div>
      ) : (
        <dl className="dl-horizontal dl-narrow">
          {environmentVariables.map((variable: any) => (
            <React.Fragment key={variable.name}>
              <dt>{variable.name}</dt>
              <dd>{variable.value}</dd>
            </React.Fragment>
          ))}
        </dl>
      )}
    </CollapsibleSection>
  );
}

export function EcsHealthSection({ serverGroup }: IServerGroupDetailsSectionProps) {
  if (!serverGroup.instanceCounts || serverGroup.instanceCounts.total <= 0) {
    return null;
  }
  return (
    <CollapsibleSection heading="Health" defaultExpanded={true}>
      <dl className="dl-horizontal dl-narrow">
        <dt>Instances</dt>
        <dd>
          <HealthCounts container={serverGroup.instanceCounts} className="pull-left" />
        </dd>
      </dl>
    </CollapsibleSection>
  );
}

export function EcsFirewallsSection({ serverGroup }: IServerGroupDetailsSectionProps) {
  const securityGroups = (serverGroup as any).securityGroups || [];
  return (
    <CollapsibleSection heading={FirewallLabels.get('Firewalls')}>
      <ul>
        {securityGroups.map((securityGroup: string) => (
          <li key={securityGroup}>{securityGroup}</li>
        ))}
      </ul>
    </CollapsibleSection>
  );
}

export function EcsCapacitySection({ serverGroup }: IServerGroupDetailsSectionProps) {
  const capacity = (serverGroup as any).capacity || {};
  return (
    <CollapsibleSection heading="Capacity">
      <dl className="dl-horizontal dl-narrow">
        <dt>Current</dt>
        <dd>{serverGroup.instances?.length || 0}</dd>
        <dt>Desired</dt>
        <dd>{capacity.desired}</dd>
        <dt>Min</dt>
        <dd>{capacity.min}</dd>
        <dt>Max</dt>
        <dd>{capacity.max}</dd>
      </dl>
    </CollapsibleSection>
  );
}

export function EcsScalingPoliciesSection({ serverGroup }: IServerGroupDetailsSectionProps) {
  const metricAlarms = (serverGroup as any).metricAlarms || [];
  return (
    <CollapsibleSection heading="Scaling Policies">
      {metricAlarms.length ? (
        <ul>
          {metricAlarms.map((alarm: string) => (
            <li key={alarm}>{alarm}</li>
          ))}
        </ul>
      ) : (
        <i>There are no scaling policies assigned.</i>
      )}
    </CollapsibleSection>
  );
}

function getBuildLink(buildInfo: any): string {
  if (buildInfo.buildInfoUrl) {
    return buildInfo.buildInfoUrl;
  }
  if (buildInfo.jenkins) {
    return `${buildInfo.jenkins.host.replace(/\/?$/, '/')}job/${buildInfo.jenkins.name}/${buildInfo.jenkins.number}`;
  }
  return null;
}

export function EcsBuildInfoSection({ serverGroup }: IServerGroupDetailsSectionProps) {
  const buildInfo = (serverGroup as any).buildInfo;
  if (!buildInfo) {
    return null;
  }
  const buildLink = getBuildLink(buildInfo);
  return (
    <CollapsibleSection heading="Build Data">
      <dl className="dl-horizontal dl-narrow">
        {buildInfo.jenkins && (
          <>
            <dt>Job</dt>
            <dd>{buildInfo.jenkins.name}</dd>
            <dt>Build</dt>
            <dd>{buildInfo.jenkins.number}</dd>
          </>
        )}
        <dt>Package</dt>
        <dd>{buildInfo.package_name}</dd>
        <dt>Commit</dt>
        <dd>{buildInfo.commit?.substring(0, 8)}</dd>
        <dt>Version</dt>
        <dd>{buildInfo.version}</dd>
        {buildLink && (
          <>
            <dt>Build Link</dt>
            <dd>
              <a target="_blank" rel="noopener noreferrer" href={buildLink}>
                {buildLink}
              </a>
            </dd>
          </>
        )}
      </dl>
    </CollapsibleSection>
  );
}
