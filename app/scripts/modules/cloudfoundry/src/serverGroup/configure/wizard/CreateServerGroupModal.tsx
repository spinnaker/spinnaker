import * as React from 'react';

import { get } from 'lodash';

import {
  AccountService,
  Application,
  IArtifactAccount,
  IModalComponentProps,
  ReactInjector,
  ReactModal,
  TaskMonitor,
  WizardModal,
  WizardPage,
  noop,
} from '@spinnaker/core';

import { ICloudFoundryCreateServerGroupCommand } from '../serverGroupConfigurationModel.cf';
import { CloudFoundryServerGroupBasicSettings } from './sections/basicSettings/BasicSettings.cf';
import { CloudFoundryServerGroupArtifactSettings } from './sections/artifactSettings/ArtifactSettings.cf';
import { CloudFoundryServerGroupConstantArtifactSettings } from './sections/artifactSettings/ConstantArtifactSettings.cf';
import { CloudFoundryServerGroupConfigurationSettings } from './sections/configurationSettings/ConfigurationSettings.cf';
import { CfDisclaimerPage } from 'cloudfoundry/common/wizard/sections/cfDisclaimer.cf';
import { ServerGroupTemplateSelection } from 'cloudfoundry/serverGroup/configure/wizard/ServerGroupTemplateSelection';
import { ICloudFoundryServerGroup } from 'cloudfoundry/domain';

import './serverGroup.less';

export interface ICloudFoundryCreateServerGroupProps extends IModalComponentProps {
  application: Application;
  command: ICloudFoundryCreateServerGroupCommand;
  isSourceConstant?: boolean;
  serverGroup?: ICloudFoundryServerGroup;
  title: string;
}

export interface ICloudFoundryCreateServerGroupState {
  artifactAccounts: IArtifactAccount[];
  loading: boolean;
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
      loading: false,
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
    this.initialize();
  };

  private initialize = () => {
    this.setState({ loading: false });
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
    const { artifactAccounts, loading, requiresTemplateSelection, taskMonitor } = this.state;
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
        loading={loading}
        taskMonitor={taskMonitor}
        dismissModal={dismissModal}
        closeModal={this.submit}
        submitButtonLabel={command.viewState.submitButtonLabel}
        render={({ formik, nextIdx, wizard }) => (
          <>
            <WizardPage
              label="Basic Settings"
              wizard={wizard}
              order={nextIdx()}
              render={({ innerRef }) => <CloudFoundryServerGroupBasicSettings ref={innerRef} formik={formik} />}
            />

            {isSourceConstant && (
              <WizardPage
                label="Artifact"
                wizard={wizard}
                order={nextIdx()}
                render={({ innerRef }) => (
                  <CloudFoundryServerGroupConstantArtifactSettings
                    ref={innerRef}
                    formik={formik}
                    serverGroup={serverGroup}
                  />
                )}
              />
            )}

            {!isSourceConstant && (
              <WizardPage
                label="Artifact"
                wizard={wizard}
                order={nextIdx()}
                render={({ innerRef }) => (
                  <CloudFoundryServerGroupArtifactSettings
                    ref={innerRef}
                    formik={formik}
                    artifactAccounts={artifactAccounts}
                  />
                )}
              />
            )}

            <WizardPage
              label="Configuration"
              wizard={wizard}
              order={nextIdx()}
              render={({ innerRef }) => (
                <CloudFoundryServerGroupConfigurationSettings
                  ref={innerRef}
                  formik={formik}
                  artifactAccounts={artifactAccounts}
                />
              )}
            />

            <WizardPage
              label="Disclaimer"
              wizard={wizard}
              order={nextIdx()}
              render={({ innerRef }) => <CfDisclaimerPage ref={innerRef} />}
            />
          </>
        )}
      />
    );
  }
}
