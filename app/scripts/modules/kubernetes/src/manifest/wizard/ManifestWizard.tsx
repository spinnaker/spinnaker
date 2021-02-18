import React from 'react';

import {
  Application,
  IModalComponentProps,
  ManifestWriter,
  noop,
  ReactModal,
  TaskMonitor,
  WizardModal,
  WizardPage,
} from '@spinnaker/core';

import { WizardManifestBasicSettings } from './BasicSettings';
import { ManifestEntry } from './ManifestEntry';
import { IKubernetesManifestCommandData, KubernetesManifestCommandBuilder } from '../manifestCommandBuilder.service';

export interface IKubernetesManifestModalProps extends IModalComponentProps {
  title: string;
  application: Application;
  command: IKubernetesManifestCommandData;
  isNew?: boolean;
}

export interface IKubernetesManifestModalState {
  command: IKubernetesManifestCommandData;
  loaded: boolean;
  taskMonitor: TaskMonitor;
}

export class ManifestWizard extends React.Component<IKubernetesManifestModalProps, IKubernetesManifestModalState> {
  public static defaultProps: Partial<IKubernetesManifestModalProps> = {
    closeModal: noop,
    dismissModal: noop,
  };

  public static show(props: IKubernetesManifestModalProps): Promise<IKubernetesManifestCommandData> {
    const modalProps = { dialogClassName: 'wizard-modal modal-lg' };
    return ReactModal.show(ManifestWizard, props, modalProps);
  }

  constructor(props: IKubernetesManifestModalProps) {
    super(props);
    if (!props.command) {
      KubernetesManifestCommandBuilder.buildNewManifestCommand(props.application).then((command) => {
        Object.assign(this.state.command, command);
        this.setState({ loaded: true });
      });
    }

    this.state = {
      loaded: !!props.command,
      command: props.command || ({} as IKubernetesManifestCommandData),
      taskMonitor: new TaskMonitor({
        application: props.application,
        title: `${this.props.isNew ? 'Deploying' : 'Updating'} your manifest`,
        modalInstance: TaskMonitor.modalInstanceEmulation(() => this.props.dismissModal()),
      }),
    };
  }

  private submit = (c: IKubernetesManifestCommandData): void => {
    const command = KubernetesManifestCommandBuilder.copyAndCleanCommand(c.command);
    const submitMethod = () => ManifestWriter.deployManifest(command, this.props.application);
    this.state.taskMonitor.submit(submitMethod);
  };

  public render() {
    const { application, dismissModal, isNew } = this.props;
    const { loaded, taskMonitor, command } = this.state;

    return (
      <WizardModal<IKubernetesManifestCommandData>
        heading={`${isNew ? 'Deploy' : 'Update'} Manifest`}
        initialValues={command}
        loading={!loaded}
        taskMonitor={taskMonitor}
        dismissModal={dismissModal}
        closeModal={this.submit}
        submitButtonLabel={isNew ? 'Create' : 'Edit'}
        render={({ formik, nextIdx, wizard }) => (
          <>
            <WizardPage
              label="Basic Settings"
              wizard={wizard}
              order={nextIdx()}
              render={({ innerRef }) => <WizardManifestBasicSettings ref={innerRef} formik={formik} />}
            />

            <WizardPage
              label="Manifest"
              wizard={wizard}
              order={nextIdx()}
              render={({ innerRef }) => <ManifestEntry ref={innerRef} formik={formik} app={application} />}
            />
          </>
        )}
      />
    );
  }
}
