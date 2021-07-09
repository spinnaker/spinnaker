import { Form, Formik, FormikProps } from 'formik';
import React from 'react';
import { Modal, ModalFooter } from 'react-bootstrap';

import {
  Application,
  FormikFormField,
  ICapacity,
  IModalComponentProps,
  IServerGroupJob,
  ModalClose,
  noop,
  NumberInput,
  ReactInjector,
  ReactModal,
  SpinFormik,
  TaskMonitor,
  TaskMonitorWrapper,
  TaskReason,
} from '@spinnaker/core';
import { ICloudFoundryServerGroup } from '../../../domain';

export interface ICloudFoundryResizeServerGroupModalProps extends IModalComponentProps {
  application: Application;
  serverGroup: ICloudFoundryServerGroup;
}

export interface ICloudFoundryResizeServerGroupModalState {
  initialValues: ICloudFoundryResizeServerGroupValues;
  taskMonitor: TaskMonitor;
}

export interface ICloudFoundryResizeServerGroupValues {
  desired: number | string;
  diskQuota?: number;
  memory?: number;
  reason?: string;
}

export interface ICloudFoundryResizeJob extends IServerGroupJob {
  capacity?: Partial<ICapacity>;
  diskQuota?: number;
  instanceCount?: number;
  memory?: number;
  serverGroupName: string;
  reason?: string;
}

export class CloudFoundryResizeServerGroupModal extends React.Component<
  ICloudFoundryResizeServerGroupModalProps,
  ICloudFoundryResizeServerGroupModalState
> {
  public static defaultProps: Partial<ICloudFoundryResizeServerGroupModalProps> = {
    closeModal: noop,
    dismissModal: noop,
  };

  private formikRef = React.createRef<Formik<ICloudFoundryResizeServerGroupValues>>();

  public static show(props: ICloudFoundryResizeServerGroupModalProps): Promise<ICloudFoundryResizeJob> {
    const modalProps = {};
    return ReactModal.show(CloudFoundryResizeServerGroupModal, props, modalProps);
  }

  constructor(props: ICloudFoundryResizeServerGroupModalProps) {
    super(props);

    const { capacity, diskQuota, memory } = props.serverGroup;
    const { desired } = capacity;
    this.state = {
      initialValues: {
        desired,
        diskQuota: diskQuota,
        memory: memory,
      },
      taskMonitor: new TaskMonitor({
        application: props.application,
        title: 'Resizing your server group',
        modalInstance: TaskMonitor.modalInstanceEmulation(() => this.props.dismissModal()),
        onTaskComplete: () => this.props.application.serverGroups.refresh(),
      }),
    };
  }

  private close = (args?: any): void => {
    this.props.dismissModal.apply(null, args);
  };

  private submit = (values: ICloudFoundryResizeServerGroupValues): void => {
    const { desired, diskQuota, memory, reason } = values;
    const { serverGroup, application } = this.props;
    const capacity = {
      min: desired,
      max: desired,
      desired,
    };

    const command: ICloudFoundryResizeJob = {
      capacity,
      diskQuota,
      memory,
      reason,
      serverGroupName: serverGroup.name,
    };

    this.state.taskMonitor.submit(() => {
      return ReactInjector.serverGroupWriter.resizeServerGroup(serverGroup, application, command);
    });
  };

  private renderDesired(formik: FormikProps<ICloudFoundryResizeServerGroupValues>): JSX.Element {
    const { serverGroup } = this.props;
    const { capacity } = serverGroup;
    return (
      <div>
        <div className="form-group">
          <div className="col-md-3 sm-label-right">Current size</div>
          <div className="col-md-4">
            <div className="horizontal middle">
              <input type="number" className="NumberInput form-control" value={capacity.desired} disabled={true} />
              <div className="sp-padding-xs-xaxis">instances</div>
            </div>
          </div>
        </div>
        <div className="form-group">
          <div className="col-md-3 sm-label-right">Resize to</div>
          <div className="col-md-4">
            <div className="horizontal middle">
              <FormikFormField
                name="desired"
                input={(props) => <NumberInput {...props} min={0} />}
                touched={true}
                required={true}
                onChange={(value) => {
                  formik.setFieldValue('min', value);
                  formik.setFieldValue('max', value);
                }}
              />
              <div className="sp-padding-xs-xaxis">instances</div>
            </div>
          </div>
        </div>
      </div>
    );
  }

  private renderQuota(
    formik: FormikProps<ICloudFoundryResizeServerGroupValues>,
    field: string,
    fieldLabel: string,
    initialValue: number,
  ): JSX.Element {
    return (
      <div>
        <div className="form-group">
          <div className="col-md-3 sm-label-right">Current size</div>
          <div className="col-md-5">
            <div className="horizontal middle">
              <div className="StandardFieldLayout flex-container-h baseline margin-between-lg">
                <input type="number" className="NumberInput form-control" value={initialValue} disabled={true} />
              </div>
              <div className="sp-padding-xs-xaxis">{fieldLabel}</div>
            </div>
          </div>
        </div>
        <div className="form-group">
          <div className="col-md-3 sm-label-right">Resize to</div>
          <div className="col-md-5">
            <div className="horizontal middle">
              <FormikFormField
                name={field}
                input={(props) => <NumberInput {...props} min={64} />}
                touched={true}
                required={true}
                onChange={(value) => {
                  formik.setFieldValue('min', value);
                  formik.setFieldValue('max', value);
                }}
              />
              <div className="sp-padding-xs-xaxis">{fieldLabel}</div>
            </div>
          </div>
        </div>
      </div>
    );
  }

  public render() {
    const { serverGroup } = this.props;
    const { diskQuota, memory } = serverGroup;
    const { initialValues } = this.state;
    return (
      <>
        <TaskMonitorWrapper monitor={this.state.taskMonitor} />
        <SpinFormik<ICloudFoundryResizeServerGroupValues>
          ref={this.formikRef}
          initialValues={initialValues}
          onSubmit={this.submit}
          render={(formik) => {
            return (
              <>
                <ModalClose dismiss={this.close} />
                <Modal.Header>
                  <Modal.Title>Resize {serverGroup.name}</Modal.Title>
                </Modal.Header>
                <Modal.Body>
                  <Form className="form-horizontal">
                    {this.renderDesired(formik)}
                    {this.renderQuota(formik, 'diskQuota', 'Disk (MB)', diskQuota)}
                    {this.renderQuota(formik, 'memory', 'Mem (MB)', memory)}
                    <TaskReason reason={formik.values.reason} onChange={(val) => formik.setFieldValue('reason', val)} />
                  </Form>
                </Modal.Body>
                <ModalFooter>
                  <button className="btn btn-default" onClick={this.close}>
                    Cancel
                  </button>
                  <button
                    type="submit"
                    className="btn btn-primary"
                    onClick={() => this.submit(formik.values)}
                    disabled={!formik.isValid}
                  >
                    Submit
                  </button>
                </ModalFooter>
              </>
            );
          }}
        />
      </>
    );
  }
}
