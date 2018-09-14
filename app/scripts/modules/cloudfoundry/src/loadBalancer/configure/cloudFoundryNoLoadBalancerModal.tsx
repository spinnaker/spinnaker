import { IDeferred } from 'angular';

import * as React from 'react';

import { ILoadBalancerModalProps, WizardModal, ReactModal, noop } from '@spinnaker/core';
import { ICloudFoundryLoadBalancerUpsertCommand } from 'cloudfoundry/domain/ICloudFoundryLoadBalancer';
import { NoLoadBalancerDetails } from 'cloudfoundry/loadBalancer/configure/noLoadBalancer';
import { CfDisclaimerPage } from 'cloudfoundry/common/wizard/sections/cfDisclaimer.cf';
import { IModalServiceInstance } from 'angular-ui-bootstrap';
import { $q } from 'ngimport';

type NoLoadBalancerCreateModal = new () => WizardModal<ICloudFoundryLoadBalancerUpsertCommand>;
const NoLoadBalancerCreateModal = WizardModal as NoLoadBalancerCreateModal;

export interface ICreateCloudFoundryNoLoadBalancerState {}

export class CloudFoundryNoLoadBalancerModal extends React.Component<
  ILoadBalancerModalProps,
  ICreateCloudFoundryNoLoadBalancerState
> {
  private refreshUnsubscribe: () => void;
  private $uibModalInstanceEmulation: IModalServiceInstance & { deferred?: IDeferred<any> };

  public static show(props: ILoadBalancerModalProps): Promise<void> {
    const modalProps = { dialogClassName: 'wizard-modal modal-lg' };
    return ReactModal.show(
      CloudFoundryNoLoadBalancerModal,
      {
        ...props,
        className: 'create-pipeline-modal-overflow-visible',
      },
      modalProps,
    );
  }

  constructor(props: ILoadBalancerModalProps) {
    super(props);

    const deferred = $q.defer();
    const promise = deferred.promise;
    this.$uibModalInstanceEmulation = {
      result: promise,
      close: () => this.props.dismissModal(),
      dismiss: () => this.props.dismissModal(),
    } as IModalServiceInstance;
    Object.assign(this.$uibModalInstanceEmulation, { deferred });
  }

  public componentWillUnmount(): void {
    if (this.refreshUnsubscribe) {
      this.refreshUnsubscribe();
    }
  }

  protected onApplicationRefresh(): void {
    this.refreshUnsubscribe = undefined;
    this.props.dismissModal();
  }

  private submit = (): void => {
    this.props.dismissModal();
  };

  public render() {
    const firstValues = {};

    return (
      <NoLoadBalancerCreateModal
        heading="Create a load balancer"
        initialValues={firstValues}
        taskMonitor={noop}
        dismissModal={this.props.dismissModal}
        closeModal={this.submit}
        submitButtonLabel={'Ok'}
        validate={noop}
      >
        <NoLoadBalancerDetails />
        <CfDisclaimerPage />
      </NoLoadBalancerCreateModal>
    );
  }
}
