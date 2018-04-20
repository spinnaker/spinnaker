import * as React from 'react';
import { BindAll } from 'lodash-decorators';

import { CollapsibleSection, HelpField, ModalInjector, Tooltip, timestamp } from '@spinnaker/core';

import { IScalingProcess } from 'amazon/domain';
import { AwsReactInjector } from 'amazon/reactShims';

import { IAmazonServerGroupDetailsSectionProps } from './IAmazonServerGroupDetailsSectionProps';

export interface IScalingProcessesDetailsSectionState {
  autoScalingProcesses: IScalingProcess[];
  scalingPoliciesDisabled: boolean;
  scheduledActionsDisabled: boolean;
}

@BindAll()
export class ScalingProcessesDetailsSection extends React.Component<
  IAmazonServerGroupDetailsSectionProps,
  IScalingProcessesDetailsSectionState
> {
  constructor(props: IAmazonServerGroupDetailsSectionProps) {
    super(props);

    this.state = this.getState(props);
  }

  private toggleScalingProcesses(): void {
    ModalInjector.modalService.open({
      templateUrl: require('../scalingProcesses/modifyScalingProcesses.html'),
      controller: 'ModifyScalingProcessesCtrl as ctrl',
      resolve: {
        serverGroup: () => this.props.serverGroup,
        application: () => this.props.app,
        processes: () => this.state.autoScalingProcesses,
      },
    });
  }

  private getState(props: IAmazonServerGroupDetailsSectionProps): IScalingProcessesDetailsSectionState {
    const { serverGroup } = props;

    const autoScalingProcesses: IScalingProcess[] = AwsReactInjector.autoScalingProcessService.normalizeScalingProcesses(
      serverGroup,
    );

    const scalingPoliciesDisabled =
      serverGroup.scalingPolicies.length > 0 &&
      autoScalingProcesses
        .filter(p => !p.enabled)
        .some(p => ['Launch', 'Terminate', 'AlarmNotification'].includes(p.name));
    const scheduledActionsDisabled =
      serverGroup.scheduledActions.length > 0 &&
      autoScalingProcesses
        .filter(p => !p.enabled)
        .some(p => ['Launch', 'Terminate', 'ScheduledAction'].includes(p.name));

    return { autoScalingProcesses, scalingPoliciesDisabled, scheduledActionsDisabled };
  }

  public componentWillReceiveProps(nextProps: IAmazonServerGroupDetailsSectionProps): void {
    this.setState(this.getState(nextProps));
  }

  public render(): JSX.Element {
    const { autoScalingProcesses, scalingPoliciesDisabled, scheduledActionsDisabled } = this.state;

    return (
      <CollapsibleSection
        cacheKey="Scaling Processes"
        heading={() => (
          <span>
            {scalingPoliciesDisabled && (
              <Tooltip value="Some scaling processes are disabled that may prevent scaling policies from working">
                <span className="fa fa-exclamation-circle warning-text" />
              </Tooltip>
            )}
            {scheduledActionsDisabled && (
              <Tooltip value="Some scaling processes are disabled that may prevent scheduled actions from working">
                <span className="fa fa-exclamation-circle warning-text" />
              </Tooltip>
            )}
            Scaling Processes
          </span>
        )}
      >
        <ul className="scaling-processes">
          {autoScalingProcesses.map(process => (
            <li key={process.name}>
              <span style={{ visibility: process.enabled ? 'visible' : 'hidden' }} className="fa fa-check small" />
              <span className={!process.enabled ? 'text-disabled' : ''}>{process.name} </span>
              <HelpField content={process.description} placement="bottom" />
              {process.suspensionDate && (
                <div className="text-disabled small" style={{ marginLeft: '35px' }}>
                  Suspended {timestamp(process.suspensionDate)}
                </div>
              )}
            </li>
          ))}
        </ul>
        <a className="clickable" onClick={this.toggleScalingProcesses}>
          Edit Scaling Processes
        </a>
      </CollapsibleSection>
    );
  }
}
