import React from 'react';

import { Application, ModalInjector } from '@spinnaker/core';
import { IAmazonServerGroupView } from '../../../domain';
import { AwsReactInjector } from '../../../reactShims';

import { UpsertTargetTrackingController } from './targetTracking/upsertTargetTracking.controller';
import { PolicyTypeSelectionModal } from './upsert/PolicyTypeSelectionModal';

export interface ICreateScalingPolicyButtonProps {
  application: Application;
  serverGroup: IAmazonServerGroupView;
}

export interface ICreateScalingPolicyButtonState {
  showSelection: boolean;
  showModal: boolean;
  typeSelection: string;
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
    };
  }

  public handleClick = (): void => {
    this.setState({ showSelection: true });
  };

  public createStepPolicy(): void {
    const { serverGroup, application } = this.props;

    ModalInjector.modalService
      .open({
        templateUrl: require('./upsert/upsertScalingPolicy.modal.html'),
        controller: 'awsUpsertScalingPolicyCtrl',
        controllerAs: 'ctrl',
        size: 'lg',
        resolve: {
          policy: () => AwsReactInjector.awsServerGroupTransformer.constructNewStepScalingPolicyTemplate(serverGroup),
          serverGroup: () => serverGroup,
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
          policy: () => AwsReactInjector.awsServerGroupTransformer.constructNewTargetTrackingPolicyTemplate(),
          serverGroup: () => serverGroup,
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
        <a className="clickable" onClick={this.handleClick}>
          Create new scaling policy
        </a>
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
