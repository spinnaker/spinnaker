import React from 'react';

import { ScalingPolicySummary } from '@spinnaker/amazon';
import { AccountTag, CollapsibleSection, HealthCounts, timestamp } from '@spinnaker/core';

import { TitusCapacityDetailsSection } from './TitusCapacityDetailsSection';
import { TitusLaunchConfigSection } from './TitusLaunchConfigSection';
import { TitusPackageDetailsSection } from './TitusPackageDetailsSection';
import { DisruptionBudgetSection } from './disruptionBudget/DisruptionBudgetSection';
import { CreateScalingPolicyButton } from './scalingPolicy/CreateScalingPolicyButton';
import { TitusCustomScalingPolicy } from './scalingPolicy/TitusCustomScalingPolicy';
import type { ITitusServerGroupDetailsSectionProps } from './sections/ITitusServerGroupDetailsSectionProps';
import { ServiceJobProcessesSection } from './serviceJobProcesses/ServiceJobProcessesSection';

export function TitusServerGroupInformationSection({ serverGroup }: ITitusServerGroupDetailsSectionProps) {
  return (
    <CollapsibleSection heading="Server Group Information" defaultExpanded={true}>
      <dl className="dl-horizontal dl-narrow">
        <dt>Created</dt>
        <dd>{timestamp(serverGroup.createdTime)}</dd>
        <dt>In</dt>
        <dd>
          <AccountTag account={serverGroup.account} /> {serverGroup.region}
        </dd>
        <dt>Job Id</dt>
        <dd>
          <a href={`${(serverGroup as any).titusUiEndpoint}jobs/${serverGroup.id}`} target="_blank">
            {serverGroup.id}
          </a>
        </dd>
      </dl>
    </CollapsibleSection>
  );
}

export function TitusCapacitySection(props: ITitusServerGroupDetailsSectionProps) {
  return (
    <CollapsibleSection heading="Capacity" defaultExpanded={true}>
      <TitusCapacityDetailsSection {...props} />
    </CollapsibleSection>
  );
}

export function TitusHealthSection({ serverGroup }: ITitusServerGroupDetailsSectionProps) {
  if (!serverGroup.instanceCounts || serverGroup.instanceCounts.total <= 0) {
    return null;
  }
  return (
    <CollapsibleSection heading="Health" defaultExpanded={true}>
      <dl className="dl-horizontal dl-narrow">
        <dt>Tasks</dt>
        <dd>
          <HealthCounts container={serverGroup.instanceCounts} />
        </dd>
      </dl>
    </CollapsibleSection>
  );
}

export function TitusLaunchConfigurationSection({ serverGroup }: ITitusServerGroupDetailsSectionProps) {
  return (
    <CollapsibleSection heading="Launch Configuration">
      <TitusLaunchConfigSection serverGroup={serverGroup} />
    </CollapsibleSection>
  );
}

export function TitusServiceJobProcessesDetailsSection(props: ITitusServerGroupDetailsSectionProps) {
  return (
    <CollapsibleSection heading="Service Job Processes">
      <ServiceJobProcessesSection {...props} />
    </CollapsibleSection>
  );
}

export function TitusScalingPoliciesSection({ app, serverGroup }: ITitusServerGroupDetailsSectionProps) {
  return (
    <CollapsibleSection heading="Scaling Policies">
      {(serverGroup.scalingPolicies || []).map((policy: any) => (
        <ScalingPolicySummary
          key={policy.id || policy.policyARN || policy.policyName}
          policy={policy}
          serverGroup={serverGroup}
          application={app}
        />
      ))}
      <CreateScalingPolicyButton serverGroup={serverGroup} application={app} />
      <TitusCustomScalingPolicy serverGroup={serverGroup} application={app} />
    </CollapsibleSection>
  );
}

export function TitusPackageSection({ serverGroup }: ITitusServerGroupDetailsSectionProps) {
  return <TitusPackageDetailsSection buildInfo={(serverGroup as any).buildInfo} />;
}

export function TitusDisruptionBudgetDetailsSection(props: ITitusServerGroupDetailsSectionProps) {
  return (
    <CollapsibleSection heading="Job Disruption Budget">
      <DisruptionBudgetSection {...props} />
    </CollapsibleSection>
  );
}

export function TitusJobAttributesSection({ serverGroup }: ITitusServerGroupDetailsSectionProps) {
  const labels = (serverGroup as any).displayLabels;
  return (
    <CollapsibleSection heading="Job Attributes">
      {!labels && <div>No job attributes associated with this server group</div>}
      {labels && (
        <dl>
          {Object.keys(labels).map((key) => (
            <React.Fragment key={key}>
              <dt>{key}</dt>
              <dd>{labels[key]}</dd>
            </React.Fragment>
          ))}
        </dl>
      )}
    </CollapsibleSection>
  );
}

export function TitusContainerAttributesSection({ serverGroup }: ITitusServerGroupDetailsSectionProps) {
  return (
    <KeyValueSection
      heading="Container Attributes"
      values={serverGroup.containerAttributes}
      empty="No container attributes associated with this server group"
    />
  );
}

export function TitusEnvironmentVariablesSection({ serverGroup }: ITitusServerGroupDetailsSectionProps) {
  return (
    <KeyValueSection
      heading="Environment Variables"
      values={serverGroup.env}
      empty="No environment variables associated with this server group"
    />
  );
}

function KeyValueSection({
  heading,
  values,
  empty,
}: {
  heading: string;
  values: { [key: string]: string };
  empty: string;
}) {
  return (
    <CollapsibleSection heading={heading}>
      {!values && <div>{empty}</div>}
      {values && (
        <dl>
          {Object.keys(values).map((key) => (
            <React.Fragment key={key}>
              <dt>{key}</dt>
              <dd>{values[key]}</dd>
            </React.Fragment>
          ))}
        </dl>
      )}
    </CollapsibleSection>
  );
}
