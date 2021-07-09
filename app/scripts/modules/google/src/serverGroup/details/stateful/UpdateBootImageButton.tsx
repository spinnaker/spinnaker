import React from 'react';

import {
  Application,
  FormikFormField,
  IModalComponentProps,
  ReactModal,
  TaskMonitor,
  WizardModal,
  WizardPage,
} from '@spinnaker/core';

import { StatefulMIGService } from './StatefulMIGService';
import { IGceServerGroup } from '../../../domain';
import { GceImageReader, IGceImage, ImageSelect } from '../../../image';

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
  availableImages: IGceImage[];
  taskMonitor: TaskMonitor;
}

interface IUpdateBootImageModalFormValues {
  image: string;
}

class UpdateBootImageModal extends React.Component<IUpdateBootImageModalProps, IUpdateBootImageModalState> {
  public constructor(props: IUpdateBootImageModalProps) {
    super(props);
    this.state = {
      availableImages: [],
      taskMonitor: new TaskMonitor({
        application: props.application,
        title: 'Updating Boot Image',
        modalInstance: TaskMonitor.modalInstanceEmulation(() => this.props.dismissModal()),
      }),
    };
  }

  public componentDidMount() {
    GceImageReader.findImages({
      account: this.props.serverGroup.account,
      provider: 'gce',
      q: '*',
    }).then((images) => {
      this.setState({ availableImages: images });
    });
  }

  private submit = (values: IUpdateBootImageModalFormValues): void => {
    this.state.taskMonitor.submit(() =>
      StatefulMIGService.statefullyUpdateBootDisk(this.props.application.name, values.image, this.props.serverGroup),
    );
  };

  public render() {
    return (
      <WizardModal<IUpdateBootImageModalFormValues>
        closeModal={this.submit}
        dismissModal={this.props.dismissModal}
        heading="Update Boot Disk Image"
        initialValues={{ image: this.props.bootImage }}
        submitButtonLabel="Update Image"
        taskMonitor={this.state.taskMonitor}
        render={({ formik, nextIdx, wizard }) => (
          <WizardPage
            label="Boot Image"
            wizard={wizard}
            order={nextIdx()}
            render={() => (
              <FormikFormField
                input={(props) => (
                  <div className="full-width" style={{ height: '225px' }}>
                    <ImageSelect
                      availableImages={this.state.availableImages}
                      selectedImage={props.value}
                      selectImage={(imageName: string) => formik.setFieldValue('image', imageName)}
                    />
                  </div>
                )}
                label="Boot image name:"
                name="image"
                required={true}
              />
            )}
          />
        )}
      />
    );
  }
}
