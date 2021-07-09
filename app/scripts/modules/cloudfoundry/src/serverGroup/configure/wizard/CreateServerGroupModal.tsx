import { get } from 'lodash';
import React from 'react';

import {
  Application,
  IModalComponentProps,
  IPipeline,
  IStage,
  noop,
  ReactInjector,
  ReactModal,
  TaskMonitor,
  WizardModal,
  WizardPage,
} from '@spinnaker/core';

import { ServerGroupTemplateSelection } from './ServerGroupTemplateSelection';
import { ICloudFoundryServerGroup } from '../../../domain';
import { CloudFoundryServerGroupArtifactSettings } from './sections/artifactSettings/ArtifactSettings.cf';
import { CloudFoundryServerGroupConstantArtifactSettings } from './sections/artifactSettings/ConstantArtifactSettings.cf';
import { CloudFoundryServerGroupBasicSettings } from './sections/basicSettings/BasicSettings.cf';
import { CloudFoundryServerGroupCloneSettings } from './sections/cloneSettings/CloneSettings.cf';
import { CloudFoundryServerGroupConfigurationSettings } from './sections/configurationSettings/ConfigurationSettings.cf';
import { ICloudFoundryCreateServerGroupCommand } from '../serverGroupConfigurationModel.cf';

import './serverGroup.less';

export interface ICloudFoundryCreateServerGroupProps extends IModalComponentProps {
  application: Application;
  command: ICloudFoundryCreateServerGroupCommand;
  isSourceConstant?: boolean;
  serverGroup?: ICloudFoundryServerGroup;
  title: string;
}

export interface ICloudFoundryCreateServerGroupState {
  pipeline: IPipeline;
  isClone: boolean;
  loading: boolean;
  requiresTemplateSelection: boolean;
  stage?: IStage;
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
    const pipeline = get(props, 'command.viewState.pipeline', undefined);
    const stage = get(props, 'command.viewState.stage', undefined);
    const mode = get(props, 'command.viewState.mode', undefined);
    this.state = {
      pipeline: pipeline,
      isClone: !!props.isSourceConstant || mode === 'editClonePipeline',
      loading: false,
      requiresTemplateSelection: get(props, 'command.viewState.requiresTemplateSelection', false),
      stage,
      taskMonitor: new TaskMonitor({
        application: props.application,
        title: 'Creating your server group',
        modalInstance: TaskMonitor.modalInstanceEmulation(() => this.props.dismissModal()),
        onTaskComplete: this.onTaskComplete,
      }),
    };
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
    if (
      command.viewState.mode === 'createPipeline' ||
      command.viewState.mode === 'editPipeline' ||
      command.viewState.mode === 'editClonePipeline'
    ) {
      this.props.closeModal && this.props.closeModal(command);
    } else if (command.viewState.mode === 'clone') {
      this.state.taskMonitor.submit(() =>
        ReactInjector.serverGroupWriter.cloneServerGroup(command, this.props.application),
      );
    } else {
      this.state.taskMonitor.submit(() =>
        ReactInjector.serverGroupWriter.cloneServerGroup(command, this.props.application),
      );
    }
  };

  public render(): React.ReactElement<CloudFoundryCreateServerGroupModal> {
    const { loading, pipeline, isClone, requiresTemplateSelection, stage, taskMonitor } = this.state;
    const { application, command, dismissModal, title, isSourceConstant } = this.props;

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

            {isClone && isSourceConstant && (
              <WizardPage
                label="Application"
                wizard={wizard}
                order={nextIdx()}
                render={({ innerRef }) => (
                  <CloudFoundryServerGroupConstantArtifactSettings
                    ref={innerRef}
                    formik={formik}
                    source={command.source}
                  />
                )}
              />
            )}

            {isClone && !isSourceConstant && (
              <WizardPage
                label="Source"
                wizard={wizard}
                order={nextIdx()}
                render={({ innerRef }) => (
                  <CloudFoundryServerGroupCloneSettings application={application} ref={innerRef} formik={formik} />
                )}
              />
            )}

            {!isClone && (
              <WizardPage
                label="Application"
                wizard={wizard}
                order={nextIdx()}
                render={({ innerRef }) => (
                  <CloudFoundryServerGroupArtifactSettings
                    ref={innerRef}
                    formik={formik}
                    pipeline={pipeline}
                    stage={stage}
                  />
                )}
              />
            )}

            <WizardPage
              label="Manifest"
              wizard={wizard}
              order={nextIdx()}
              render={({ innerRef }) => (
                <CloudFoundryServerGroupConfigurationSettings
                  ref={innerRef}
                  formik={formik}
                  pipeline={pipeline}
                  stage={stage}
                />
              )}
            />
          </>
        )}
      />
    );
  }
}
