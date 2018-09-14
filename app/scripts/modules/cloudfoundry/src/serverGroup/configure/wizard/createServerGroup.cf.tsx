import * as React from 'react';

import { FormikErrors } from 'formik';
import { get } from 'lodash';

import {
  Application,
  IServerGroupCommand,
  ServerGroupWriter,
  TaskMonitor,
  WizardModal,
  IArtifactAccount,
  AccountService,
  IModalComponentProps,
} from '@spinnaker/core';

import { ICloudFoundryCreateServerGroupCommand } from '../serverGroupConfigurationModel.cf';
import { CloudFoundryServerGroupBasicSettings } from './sections/basicSettings.cf';
import { CloudFoundryServerGroupConfigurationSettings } from './sections/configurationSettings.cf';
import { CloudFoundryServerGroupArtifactSettings } from './sections/artifactSettings.cf';
import { CfDisclaimerPage } from 'cloudfoundry/common/wizard/sections/cfDisclaimer.cf';
import { ServerGroupTemplateSelection } from 'cloudfoundry/serverGroup/configure/wizard/ServerGroupTemplateSelection';

import '../../../common/modalWizard.less';

type CloudFoundryCreateServerGroupModal = new () => WizardModal<ICloudFoundryCreateServerGroupCommand>;
const CloudFoundryCreateServerGroupModal = WizardModal as CloudFoundryCreateServerGroupModal;

export interface ICloudFoundryCreateServerGroupProps extends IModalComponentProps {
  onDismiss: (rejectReason?: any) => void;
  onSubmit: (command?: IServerGroupCommand) => void;
  initialCommand: ICloudFoundryCreateServerGroupCommand;
  taskMonitor: TaskMonitor;
  serverGroupWriter: ServerGroupWriter;
  application: Application;
}

export interface ICloudFoundryCreateServerGroupState {
  artifactAccounts: IArtifactAccount[];
  requiresTemplateSelection: boolean;
}

export class CloudFoundryCreateServerGroup extends React.Component<
  ICloudFoundryCreateServerGroupProps,
  ICloudFoundryCreateServerGroupState
> {
  constructor(props: ICloudFoundryCreateServerGroupProps) {
    super(props);
    this.state = {
      artifactAccounts: [],
      requiresTemplateSelection: get(props, 'initialCommand.viewState.requiresTemplateSelection', false),
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

  private dismiss = (): void => {
    this.props.onDismiss('cancelled');
  };

  private submit = (values: ICloudFoundryCreateServerGroupCommand): void => {
    const command = (values as any) as IServerGroupCommand;
    command.selectedProvider = 'cloudfoundry';
    if (values.viewState.mode === 'createPipeline' || values.viewState.mode === 'editPipeline') {
      this.props.onSubmit(command);
    } else {
      this.props.taskMonitor.submit(() =>
        this.props.serverGroupWriter.cloneServerGroup(command, this.props.application),
      );
    }
  };

  public render(): React.ReactElement<CloudFoundryCreateServerGroup> {
    const hideSections = new Set<string>();
    const { artifactAccounts, requiresTemplateSelection } = this.state;
    const { application, initialCommand } = this.props;

    if (requiresTemplateSelection) {
      return (
        <ServerGroupTemplateSelection
          app={application}
          command={initialCommand}
          onDismiss={this.dismiss}
          onTemplateSelected={this.templateSelected}
        />
      );
    }

    return (
      <CloudFoundryCreateServerGroupModal
        heading={'Create server group'}
        initialValues={initialCommand}
        taskMonitor={this.props.taskMonitor}
        dismissModal={this.dismiss}
        closeModal={this.submit}
        submitButtonLabel={initialCommand.viewState.submitButtonLabel}
        validate={this.validate}
        hideSections={hideSections}
      >
        <CloudFoundryServerGroupBasicSettings />
        <CloudFoundryServerGroupArtifactSettings artifactAccounts={artifactAccounts} />
        <CloudFoundryServerGroupConfigurationSettings artifactAccounts={artifactAccounts} />
        <CfDisclaimerPage />
      </CloudFoundryCreateServerGroupModal>
    );
  }
}
