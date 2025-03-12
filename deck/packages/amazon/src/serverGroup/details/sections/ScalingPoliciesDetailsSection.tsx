import React from 'react';

import { CollapsibleSection, Overridable, Tooltip } from '@spinnaker/core';

import type { IAmazonServerGroupDetailsSectionProps } from './IAmazonServerGroupDetailsSectionProps';
import { AWSProviderSettings } from '../../../aws.settings';
import type { IAmazonServerGroupView, IScalingProcess } from '../../../domain';
import { CreateScalingPolicyButton } from '../scalingPolicy/CreateScalingPolicyButton';
import { ScalingPolicySummary } from '../scalingPolicy/ScalingPolicySummary';
import { AutoScalingProcessService } from '../scalingProcesses/AutoScalingProcessService';

@Overridable('aws.serverGroup.ScalingPoliciesDetailsSection')
export class ScalingPoliciesDetailsSection extends React.Component<IAmazonServerGroupDetailsSectionProps> {
  constructor(props: IAmazonServerGroupDetailsSectionProps) {
    super(props);
  }

  public static arePoliciesDisabled(serverGroup: IAmazonServerGroupView): boolean {
    const autoScalingProcesses: IScalingProcess[] = AutoScalingProcessService.normalizeScalingProcesses(serverGroup);
    return (
      serverGroup.scalingPolicies.length > 0 &&
      autoScalingProcesses
        .filter((p) => !p.enabled)
        .some((p) => ['Launch', 'Terminate', 'AlarmNotification'].includes(p.name))
    );
  }

  public render(): JSX.Element {
    const { app, serverGroup } = this.props;
    const scalingPoliciesDisabled = ScalingPoliciesDetailsSection.arePoliciesDisabled(serverGroup);

    return (
      <CollapsibleSection
        cacheKey="Scaling Policies"
        heading={({ chevron }) => (
          <h4 className="collapsible-heading">
            {chevron}
            <span>
              {scalingPoliciesDisabled && (
                <Tooltip value="Some scaling processes are disabled that may prevent scaling policies from working">
                  <span className="fa fa-exclamation-circle warning-text" />
                </Tooltip>
              )}
              Scaling Policies
            </span>
          </h4>
        )}
      >
        {scalingPoliciesDisabled && (
          <div className="band band-warning">
            Some scaling processes are disabled that may prevent scaling policies from working.
          </div>
        )}
        {serverGroup.scalingPolicies.map((policy) => (
          <ScalingPolicySummary key={policy.policyARN} policy={policy} serverGroup={serverGroup} application={app} />
        ))}
        {AWSProviderSettings.adHocInfraWritesEnabled ? (
          <CreateScalingPolicyButton serverGroup={serverGroup} application={app} />
        ) : (
          <p>Can not create scaling policy, because ad-hoc operations are disabled for AWS providers.</p>
        )}
      </CollapsibleSection>
    );
  }
}
