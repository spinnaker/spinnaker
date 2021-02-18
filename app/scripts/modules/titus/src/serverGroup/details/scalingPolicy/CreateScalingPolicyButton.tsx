import React from 'react';

import { PolicyTypeSelectionModal } from '@spinnaker/amazon';
import { AccountService, Application, IServerGroup, ModalInjector } from '@spinnaker/core';

import { TitusReactInjector } from '../../../reactShims';
import { UpsertTargetTrackingController } from './targetTracking/upsertTargetTracking.controller';

export interface ICreateScalingPolicyButtonProps {
  application: Application;
  serverGroup: IServerGroup;
}

export interface ICreateScalingPolicyButtonState {
  showSelection: boolean;
  showModal: boolean;
  typeSelection: string;
  awsAccount: string;
}

export class CreateScalingPolicyButton extends React.Component<
  ICreateScalingPolicyButtonProps,
  ICreateScalingPolicyButtonState
> {
  constructor(props: ICreateScalingPolicyButtonProps) {
    super(props);
    this.state = {
      showSelection: false,
      showModal: false,
      typeSelection: null,
      awsAccount: null,
    };
    AccountService.getAccountDetails(props.serverGroup.account).then((details) => {
      this.setState({ awsAccount: details.awsAccount });
    });
  }

  public handleClick = (): void => {
    this.setState({ showSelection: true });
  };

  public createStepPolicy(): void {
    const { serverGroup, application } = this.props;

    ModalInjector.modalService
      .open({
        templateUrl: require('./upsert/upsertScalingPolicy.modal.html'),
        controller: 'titusUpsertScalingPolicyCtrl',
        controllerAs: 'ctrl',
        size: 'lg',
        resolve: {
          policy: () =>
            TitusReactInjector.titusServerGroupTransformer.constructNewStepScalingPolicyTemplate(serverGroup),
          serverGroup: () => serverGroup,
          alarmServerGroup: () => ({
            type: 'aws',
            account: this.state.awsAccount,
            region: serverGroup.region,
            name: serverGroup.name,
          }),
          application: () => application,
        },
      })
      .result.catch(() => {});
  }

  public createTargetTrackingPolicy(): void {
    const { serverGroup, application } = this.props;

    ModalInjector.modalService
      .open({
        templateUrl: require('./targetTracking/upsertTargetTracking.modal.html'),
        controller: UpsertTargetTrackingController,
        controllerAs: '$ctrl',
        size: 'lg',
        resolve: {
          policy: () =>
            TitusReactInjector.titusServerGroupTransformer.constructNewTargetTrackingPolicyTemplate(serverGroup),
          serverGroup: () => serverGroup,
          alarmServerGroup: () => ({
            type: 'aws',
            account: this.state.awsAccount,
            region: serverGroup.region,
            name: serverGroup.name,
          }),
          application: () => application,
        },
      })
      .result.catch(() => {});
  }

  public typeSelected = (typeSelection: string): void => {
    this.setState({ typeSelection, showSelection: false, showModal: true });
    if (typeSelection === 'step') {
      this.createStepPolicy();
    }
    if (typeSelection === 'targetTracking') {
      this.createTargetTrackingPolicy();
    }
  };

  public showModalCallback = (): void => {
    this.setState({ showSelection: false, showModal: false, typeSelection: null });
  };

  public render() {
    const { min, max } = this.props.serverGroup.capacity;
    return (
      <div>
        {this.state.awsAccount ? (
          <a className="clickable" onClick={this.handleClick}>
            Create new scaling policy
          </a>
        ) : null}
        {this.state.showSelection && (
          <PolicyTypeSelectionModal
            warnOnMinMaxCapacity={min === max}
            typeSelectedCallback={this.typeSelected}
            showCallback={this.showModalCallback}
          />
        )}
      </div>
    );
  }
}
