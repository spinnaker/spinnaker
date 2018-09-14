import { IDeferred } from 'angular';

import * as React from 'react';

import { IModalServiceInstance } from 'angular-ui-bootstrap';
import { FormikErrors } from 'formik';
import { cloneDeep } from 'lodash';
import { $q } from 'ngimport';

import {
  ILoadBalancerModalProps,
  WizardModal,
  TaskMonitor,
  LoadBalancerWriter,
  ReactModal,
  noop,
} from '@spinnaker/core';

import { ICloudFoundryLoadBalancer, ICloudFoundryLoadBalancerUpsertCommand } from 'cloudfoundry/domain';
import { CloudFoundryReactInjector } from 'cloudfoundry/reactShims';
import { LoadBalancerDetails } from 'cloudfoundry/loadBalancer/configure/loadBalancerDetails';

type LoadBalancerCreateModal = new () => WizardModal<ICloudFoundryLoadBalancerUpsertCommand>;
const LoadBalancerCreateModal = WizardModal as LoadBalancerCreateModal;

export interface ICreateCloudFoundryLoadBalancerProps extends ILoadBalancerModalProps {
  loadBalancer: ICloudFoundryLoadBalancer;
}

export interface ICreateCloudFoundryLoadBalancerState {
  isNew: boolean;
  loadBalancerCommand: ICloudFoundryLoadBalancerUpsertCommand;
  taskMonitor: TaskMonitor;
}

export class CloudFoundryLoadBalancerCreateModal extends React.Component<
  ICreateCloudFoundryLoadBalancerProps,
  ICreateCloudFoundryLoadBalancerState
> {
  public static defaultProps: Partial<ICreateCloudFoundryLoadBalancerProps> = {
    closeModal: noop,
    dismissModal: noop,
  };

  private refreshUnsubscribe: () => void;
  private $uibModalInstanceEmulation: IModalServiceInstance & { deferred?: IDeferred<any> };

  public static show(props: ICreateCloudFoundryLoadBalancerProps): Promise<void> {
    const modalProps = { dialogClassName: 'wizard-modal modal-lg' };
    return ReactModal.show(
      CloudFoundryLoadBalancerCreateModal,
      {
        ...props,
        className: 'create-pipeline-modal-overflow-visible',
      },
      modalProps,
    );
  }

  constructor(props: ICreateCloudFoundryLoadBalancerProps) {
    super(props);

    const loadBalancerCommand = CloudFoundryReactInjector.cfLoadBalancerTransformer.constructNewCloudFoundryLoadBalancerTemplate(
      props.app,
    );

    this.state = {
      isNew: true,
      loadBalancerCommand,
      taskMonitor: null,
    };

    const deferred = $q.defer();
    const promise = deferred.promise;
    this.$uibModalInstanceEmulation = {
      result: promise,
      close: () => this.props.dismissModal(),
      dismiss: () => this.props.dismissModal(),
    } as IModalServiceInstance;
    Object.assign(this.$uibModalInstanceEmulation, { deferred });
  }

  private dismiss = (): void => {
    this.props.dismissModal();
  };

  public componentWillUnmount(): void {
    if (this.refreshUnsubscribe) {
      this.refreshUnsubscribe();
    }
  }

  private onTaskComplete(): void {
    this.props.app.loadBalancers.refresh();
    this.refreshUnsubscribe = this.props.app.loadBalancers.onNextRefresh(null, () => this.onApplicationRefresh());
  }

  protected onApplicationRefresh(): void {
    this.refreshUnsubscribe = undefined;
    this.props.dismissModal();
    this.setState({ taskMonitor: undefined });
  }

  private submit = (values: ICloudFoundryLoadBalancerUpsertCommand): void => {
    const { app } = this.props;
    const { isNew } = this.state;

    const descriptor = isNew ? 'Create' : 'Update';
    const loadBalancerCommandFormatted = cloneDeep(values);
    const taskMonitor = new TaskMonitor({
      application: app,
      title: `${isNew ? 'Creating' : 'Updating'} your load balancer`,
      modalInstance: this.$uibModalInstanceEmulation,
      onTaskComplete: () => this.onTaskComplete(),
    });

    taskMonitor.submit(() => {
      return LoadBalancerWriter.upsertLoadBalancer(loadBalancerCommandFormatted, app, descriptor);
    });

    this.setState({ taskMonitor });
  };

  private validate = (): FormikErrors<ICloudFoundryLoadBalancerUpsertCommand> => {
    return {} as FormikErrors<ICloudFoundryLoadBalancerUpsertCommand>;
  };

  public render() {
    const { app, forPipelineConfig } = this.props;
    const { isNew, loadBalancerCommand, taskMonitor } = this.state;
    const hideSections = new Set<string>();

    return (
      <LoadBalancerCreateModal
        heading="Create a load balancer"
        initialValues={loadBalancerCommand}
        taskMonitor={taskMonitor}
        dismissModal={this.dismiss}
        closeModal={this.submit}
        submitButtonLabel={forPipelineConfig ? (isNew ? 'Add' : 'Done') : isNew ? 'Create' : 'Update'}
        validate={this.validate}
        hideSections={hideSections}
      >
        <LoadBalancerDetails app={app} isNew={isNew} />
      </LoadBalancerCreateModal>
    );
  }
}
