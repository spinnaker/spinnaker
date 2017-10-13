import * as React from 'react';
import { BindAll } from 'lodash-decorators';

import { Application, IServerGroup, ReactInjector } from '@spinnaker/core';

// import { PolicyTypeSelectionModal } from './upsert/PolicyTypeSelectionModal';
// import { UpsertTargetTrackingController } from './targetTracking/upsertTargetTracking.controller';
import { TitusReactInjector } from 'titus/reactShims/titus.react.injector';

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

@BindAll()
export class CreateScalingPolicyButton extends React.Component<ICreateScalingPolicyButtonProps, ICreateScalingPolicyButtonState> {

  constructor(props: ICreateScalingPolicyButtonProps) {
    super(props);
    this.state = {
      showSelection: false,
      showModal: false,
      typeSelection: null,
      awsAccount: null,
    };
    ReactInjector.accountService.getAccountDetails(props.serverGroup.account).then((details) => {
      this.setState({awsAccount: details.awsAccount});
    });
  }

  public handleClick(): void {
    this.typeSelected('step');
    // this.setState({showSelection: true});
  }

  public createStepPolicy(): void {
    const { serverGroup, application } = this.props;

    ReactInjector.modalService.open({
      templateUrl: require('./upsert/upsertScalingPolicy.modal.html'),
      controller: 'titusUpsertScalingPolicyCtrl',
      controllerAs: 'ctrl',
      size: 'lg',
      resolve: {
        policy: () => TitusReactInjector.titusServerGroupTransformer.constructNewStepScalingPolicyTemplate(serverGroup),
        serverGroup: () => serverGroup,
        alarmServerGroup: () => ({ type: 'aws', account: this.state.awsAccount, region: serverGroup.region, name: serverGroup.name}),
        application: () => application,
      }
    }).result.catch(() => {});
  }

  // public createTargetTrackingPolicy(): void {
  //   const { serverGroup, application } = this.props;
  //
  //   ReactInjector.modalService.open({
  //     templateUrl: require('./targetTracking/upsertTargetTracking.modal.html'),
  //     controller: UpsertTargetTrackingController,
  //     controllerAs: '$ctrl',
  //     size: 'lg',
  //     resolve: {
  //       policy: () => TitusReactInjector.titusServerGroupTransformer.constructNewTargetTrackingPolicyTemplate(),
  //       serverGroup: () => serverGroup,
  //       application: () => application,
  //     }
  //   }).result.catch(() => {});
  // }

  public typeSelected(typeSelection: string): void {
    this.setState({typeSelection, showSelection: false, showModal: true});
    if (typeSelection === 'step') {
      this.createStepPolicy();
    }
    // if (typeSelection === 'targetTracking') {
    //   this.createTargetTrackingPolicy();
    // }
  }

  // public showModalCallback(): void {
  //   this.setState({showSelection: false, showModal: false, typeSelection: null});
  // }

  public render() {
    return (
      <div>
        {this.state.awsAccount ? <a className="clickable" onClick={this.handleClick}>Create new scaling policy</a> : null}
        {/*{ this.state.showSelection && (*/}
          {/*<PolicyTypeSelectionModal typeSelectedCallback={this.typeSelected} showCallback={this.showModalCallback}/>*/}
        {/*)}*/}
      </div>
    );
  }
}
