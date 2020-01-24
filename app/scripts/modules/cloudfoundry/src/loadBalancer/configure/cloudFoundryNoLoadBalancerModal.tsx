import { IDeferred } from 'angular';

import React from 'react';

import { ILoadBalancerModalProps, WizardModal, WizardPage, ReactModal, noop } from '@spinnaker/core';
import { ICloudFoundryLoadBalancerUpsertCommand } from 'cloudfoundry/domain/ICloudFoundryLoadBalancer';
import { NoLoadBalancerDetails } from './noLoadBalancer';
import { IModalServiceInstance } from 'angular-ui-bootstrap';
import { $q } from 'ngimport';

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
    const initialValues = {} as ICloudFoundryLoadBalancerUpsertCommand;

    return (
      <WizardModal<ICloudFoundryLoadBalancerUpsertCommand>
        heading="Create a load balancer"
        initialValues={initialValues}
        taskMonitor={noop}
        dismissModal={this.props.dismissModal}
        closeModal={this.submit}
        submitButtonLabel={'Ok'}
        render={({ nextIdx, wizard }) => (
          <>
            <WizardPage
              label="Message"
              wizard={wizard}
              order={nextIdx()}
              render={({ innerRef }) => <NoLoadBalancerDetails ref={innerRef} />}
            />
          </>
        )}
      />
    );
  }
}
