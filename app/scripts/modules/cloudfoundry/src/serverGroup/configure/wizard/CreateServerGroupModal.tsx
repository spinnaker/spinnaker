import * as React from 'react';

import { FormikErrors } from 'formik';
import { get } from 'lodash';

import {
  Application,
  TaskMonitor,
  WizardModal,
  IArtifactAccount,
  AccountService,
  IModalComponentProps,
  ReactModal,
  noop,
  ReactInjector,
} from '@spinnaker/core';

import { ICloudFoundryCreateServerGroupCommand } from '../serverGroupConfigurationModel.cf';
import { CloudFoundryServerGroupBasicSettings } from './sections/basicSettings.cf';
import { CloudFoundryServerGroupConfigurationSettings } from './sections/configurationSettings.cf';
import { CloudFoundryServerGroupArtifactSettings } from './sections/artifactSettings.cf';
import { CfDisclaimerPage } from 'cloudfoundry/common/wizard/sections/cfDisclaimer.cf';
import { ServerGroupTemplateSelection } from 'cloudfoundry/serverGroup/configure/wizard/ServerGroupTemplateSelection';
import { CloudFoundryServerGroupConstantArtifactSettings } from 'cloudfoundry/serverGroup/configure/wizard/sections/constantArtifactSettings.cf';
import { ICloudFoundryServerGroup } from 'cloudfoundry/domain';

export interface ICloudFoundryCreateServerGroupProps extends IModalComponentProps {
  application: Application;
  command: ICloudFoundryCreateServerGroupCommand;
  isSourceConstant?: boolean;
  serverGroup?: ICloudFoundryServerGroup;
  title: string;
}

export interface ICloudFoundryCreateServerGroupState {
  artifactAccounts: IArtifactAccount[];
  requiresTemplateSelection: boolean;
  taskMonitor: TaskMonitor;
}

export class CloudFoundryCreateServerGroupModal extends React.Component<
  ICloudFoundryCreateServerGroupProps,
  ICloudFoundryCreateServerGroupState
> {
  public static defaultProps: Partial<ICloudFoundryCreateServerGroupProps> = {
    closeModal: noop,
    dismissModal: noop,
  };

  public static show(props: ICloudFoundryCreateServerGroupProps): Promise<ICloudFoundryCreateServerGroupCommand> {
    const modalProps = { dialogClassName: 'wizard-modal modal-lg' };
    return ReactModal.show(CloudFoundryCreateServerGroupModal, props, modalProps);
  }

  constructor(props: ICloudFoundryCreateServerGroupProps) {
    super(props);
    this.state = {
      artifactAccounts: [],
      requiresTemplateSelection: get(props, 'command.viewState.requiresTemplateSelection', false),
      taskMonitor: new TaskMonitor({
        application: props.application,
        title: 'Creating your server group',
        modalInstance: TaskMonitor.modalInstanceEmulation(() => this.props.dismissModal()),
        onTaskComplete: this.onTaskComplete,
      }),
    };
  }

  public componentDidMount(): void {
    AccountService.getArtifactAccounts().then(artifactAccounts => {
      this.setState({ artifactAccounts: artifactAccounts });
    });
  }

  private templateSelected = () => {
    this.setState({ requiresTemplateSelection: false });
  };

  private validate = (): FormikErrors<ICloudFoundryCreateServerGroupCommand> => {
    return {};
  };

  private onTaskComplete = () => {
    this.props.application.serverGroups.refresh();
  };

  private submit = (command: ICloudFoundryCreateServerGroupCommand): void => {
    command.selectedProvider = 'cloudfoundry';
    if (command.viewState.mode === 'createPipeline' || command.viewState.mode === 'editPipeline') {
      this.props.closeModal && this.props.closeModal(command);
    } else {
      this.state.taskMonitor.submit(() =>
        ReactInjector.serverGroupWriter.cloneServerGroup(command, this.props.application),
      );
    }
  };

  public render(): React.ReactElement<CloudFoundryCreateServerGroupModal> {
    const hideSections = new Set<string>();
    const { artifactAccounts, requiresTemplateSelection, taskMonitor } = this.state;
    const { application, command, dismissModal, isSourceConstant, serverGroup, title } = this.props;

    if (requiresTemplateSelection) {
      return (
        <ServerGroupTemplateSelection
          app={application}
          command={command}
          onDismiss={dismissModal}
          onTemplateSelected={this.templateSelected}
        />
      );
    }

    return (
      <WizardModal<ICloudFoundryCreateServerGroupCommand>
        heading={title}
        initialValues={command}
        taskMonitor={taskMonitor}
        dismissModal={dismissModal}
        closeModal={this.submit}
        submitButtonLabel={command.viewState.submitButtonLabel}
        validate={this.validate}
        hideSections={hideSections}
      >
        <CloudFoundryServerGroupBasicSettings />
        {isSourceConstant && <CloudFoundryServerGroupConstantArtifactSettings serverGroup={serverGroup} />}
        {!isSourceConstant && <CloudFoundryServerGroupArtifactSettings artifactAccounts={artifactAccounts} />}
        <CloudFoundryServerGroupConfigurationSettings artifactAccounts={artifactAccounts} />
        <CfDisclaimerPage />
      </WizardModal>
    );
  }
}
