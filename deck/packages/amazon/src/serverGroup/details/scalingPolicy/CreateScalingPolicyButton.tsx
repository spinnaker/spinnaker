import React from 'react';

import type { Application } from '@spinnaker/core';
import { ReactModal } from '@spinnaker/core';
import type { IAmazonServerGroupView } from '../../../domain';
import { AwsReactInjector } from '../../../reactShims';

import type { IUpsertTargetTrackingModalProps } from './targetTracking/UpsertTargetTrackingModal';
import { UpsertTargetTrackingModal } from './targetTracking/UpsertTargetTrackingModal';
import { PolicyTypeSelectionModal } from './upsert/PolicyTypeSelectionModal';
import type { IUpsertScalingPolicyModalProps } from './upsert/UpsertScalingPolicyModal';
import { UpsertScalingPolicyModal } from './upsert/UpsertScalingPolicyModal';

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

    const upsertProps = {
      app: application,
      policy: AwsReactInjector.awsServerGroupTransformer.constructNewStepScalingPolicyTemplate(serverGroup),
      serverGroup,
    } as IUpsertScalingPolicyModalProps;
    const modalProps = { dialogClassName: 'wizard-modal modal-lg' };
    ReactModal.show(UpsertScalingPolicyModal, upsertProps, modalProps);
  }

  public createTargetTrackingPolicy(): void {
    const { serverGroup, application } = this.props;

    const upsertProps = {
      app: application,
      policy: AwsReactInjector.awsServerGroupTransformer.constructNewTargetTrackingPolicyTemplate(),
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
