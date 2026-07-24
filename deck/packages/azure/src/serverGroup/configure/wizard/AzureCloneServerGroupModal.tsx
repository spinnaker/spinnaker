import { cloneDeep } from 'lodash';
import React from 'react';

import type {
  Application,
  DeckRuntimeServices,
  IModalComponentProps,
  IRouterInjectedProps,
  IStage,
  ITemplateSelectionText,
} from '@spinnaker/core';
import {
  DeckRuntimeContext,
  DeployInitializer,
  FirewallLabels,
  noop,
  ReactModal,
  TaskMonitor,
  withRouter,
  WizardModal,
  WizardPage,
} from '@spinnaker/core';

import {
  ServerGroupAdvancedSettings,
  ServerGroupBasicSettings,
  ServerGroupCapacityAndZones,
  ServerGroupHealthSettings,
  ServerGroupImageSettings,
  ServerGroupInstanceType,
  ServerGroupLoadBalancers,
  ServerGroupNetworkSettings,
  ServerGroupSecurityGroups,
  ServerGroupTags,
} from './pages';
import type { AzureServerGroupConfigurationService } from '../serverGroupConfiguration.service';

export interface IAzureCloneServerGroupModalProps extends IModalComponentProps {
  application: Application;
  command: any;
  title: string;
}

interface IAzureCloneServerGroupModalState {
  command: any;
  loaded: boolean;
  requiresTemplateSelection: boolean;
  taskMonitor: TaskMonitor;
  templateSelectionText: ITemplateSelectionText;
}

export class AzureCloneServerGroupModalComponent extends React.Component<
  IAzureCloneServerGroupModalProps & IRouterInjectedProps,
  IAzureCloneServerGroupModalState
> {
  public static contextType = DeckRuntimeContext;
  public declare context: React.ContextType<typeof DeckRuntimeContext>;

  public static defaultProps: Partial<IAzureCloneServerGroupModalProps> = {
    closeModal: noop,
    dismissModal: noop,
  };

  private _isUnmounted = false;
  public static show(props: IAzureCloneServerGroupModalProps, runtimeServices: DeckRuntimeServices): Promise<any> {
    return ReactModal.show(
      AzureCloneServerGroupModal,
      props,
      { dialogClassName: 'wizard-modal modal-lg' },
      runtimeServices,
    );
  }

  constructor(props: IAzureCloneServerGroupModalProps & IRouterInjectedProps) {
    super(props);
    const requiresTemplateSelection = !!props.command.viewState?.requiresTemplateSelection;
    const workingCommand = cloneDeep(props.command);
    const alreadyConfigured =
      !requiresTemplateSelection && workingCommand.backingData?.accounts && workingCommand.backingData?.filtered;
    const templateSelectionText: ITemplateSelectionText = {
      copied: [
        'account, region, subnet, cluster name (stack, details)',
        'load balancers',
        FirewallLabels.get('firewalls'),
        'instance type',
        'all fields on the Advanced Settings page',
      ],
      notCopied: [],
      additionalCopyText:
        'If a server group exists in this cluster at the time of deployment, its scaling policies will be copied over to the new server group.',
    };

    if (!props.command.viewState?.disableStrategySelection) {
      templateSelectionText.notCopied.push(
        'the deployment strategy (if any) used to deploy the most recent server group',
      );
    }

    this.state = {
      command: workingCommand,
      loaded: !!alreadyConfigured,
      requiresTemplateSelection,
      taskMonitor: new TaskMonitor({
        application: props.application,
        title: 'Creating your server group',
        modalInstance: TaskMonitor.modalInstanceEmulation(() => this.props.dismissModal()),
        onTaskComplete: this.onTaskComplete,
      }),
      templateSelectionText,
    };
  }

  public componentDidMount(): void {
    if (this.state.loaded) {
      this.completeConfiguration(this.state.command);
    } else if (!this.state.requiresTemplateSelection) {
      this.prepareCommand();
    }
  }

  private get configurationService(): AzureServerGroupConfigurationService {
    return this.context.services.providerServiceDelegate.getDelegate('azure', 'serverGroup.configurationService');
  }

  private templateSelected = (): void => {
    this.setState({ loaded: false, requiresTemplateSelection: false });
    this.prepareCommand();
  };

  public componentWillUnmount(): void {
    this._isUnmounted = true;
  }

  private onTaskComplete = () => {
    this.props.application.serverGroups.refresh();
    this.props.application.serverGroups.onNextRefresh(null, this.onApplicationRefresh);
  };

  private onApplicationRefresh = (): void => {
    if (this._isUnmounted) {
      return;
    }
    const { command } = this.state;
    const cloneStage = this.state.taskMonitor.task.execution.stages.find(
      (stage: IStage) => stage.type === 'cloneServerGroup',
    );
    if (cloneStage && cloneStage.context['deploy.server.groups']) {
      const newServerGroupName = cloneStage.context['deploy.server.groups'][command.region];
      if (newServerGroupName) {
        let transitionTo = '^.^.^.clusters.serverGroup';
        if (this.props.stateService.includes('**.clusters.serverGroup')) {
          transitionTo = '^.serverGroup';
        }
        if (this.props.stateService.includes('**.clusters.cluster.serverGroup')) {
          transitionTo = '^.^.serverGroup';
        }
        if (this.props.stateService.includes('**.clusters')) {
          transitionTo = '.serverGroup';
        }
        this.props.stateService.go(transitionTo, {
          accountId: command.credentials,
          provider: 'azure',
          region: command.region,
          serverGroup: newServerGroupName,
        });
      }
    }
  };

  private prepareCommand = () => {
    const { application } = this.props;
    const { command } = this.state;
    command.viewState = command.viewState || {};
    command.processCommandUpdateResult = this.processCommandUpdateResult;

    const alreadyConfigured = command.backingData?.accounts && command.backingData?.filtered;
    const configure = alreadyConfigured
      ? null
      : (this.configurationService as any).configureCommand(application, command);

    const completeConfiguration = () => {
      this.completeConfiguration(command);
      if (!this._isUnmounted) {
        this.setState({ loaded: true, requiresTemplateSelection: false });
      }
    };

    if (configure) {
      configure.then(completeConfiguration);
    } else {
      completeConfiguration();
    }
  };

  private completeConfiguration(command: any): void {
    command.backingData = command.backingData || {};
    command.backingData.filtered = command.backingData.filtered || {};
    command.backingData.filtered.regions = command.backingData.filtered.regions || [];
    command.backingData.packageImages = command.backingData.packageImages || command.images || [];
    command.processCommandUpdateResult = this.processCommandUpdateResult;
    this.attachCommandHandlers(command);
    this.processCommandUpdateResult(command.credentialsChanged?.(command, true));
    this.processCommandUpdateResult(command.regionChanged?.(command, true));
  }

  private attachCommandHandlers(command: any): void {
    if (command.viewState.azureReactHandlersAttached) {
      return;
    }
    const credentialsChanged = command.credentialsChanged;
    const regionChanged = command.regionChanged;
    command.credentialsChanged = (cmd: any, isInit = false) =>
      this.withImageConfiguration(cmd, credentialsChanged?.(cmd, isInit));
    command.regionChanged = (cmd: any, isInit = false) =>
      this.withImageConfiguration(cmd, regionChanged?.(cmd, isInit));
    command.viewState.azureReactHandlersAttached = true;
  }

  private withImageConfiguration(command: any, result: any = { dirty: {} }): any {
    const imageResult = (this.configurationService as any).configureImages(command) || { dirty: {} };
    return { dirty: { ...(result.dirty || {}), ...(imageResult.dirty || {}) } };
  }

  private processCommandUpdateResult = (result: any = { dirty: {} }) => {
    const dirty = result.dirty || {};
    if (!Object.keys(dirty).length) {
      return;
    }
    const viewState = (this.state.command.viewState = this.state.command.viewState || {});
    viewState.dirty = { ...(viewState.dirty || {}), ...dirty };
  };

  public submit = (command: any): any => {
    const mode = command.viewState?.mode;
    if (mode === 'editPipeline' || mode === 'createPipeline') {
      return this.props.closeModal(command);
    }
    return this.state.taskMonitor.submit(() =>
      this.context.services.serverGroupWriter.cloneServerGroup(command, this.props.application),
    );
  };

  public render() {
    const { application, dismissModal, title } = this.props;
    const { command } = this.state;
    if (this.state.requiresTemplateSelection) {
      return (
        <DeployInitializer
          application={application}
          cloudProvider="azure"
          command={command}
          onDismiss={dismissModal}
          onTemplateSelected={this.templateSelected}
          templateSelectionText={this.state.templateSelectionText}
        />
      );
    }

    return (
      <WizardModal<any>
        closeModal={this.submit}
        dismissModal={dismissModal}
        heading={title}
        initialValues={command}
        loading={!this.state.loaded}
        submitButtonLabel={command.viewState?.submitButtonLabel}
        taskMonitor={this.state.taskMonitor}
        render={({ formik, nextIdx, wizard }) => (
          <>
            <WizardPage
              label="Basic Settings"
              order={nextIdx()}
              render={({ innerRef }) => <ServerGroupBasicSettings ref={innerRef} app={application} formik={formik} />}
              wizard={wizard}
            />
            <WizardPage
              label="Image"
              order={nextIdx()}
              render={({ innerRef }) => <ServerGroupImageSettings ref={innerRef} formik={formik} />}
              wizard={wizard}
            />
            <WizardPage
              label="Instance Type"
              order={nextIdx()}
              render={({ innerRef }) => <ServerGroupInstanceType ref={innerRef} formik={formik} />}
              wizard={wizard}
            />
            <WizardPage
              label="Capacity and Zones"
              order={nextIdx()}
              render={({ innerRef }) => <ServerGroupCapacityAndZones ref={innerRef} formik={formik} />}
              wizard={wizard}
            />
            <WizardPage
              label="Load Balancers"
              order={nextIdx()}
              render={({ innerRef }) => <ServerGroupLoadBalancers ref={innerRef} formik={formik} />}
              wizard={wizard}
            />
            <WizardPage
              label="Network Settings"
              order={nextIdx()}
              render={({ innerRef }) => <ServerGroupNetworkSettings ref={innerRef} formik={formik} />}
              wizard={wizard}
            />
            <WizardPage
              label={FirewallLabels.get('Firewalls')}
              order={nextIdx()}
              render={({ innerRef }) => <ServerGroupSecurityGroups ref={innerRef} formik={formik} />}
              wizard={wizard}
            />
            <WizardPage
              label="Health"
              order={nextIdx()}
              render={({ innerRef }) => <ServerGroupHealthSettings ref={innerRef} formik={formik} />}
              wizard={wizard}
            />
            <WizardPage
              label="Advanced Settings"
              order={nextIdx()}
              render={({ innerRef }) => <ServerGroupAdvancedSettings ref={innerRef} formik={formik} />}
              wizard={wizard}
            />
            <WizardPage
              label="Tags"
              order={nextIdx()}
              render={({ innerRef }) => <ServerGroupTags ref={innerRef} formik={formik} />}
              wizard={wizard}
            />
          </>
        )}
      />
    );
  }
}

export const AzureCloneServerGroupModal = Object.assign(
  withRouter<IAzureCloneServerGroupModalProps & IRouterInjectedProps>(AzureCloneServerGroupModalComponent),
  { show: AzureCloneServerGroupModalComponent.show },
);
