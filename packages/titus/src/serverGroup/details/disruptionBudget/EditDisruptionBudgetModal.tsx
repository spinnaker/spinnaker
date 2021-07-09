import React from 'react';
import { Modal } from 'react-bootstrap';

import {
  Application,
  IJob,
  IModalComponentProps,
  ModalClose,
  ReactModal,
  SpinFormik,
  SubmitButton,
  TaskExecutor,
  TaskMonitor,
  TaskMonitorWrapper,
} from '@spinnaker/core';

import { ITitusServerGroupCommand } from '../../configure/serverGroupConfiguration.service';
import { JobDisruptionBudget } from '../../configure/wizard/pages/disruptionBudget/JobDisruptionBudget';
import { ITitusServerGroup } from '../../../domain';

export interface IEditDisruptionBudgetModalProps extends IModalComponentProps {
  application: Application;
  command: ITitusServerGroupCommand;
  serverGroup: ITitusServerGroup;
}

export class EditDisruptionBudgetModal extends React.Component<IEditDisruptionBudgetModalProps> {
  public static show(props: IEditDisruptionBudgetModalProps): Promise<void> {
    return ReactModal.show(EditDisruptionBudgetModal, props);
  }

  private submit(values: ITitusServerGroupCommand, taskMonitor: TaskMonitor): void {
    const { application, serverGroup } = this.props;
    const job: IJob = {
      type: 'upsertDisruptionBudget',
      cloudProvider: 'titus',
      credentials: serverGroup.account,
      region: serverGroup.region,
      jobId: serverGroup.id,
      disruptionBudget: values.disruptionBudget,
    };

    taskMonitor.submit(() =>
      TaskExecutor.executeTask({
        job: [job],
        application,
        description: `Update Disruption Budget for ${serverGroup.name}`,
      }),
    );
  }

  public render() {
    const { application, command, dismissModal } = this.props;
    const taskMonitor = new TaskMonitor({
      application,
      title: 'Updating Job Disruption Budget',
      modalInstance: TaskMonitor.modalInstanceEmulation(() => dismissModal()),
      onTaskComplete: () => application.serverGroups.refresh(),
    });

    return (
      <>
        <TaskMonitorWrapper monitor={taskMonitor} />
        <SpinFormik<ITitusServerGroupCommand>
          initialValues={command}
          onSubmit={(values: ITitusServerGroupCommand) => this.submit(values, taskMonitor)}
          render={(formik) => (
            <>
              <ModalClose dismiss={dismissModal} />
              <Modal.Header>
                <Modal.Title>Update Disruption Budget</Modal.Title>
              </Modal.Header>
              <Modal.Body>
                <JobDisruptionBudget formik={formik} app={application} />
              </Modal.Body>
              <Modal.Footer>
                <button className="btn btn-default" onClick={dismissModal} type="button">
                  Cancel
                </button>
                <SubmitButton
                  onClick={() => this.submit(formik.values, taskMonitor)}
                  isDisabled={!formik.isValid}
                  isFormSubmit={true}
                  submitting={false}
                  label="Update Budget"
                />
              </Modal.Footer>
            </>
          )}
        />
      </>
    );
  }
}
