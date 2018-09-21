import * as React from 'react';
import { FormikErrors, FormikValues } from 'formik';

import {
  Application,
  TaskMonitor,
  WizardModal,
  IModalComponentProps,
  noop,
  ReactModal,
  ManifestWriter,
} from '@spinnaker/core';

import {
  KubernetesManifestCommandBuilder,
  IKubernetesManifestCommandData,
} from 'kubernetes/v2/manifest/manifestCommandBuilder.service';
import { ManifestBasicSettings } from 'kubernetes/v2/manifest/wizard/BasicSettings';
import { ManifestEntry } from 'kubernetes/v2/manifest/wizard/ManifestEntry';

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
      KubernetesManifestCommandBuilder.buildNewManifestCommand(props.application).then(command => {
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

  private validate = (_values: FormikValues): FormikErrors<IKubernetesManifestCommandData> => {
    const errors = {} as FormikErrors<IKubernetesManifestCommandData>;
    return errors;
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
        validate={this.validate}
      >
        <ManifestBasicSettings done={true} app={application} />
        <ManifestEntry done={true} app={application} />
      </WizardModal>
    );
  }
}
