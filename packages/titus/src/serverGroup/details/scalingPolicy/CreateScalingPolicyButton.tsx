import React from 'react';

import { PolicyTypeSelectionModal } from '@spinnaker/amazon';
import type { Application, IServerGroup } from '@spinnaker/core';
import { AccountService, ReactModal } from '@spinnaker/core';

import { TitusReactInjector } from '../../../reactShims';
import type { IUpsertTargetTrackingModalProps } from './targetTracking/UpsertTargetTrackingModal';
import { UpsertTargetTrackingModal } from './targetTracking/UpsertTargetTrackingModal';
import type { IUpsertScalingPolicyModalProps } from './upsert/UpsertScalingPolicyModal';
import { UpsertScalingPolicyModal } from './upsert/UpsertScalingPolicyModal';

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

    const upsertProps = {
      app: application,
      policy: TitusReactInjector.titusServerGroupTransformer.constructNewStepScalingPolicyTemplate(serverGroup),
      serverGroup,
    } as IUpsertScalingPolicyModalProps;
    const modalProps = { dialogClassName: 'wizard-modal modal-lg' };
    ReactModal.show(UpsertScalingPolicyModal, upsertProps, modalProps);
  }

  public createTargetTrackingPolicy(): void {
    const { serverGroup, application } = this.props;
    const upsertProps = {
      app: application,
      policy: TitusReactInjector.titusServerGroupTransformer.constructNewTargetTrackingPolicyTemplate(serverGroup),
      serverGroup,
    } as IUpsertTargetTrackingModalProps;
    const modalProps = { dialogClassName: 'wizard-modal modal-lg' };
    ReactModal.show(UpsertTargetTrackingModal, upsertProps, modalProps);
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
