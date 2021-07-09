import { FormikErrors } from 'formik';
import { cloneDeep, every, get } from 'lodash';
import React from 'react';

import {
  AccountService,
  ILoadBalancerModalProps,
  LoadBalancerWriter,
  noop,
  ReactInjector,
  ReactModal,
  TaskMonitor,
  WizardModal,
  WizardPage,
} from '@spinnaker/core';

import { NLBAdvancedSettings } from './NLBAdvancedSettings';
import { NLBListeners } from './NLBListeners';
import { TargetGroups } from './TargetGroups';
import { AWSProviderSettings } from '../../../aws.settings';
import { LoadBalancerLocation } from '../common/LoadBalancerLocation';
import { IAmazonNetworkLoadBalancer, IAmazonNetworkLoadBalancerUpsertCommand } from '../../../domain';
import { AwsLoadBalancerTransformer } from '../../loadBalancer.transformer';

import '../common/configure.less';

export interface ICreateNetworkLoadBalancerProps extends ILoadBalancerModalProps {
  loadBalancer: IAmazonNetworkLoadBalancer;
}

export interface ICreateApplicationLoadBalancerState {
  isNew: boolean;
  loadBalancerCommand: IAmazonNetworkLoadBalancerUpsertCommand;
  taskMonitor: TaskMonitor;
}

export class CreateNetworkLoadBalancer extends React.Component<
  ICreateNetworkLoadBalancerProps,
  ICreateApplicationLoadBalancerState
> {
  public static defaultProps: Partial<ICreateNetworkLoadBalancerProps> = {
    closeModal: noop,
    dismissModal: noop,
  };

  private _isUnmounted = false;
  private refreshUnsubscribe: () => void;
  private certificateTypes = get(AWSProviderSettings, 'loadBalancers.certificateTypes', ['iam', 'acm']);

  public static show(props: ICreateNetworkLoadBalancerProps): Promise<IAmazonNetworkLoadBalancerUpsertCommand> {
    const modalProps = { dialogClassName: 'wizard-modal modal-lg' };
    return ReactModal.show(CreateNetworkLoadBalancer, props, modalProps);
  }

  constructor(props: ICreateNetworkLoadBalancerProps) {
    super(props);

    const loadBalancerCommand = props.loadBalancer
      ? AwsLoadBalancerTransformer.convertNetworkLoadBalancerForEditing(props.loadBalancer)
      : AwsLoadBalancerTransformer.constructNewNetworkLoadBalancerTemplate(props.app);

    this.state = {
      isNew: !props.loadBalancer,
      loadBalancerCommand,
      taskMonitor: null,
    };
  }

  protected certificateIdAsARN(
    accountId: string,
    certificateId: string,
    region: string,
    certificateType: string,
  ): string {
    if (
      certificateId &&
      (certificateId.indexOf('arn:aws:iam::') !== 0 || certificateId.indexOf('arn:aws:acm:') !== 0)
    ) {
      // If they really want to enter the ARN...
      if (certificateType === 'iam') {
        return `arn:aws:iam::${accountId}:server-certificate/${certificateId}`;
      }
      if (certificateType === 'acm') {
        return `arn:aws:acm:${region}:${accountId}:certificate/${certificateId}`;
      }
    }
    return certificateId;
  }

  private formatListeners(command: IAmazonNetworkLoadBalancerUpsertCommand): PromiseLike<void> {
    return AccountService.getAccountDetails(command.credentials).then((account) => {
      command.listeners.forEach((listener) => {
        if (listener.protocol === 'TCP') {
          delete listener.sslPolicy;
          listener.certificates = [];
        }
        listener.certificates.forEach((certificate) => {
          certificate.certificateArn = this.certificateIdAsARN(
            account.accountId,
            certificate.name,
            command.region,
            certificate.type || this.certificateTypes[0],
          );
        });
      });
    });
  }

  private setAvailabilityZones(loadBalancerCommand: IAmazonNetworkLoadBalancerUpsertCommand): void {
    const availabilityZones: { [region: string]: string[] } = {};
    availabilityZones[loadBalancerCommand.region] = loadBalancerCommand.regionZones || [];
    loadBalancerCommand.availabilityZones = availabilityZones;
  }

  private addAppName(name: string): string {
    return `${this.props.app.name}-${name}`;
  }

  private manageTargetGroupNames(command: IAmazonNetworkLoadBalancerUpsertCommand): void {
    (command.targetGroups || []).forEach((targetGroupDescription) => {
      targetGroupDescription.name = this.addAppName(targetGroupDescription.name);
    });
    (command.listeners || []).forEach((listenerDescription) => {
      listenerDescription.defaultActions.forEach((actionDescription) => {
        if (actionDescription.targetGroupName) {
          actionDescription.targetGroupName = this.addAppName(actionDescription.targetGroupName);
        }
      });
      (listenerDescription.rules || []).forEach((ruleDescription) => {
        ruleDescription.actions.forEach((actionDescription) => {
          if (actionDescription.targetGroupName) {
            actionDescription.targetGroupName = this.addAppName(actionDescription.targetGroupName);
          }
        });
      });
    });
  }

  private manageRules(command: IAmazonNetworkLoadBalancerUpsertCommand): void {
    command.listeners.forEach((listener) => {
      listener.rules.forEach((rule, index) => {
        // Set the priority in array order, starting with 1
        rule.priority = index + 1;
        // Remove conditions that have no value
        rule.conditions = rule.conditions.filter((condition) => condition.values[0].length > 0);
      });
    });
  }

  private formatCommand(command: IAmazonNetworkLoadBalancerUpsertCommand): void {
    this.setAvailabilityZones(command);
    this.manageTargetGroupNames(command);
    this.manageRules(command);
  }

  protected onApplicationRefresh(values: IAmazonNetworkLoadBalancerUpsertCommand): void {
    if (this._isUnmounted) {
      return;
    }

    this.refreshUnsubscribe = undefined;
    this.props.dismissModal();
    this.setState({ taskMonitor: undefined });
    const newStateParams = {
      name: values.name,
      accountId: values.credentials,
      region: values.region,
      vpcId: values.vpcId,
      provider: 'aws',
    };

    if (!ReactInjector.$state.includes('**.loadBalancerDetails')) {
      ReactInjector.$state.go('.loadBalancerDetails', newStateParams);
    } else {
      ReactInjector.$state.go('^.loadBalancerDetails', newStateParams);
    }
  }

  public componentWillUnmount(): void {
    this._isUnmounted = true;
    if (this.refreshUnsubscribe) {
      this.refreshUnsubscribe();
    }
  }

  private onTaskComplete(values: IAmazonNetworkLoadBalancerUpsertCommand): void {
    this.props.app.loadBalancers.refresh();
    this.refreshUnsubscribe = this.props.app.loadBalancers.onNextRefresh(null, () => this.onApplicationRefresh(values));
  }

  private submit = (values: IAmazonNetworkLoadBalancerUpsertCommand): void => {
    const { app, forPipelineConfig, closeModal } = this.props;
    const { isNew } = this.state;

    const descriptor = isNew ? 'Create' : 'Update';
    const loadBalancerCommandFormatted = cloneDeep(values);
    loadBalancerCommandFormatted.ipAddressType = loadBalancerCommandFormatted.dualstack ? 'dualstack' : 'ipv4';
    delete loadBalancerCommandFormatted.dualstack;

    if (forPipelineConfig) {
      // don't submit to backend for creation. Just return the loadBalancerCommand object
      this.formatListeners(loadBalancerCommandFormatted).then(() => {
        closeModal && closeModal(loadBalancerCommandFormatted);
      });
    } else {
      const taskMonitor = new TaskMonitor({
        application: app,
        title: `${isNew ? 'Creating' : 'Updating'} your load balancer`,
        modalInstance: TaskMonitor.modalInstanceEmulation(() => this.props.dismissModal()),
        onTaskComplete: () => this.onTaskComplete(loadBalancerCommandFormatted),
      });

      taskMonitor.submit(() => {
        return this.formatListeners(loadBalancerCommandFormatted).then(() => {
          this.formatCommand(loadBalancerCommandFormatted);
          return LoadBalancerWriter.upsertLoadBalancer(loadBalancerCommandFormatted, app, descriptor);
        });
      });

      this.setState({ taskMonitor });
    }
  };

  private validate = (): FormikErrors<IAmazonNetworkLoadBalancerUpsertCommand> => {
    const errors = {} as FormikErrors<IAmazonNetworkLoadBalancerUpsertCommand>;
    return errors;
  };

  public render(): React.ReactElement<ICreateNetworkLoadBalancerProps> {
    const { app, dismissModal, forPipelineConfig, loadBalancer } = this.props;
    const { isNew, loadBalancerCommand, taskMonitor } = this.state;

    let heading = forPipelineConfig ? 'Configure Network Load Balancer' : 'Create New Network Load Balancer';
    if (!isNew) {
      heading = `Edit ${loadBalancerCommand.name}: ${loadBalancerCommand.region}: ${loadBalancerCommand.credentials}`;
    }

    const showLocationSection = isNew || forPipelineConfig;

    return (
      <WizardModal<IAmazonNetworkLoadBalancerUpsertCommand>
        heading={heading}
        initialValues={loadBalancerCommand}
        taskMonitor={taskMonitor}
        dismissModal={dismissModal}
        closeModal={this.submit}
        submitButtonLabel={forPipelineConfig ? (isNew ? 'Add' : 'Done') : isNew ? 'Create' : 'Update'}
        validate={this.validate}
        render={({ formik, nextIdx, wizard }) => (
          <>
            {showLocationSection && (
              <WizardPage
                label="Location"
                wizard={wizard}
                order={nextIdx()}
                render={({ innerRef }) => (
                  <LoadBalancerLocation
                    app={app}
                    forPipelineConfig={forPipelineConfig}
                    formik={formik}
                    isNew={isNew}
                    loadBalancer={loadBalancer}
                    ref={innerRef}
                  />
                )}
              />
            )}

            <WizardPage
              label="Target Groups"
              wizard={wizard}
              order={nextIdx()}
              render={({ innerRef }) => (
                <TargetGroups ref={innerRef} formik={formik} app={app} isNew={isNew} loadBalancer={loadBalancer} />
              )}
            />

            <WizardPage
              label="Listeners"
              wizard={wizard}
              order={nextIdx()}
              render={({ innerRef }) => <NLBListeners ref={innerRef} formik={formik} />}
            />

            <WizardPage
              label="Advanced Settings"
              wizard={wizard}
              order={nextIdx()}
              render={({ innerRef }) => (
                <NLBAdvancedSettings
                  ref={innerRef}
                  showDualstack={
                    !formik.values.isInternal && every(formik.values.targetGroups, { targetType: 'instance' })
                  }
                />
              )}
            />
          </>
        )}
      />
    );
  }
}
