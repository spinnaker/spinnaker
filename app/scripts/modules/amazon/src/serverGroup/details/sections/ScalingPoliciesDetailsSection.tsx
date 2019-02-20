import * as React from 'react';

import { CollapsibleSection, Overridable, Tooltip } from '@spinnaker/core';

import { IScalingProcess } from 'amazon/domain';
import { AwsNgReact } from 'amazon/reactShims';
import { AutoScalingProcessService } from '../scalingProcesses/AutoScalingProcessService';

import { IAmazonServerGroupDetailsSectionProps } from './IAmazonServerGroupDetailsSectionProps';
import { CreateScalingPolicyButton } from '../scalingPolicy/CreateScalingPolicyButton';

export interface IScalingPoliciesDetailsSectionState {
  scalingPoliciesDisabled: boolean;
}

@Overridable('aws.serverGroup.ScalingPoliciesDetailsSection')
export class ScalingPoliciesDetailsSection extends React.Component<
  IAmazonServerGroupDetailsSectionProps,
  IScalingPoliciesDetailsSectionState
> {
  constructor(props: IAmazonServerGroupDetailsSectionProps) {
    super(props);

    this.state = this.getState(props);
  }

  private getState(props: IAmazonServerGroupDetailsSectionProps): IScalingPoliciesDetailsSectionState {
    const { serverGroup } = props;

    const autoScalingProcesses: IScalingProcess[] = AutoScalingProcessService.normalizeScalingProcesses(serverGroup);
    const scalingPoliciesDisabled =
      serverGroup.scalingPolicies.length > 0 &&
      autoScalingProcesses
        .filter(p => !p.enabled)
        .some(p => ['Launch', 'Terminate', 'AlarmNotification'].includes(p.name));

    return { scalingPoliciesDisabled };
  }

  public componentWillReceiveProps(nextProps: IAmazonServerGroupDetailsSectionProps): void {
    this.setState(this.getState(nextProps));
  }

  public render(): JSX.Element {
    const { app, serverGroup } = this.props;
    const { scalingPoliciesDisabled } = this.state;

    const { ScalingPolicySummary } = AwsNgReact;

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
        {serverGroup.scalingPolicies.map(policy => (
          <ScalingPolicySummary key={policy.policyARN} policy={policy} serverGroup={serverGroup} application={app} />
        ))}
        <CreateScalingPolicyButton serverGroup={serverGroup} application={app} />
      </CollapsibleSection>
    );
  }
}
