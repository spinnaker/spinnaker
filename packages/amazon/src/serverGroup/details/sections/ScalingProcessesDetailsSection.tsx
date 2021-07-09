import React from 'react';

import { CollapsibleSection, confirmNotManaged, HelpField, ModalInjector, timestamp, Tooltip } from '@spinnaker/core';

import { IAmazonServerGroupDetailsSectionProps } from './IAmazonServerGroupDetailsSectionProps';
import { IScalingProcess } from '../../../domain';
import { AutoScalingProcessService } from '../scalingProcesses/AutoScalingProcessService';

export interface IScalingProcessesDetailsSectionState {
  autoScalingProcesses: IScalingProcess[];
  scalingPoliciesDisabled: boolean;
  scheduledActionsDisabled: boolean;
}

export class ScalingProcessesDetailsSection extends React.Component<
  IAmazonServerGroupDetailsSectionProps,
  IScalingProcessesDetailsSectionState
> {
  constructor(props: IAmazonServerGroupDetailsSectionProps) {
    super(props);

    this.state = this.getState(props);
  }

  private toggleScalingProcesses = (): void => {
    const { app, serverGroup } = this.props;
    confirmNotManaged(serverGroup, app).then(
      (isNotManaged) =>
        isNotManaged &&
        ModalInjector.modalService.open({
          templateUrl: require('../scalingProcesses/modifyScalingProcesses.html'),
          controller: 'ModifyScalingProcessesCtrl as ctrl',
          resolve: {
            serverGroup: () => serverGroup,
            application: () => app,
            processes: () => this.state.autoScalingProcesses,
          },
        }),
    );
  };

  private getState(props: IAmazonServerGroupDetailsSectionProps): IScalingProcessesDetailsSectionState {
    const { serverGroup } = props;

    const autoScalingProcesses: IScalingProcess[] = AutoScalingProcessService.normalizeScalingProcesses(serverGroup);

    const scalingPoliciesDisabled =
      serverGroup.scalingPolicies.length > 0 &&
      autoScalingProcesses
        .filter((p) => !p.enabled)
        .some((p) => ['Launch', 'Terminate', 'AlarmNotification'].includes(p.name));
    const scheduledActionsDisabled =
      serverGroup.scheduledActions.length > 0 &&
      autoScalingProcesses
        .filter((p) => !p.enabled)
        .some((p) => ['Launch', 'Terminate', 'ScheduledAction'].includes(p.name));

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
        heading={({ chevron }) => (
          <h4 className="collapsible-heading">
            {chevron}
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
          </h4>
        )}
      >
        <ul className="scaling-processes">
          {autoScalingProcesses.map((process) => (
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
