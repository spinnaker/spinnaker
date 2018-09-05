import * as React from 'react';
import { cloneDeep, get } from 'lodash';
import { FormikErrors, FormikValues } from 'formik';
import { IPromise } from 'angular';

import {
  AccountService,
  LoadBalancerWriter,
  ReactInjector,
  TaskMonitor,
  WizardModal,
  noop,
  ReactModal,
  ILoadBalancerModalProps,
} from '@spinnaker/core';

import { AWSProviderSettings } from 'amazon/aws.settings';
import { IAmazonApplicationLoadBalancer, IAmazonApplicationLoadBalancerUpsertCommand } from 'amazon/domain';
import { AwsReactInjector } from 'amazon/reactShims';

import { ALBListeners } from './ALBListeners';
import { TargetGroups } from './TargetGroups';
import { SecurityGroups } from '../common/SecurityGroups';
import { LoadBalancerLocation } from '../common/LoadBalancerLocation';

import '../common/configure.less';

export interface ICreateApplicationLoadBalancerProps extends ILoadBalancerModalProps {
  loadBalancer: IAmazonApplicationLoadBalancer;
}

export interface ICreateApplicationLoadBalancerState {
  includeSecurityGroups: boolean;
  isNew: boolean;
  loadBalancerCommand: IAmazonApplicationLoadBalancerUpsertCommand;
  taskMonitor: TaskMonitor;
}

type ApplicationLoadBalancerModal = new () => WizardModal<IAmazonApplicationLoadBalancerUpsertCommand>;
const ApplicationLoadBalancerModal = WizardModal as ApplicationLoadBalancerModal;

export class CreateApplicationLoadBalancer extends React.Component<
  ICreateApplicationLoadBalancerProps,
  ICreateApplicationLoadBalancerState
> {
  public static defaultProps: Partial<ICreateApplicationLoadBalancerProps> = {
    closeModal: noop,
    dismissModal: noop,
  };

  private _isUnmounted = false;
  private refreshUnsubscribe: () => void;
  private certificateTypes = get(AWSProviderSettings, 'loadBalancers.certificateTypes', ['iam', 'acm']);

  public static show(props: ICreateApplicationLoadBalancerProps): Promise<IAmazonApplicationLoadBalancerUpsertCommand> {
    const modalProps = { dialogClassName: 'wizard-modal modal-lg' };
    return ReactModal.show(CreateApplicationLoadBalancer, props, modalProps);
  }

  constructor(props: ICreateApplicationLoadBalancerProps) {
    super(props);

    const loadBalancerCommand = props.loadBalancer
      ? AwsReactInjector.awsLoadBalancerTransformer.convertApplicationLoadBalancerForEditing(props.loadBalancer)
      : AwsReactInjector.awsLoadBalancerTransformer.constructNewApplicationLoadBalancerTemplate(props.app);

    this.state = {
      includeSecurityGroups: !!loadBalancerCommand.vpcId,
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

  private formatListeners(command: IAmazonApplicationLoadBalancerUpsertCommand): IPromise<void> {
    return AccountService.getAccountDetails(command.credentials).then(account => {
      command.listeners.forEach(listener => {
        if (listener.protocol === 'HTTP') {
          delete listener.sslPolicy;
          listener.certificates = [];
        }
        listener.certificates.forEach(certificate => {
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

  private setAvailabilityZones(loadBalancerCommand: IAmazonApplicationLoadBalancerUpsertCommand): void {
    const availabilityZones: { [region: string]: string[] } = {};
    availabilityZones[loadBalancerCommand.region] = loadBalancerCommand.regionZones || [];
    loadBalancerCommand.availabilityZones = availabilityZones;
  }

  private addAppName(name: string): string {
    return `${this.props.app.name}-${name}`;
  }

  private manageTargetGroupNames(command: IAmazonApplicationLoadBalancerUpsertCommand): void {
    (command.targetGroups || []).forEach(targetGroupDescription => {
      targetGroupDescription.name = this.addAppName(targetGroupDescription.name);
    });
    (command.listeners || []).forEach(listenerDescription => {
      listenerDescription.defaultActions.forEach(actionDescription => {
        if (actionDescription.targetGroupName) {
          actionDescription.targetGroupName = this.addAppName(actionDescription.targetGroupName);
        }
      });
      (listenerDescription.rules || []).forEach(ruleDescription => {
        ruleDescription.actions.forEach(actionDescription => {
          if (actionDescription.targetGroupName) {
            actionDescription.targetGroupName = this.addAppName(actionDescription.targetGroupName);
          }
        });
      });
    });
  }

  private manageRules(command: IAmazonApplicationLoadBalancerUpsertCommand): void {
    command.listeners.forEach(listener => {
      listener.rules.forEach((rule, index) => {
        // Set the priority in array order, starting with 1
        rule.priority = index + 1;
        // Remove conditions that have no value
        rule.conditions = rule.conditions.filter(condition => condition.values[0].length > 0);
      });
    });
  }

  private formatCommand(command: IAmazonApplicationLoadBalancerUpsertCommand): void {
    this.setAvailabilityZones(command);
    this.manageTargetGroupNames(command);
    this.manageRules(command);
  }

  protected onApplicationRefresh(values: IAmazonApplicationLoadBalancerUpsertCommand): void {
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

  private onTaskComplete(values: IAmazonApplicationLoadBalancerUpsertCommand): void {
    this.props.app.loadBalancers.refresh();
    this.refreshUnsubscribe = this.props.app.loadBalancers.onNextRefresh(null, () => this.onApplicationRefresh(values));
  }

  private submit = (values: IAmazonApplicationLoadBalancerUpsertCommand): void => {
    const { app, forPipelineConfig, closeModal } = this.props;
    const { isNew } = this.state;

    const descriptor = isNew ? 'Create' : 'Update';
    const loadBalancerCommandFormatted = cloneDeep(values);

    // replace all authenticateOidcConfig with authenticateOidcActionConfig because aws
    loadBalancerCommandFormatted.listeners.forEach(listener => {
      listener.defaultActions.forEach((a: any) => {
        if (a.authenticateOidcConfig) {
          a.authenticateOidcActionConfig = a.authenticateOidcConfig;
          delete a.authenticateOidcConfig;
        }
      });
      listener.rules.forEach(r =>
        r.actions.forEach((a: any) => {
          if (a.authenticateOidcConfig) {
            a.authenticateOidcActionConfig = a.authenticateOidcConfig;
            delete a.authenticateOidcConfig;
          }
        }),
      );
    });

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

  private validate = (values: FormikValues): FormikErrors<IAmazonApplicationLoadBalancerUpsertCommand> => {
    this.setState({ includeSecurityGroups: !!values.vpcId });
    const errors = {} as FormikErrors<IAmazonApplicationLoadBalancerUpsertCommand>;
    return errors;
  };

  public render() {
    const { app, dismissModal, forPipelineConfig, loadBalancer } = this.props;
    const { includeSecurityGroups, isNew, loadBalancerCommand, taskMonitor } = this.state;

    const hideSections = new Set<string>();

    if (!isNew && !forPipelineConfig) {
      hideSections.add(LoadBalancerLocation.label);
    }

    if (!includeSecurityGroups) {
      hideSections.add(SecurityGroups.label);
    }

    let heading = forPipelineConfig ? 'Configure Application Load Balancer' : 'Create New Application Load Balancer';
    if (!isNew) {
      heading = `Edit ${loadBalancerCommand.name}: ${loadBalancerCommand.region}: ${loadBalancerCommand.credentials}`;
    }

    return (
      <ApplicationLoadBalancerModal
        heading={heading}
        initialValues={loadBalancerCommand}
        taskMonitor={taskMonitor}
        dismissModal={dismissModal}
        closeModal={this.submit}
        submitButtonLabel={forPipelineConfig ? (isNew ? 'Add' : 'Done') : isNew ? 'Create' : 'Update'}
        validate={this.validate}
        hideSections={hideSections}
      >
        <LoadBalancerLocation
          app={app}
          isNew={isNew}
          forPipelineConfig={forPipelineConfig}
          loadBalancer={loadBalancer}
        />
        <SecurityGroups done={true} />
        <TargetGroups app={app} isNew={isNew} loadBalancer={loadBalancer} done={true} />
        <ALBListeners app={app} done={true} />
      </ApplicationLoadBalancerModal>
    );
  }
}
