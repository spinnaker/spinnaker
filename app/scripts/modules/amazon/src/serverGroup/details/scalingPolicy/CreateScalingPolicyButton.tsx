import * as React from 'react';
import autoBindMethods from 'class-autobind-decorator';

import { Application, IServerGroup, ReactInjector } from '@spinnaker/core';

import { PolicyTypeSelectionModal } from './upsert/PolicyTypeSelectionModal';
import { UpsertTargetTrackingController } from './targetTracking/upsertTargetTracking.controller';
import { AwsReactInjector } from 'amazon/reactShims';

export interface ICreateScalingPolicyButtonProps {
  application: Application;
  serverGroup: IServerGroup;
}

export interface ICreateScalingPolicyButtonState {
  showSelection: boolean;
  showModal: boolean;
  typeSelection: string;
}

@autoBindMethods
export class CreateScalingPolicyButton extends React.Component<ICreateScalingPolicyButtonProps, ICreateScalingPolicyButtonState> {

  constructor(props: ICreateScalingPolicyButtonProps) {
    super(props);
    this.state = {
      showSelection: false,
      showModal: false,
      typeSelection: null,
    };
  }

  public handleClick(): void {
    this.setState({showSelection: true});
  }

  public createStepPolicy(): void {
    const { serverGroup, application } = this.props;

    ReactInjector.modalService.open({
      templateUrl: require('./upsert/upsertScalingPolicy.modal.html'),
      controller: 'awsUpsertScalingPolicyCtrl',
      controllerAs: 'ctrl',
      size: 'lg',
      resolve: {
        policy: () => AwsReactInjector.awsServerGroupTransformer.constructNewStepScalingPolicyTemplate(serverGroup),
        serverGroup: () => serverGroup,
        application: () => application,
      }
    });
  }

  public createTargetTrackingPolicy(): void {
    const { serverGroup, application } = this.props;

    ReactInjector.modalService.open({
      templateUrl: require('./targetTracking/upsertTargetTracking.modal.html'),
      controller: UpsertTargetTrackingController,
      controllerAs: '$ctrl',
      size: 'lg',
      resolve: {
        policy: () => AwsReactInjector.awsServerGroupTransformer.constructNewTargetTrackingPolicyTemplate(),
        serverGroup: () => serverGroup,
        application: () => application,
      }
    });
  }

  public typeSelected(typeSelection: string): void {
    this.setState({typeSelection, showSelection: false, showModal: true});
    if (typeSelection === 'step') {
      this.createStepPolicy();
    }
    if (typeSelection === 'targetTracking') {
      this.createTargetTrackingPolicy();
    }
  }

  public showModalCallback(): void {
    this.setState({showSelection: false, showModal: false, typeSelection: null});
  }

  public render() {
    return (
      <div>
        <a className="clickable" onClick={this.handleClick}>Create new scaling policy</a>
        { this.state.showSelection && (
          <PolicyTypeSelectionModal typeSelectedCallback={this.typeSelected} showCallback={this.showModalCallback}/>
        )}
      </div>
    );
  }
}
