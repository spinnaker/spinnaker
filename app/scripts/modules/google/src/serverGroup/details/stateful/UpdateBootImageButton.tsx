import * as React from 'react';

import {
  Application,
  FormikFormField,
  IModalComponentProps,
  ReactModal,
  TaskMonitor,
  TextInput,
  WizardModal,
  WizardPage,
} from '@spinnaker/core';

import { IGceServerGroup } from 'google/domain';
import { StatefulMIGService } from './StatefulMIGService';

interface IUpdateBootImageButtonProps {
  application: Application;
  bootImage: string;
  serverGroup: IGceServerGroup;
}

export function UpdateBootImageButton(props: IUpdateBootImageButtonProps) {
  function openUpdateImageModal() {
    const componentProps = { ...props } as IUpdateBootImageModalProps;
    ReactModal.show(UpdateBootImageModal, componentProps, { dialogClassName: 'wizard-modal modal-lg' });
  }

  return (
    <button className="btn-link" onClick={openUpdateImageModal}>
      Statefully Update
    </button>
  );
}

interface IUpdateBootImageModalProps extends IModalComponentProps, IUpdateBootImageButtonProps {
  application: Application;
  serverGroup: IGceServerGroup;
}

interface IUpdateBootImageModalState {
  taskMonitor: TaskMonitor;
}

interface IUpdateBootImageModalFormValues {
  image: string;
}

class UpdateBootImageModal extends React.Component<IUpdateBootImageModalProps, IUpdateBootImageModalState> {
  public constructor(props: IUpdateBootImageModalProps) {
    super(props);
    this.state = {
      taskMonitor: new TaskMonitor({
        application: props.application,
        title: 'Updating Boot Image',
        modalInstance: TaskMonitor.modalInstanceEmulation(() => this.props.dismissModal()),
      }),
    };
  }

  private submit = (values: IUpdateBootImageModalFormValues): void => {
    this.state.taskMonitor.submit(() =>
      StatefulMIGService.statefullyUpdateBootDisk(this.props.application.name, values.image, this.props.serverGroup),
    );
  };

  // todo(mneterval): replace TextInput with new, performant React GceImageSelector
  public render() {
    return (
      <WizardModal<IUpdateBootImageModalFormValues>
        closeModal={this.submit}
        dismissModal={this.props.dismissModal}
        heading="Update Boot Disk Image"
        initialValues={{ image: this.props.bootImage }}
        submitButtonLabel="Update Image"
        taskMonitor={this.state.taskMonitor}
        render={({ nextIdx, wizard }) => (
          <WizardPage
            label="Boot Image"
            wizard={wizard}
            order={nextIdx()}
            render={() => (
              <FormikFormField input={TextInput} label="Enter name of new boot image:" name="image" required={true} />
            )}
          />
        )}
      />
    );
  }
}
