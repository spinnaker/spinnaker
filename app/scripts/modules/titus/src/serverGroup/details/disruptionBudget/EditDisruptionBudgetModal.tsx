import * as React from 'react';
import { Modal } from 'react-bootstrap';
import { Formik } from 'formik';

import {
  Application,
  TaskMonitor,
  IJob,
  IModalComponentProps,
  ModalClose,
  SubmitButton,
  NgReact,
  ReactModal,
  TaskExecutor,
} from '@spinnaker/core';

import { JobDisruptionBudget } from '../../configure/wizard/pages/disruptionBudget/JobDisruptionBudget';
import { ITitusServerGroupCommand } from '../../configure/serverGroupConfiguration.service';
import { ITitusServerGroup } from 'titus/domain';

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
    const { TaskMonitorWrapper } = NgReact;
    const taskMonitor = new TaskMonitor({
      application,
      title: 'Updating Job Disruption Budget',
      modalInstance: TaskMonitor.modalInstanceEmulation(() => dismissModal()),
      onTaskComplete: () => application.serverGroups.refresh(),
    });

    return (
      <>
        <TaskMonitorWrapper monitor={taskMonitor} />
        <Formik<ITitusServerGroupCommand>
          initialValues={command}
          isInitialValid={true}
          onSubmit={(values: ITitusServerGroupCommand) => this.submit(values, taskMonitor)}
          render={formik => (
            <>
              <Modal.Header>
                <h3>Update Disruption Budget</h3>
              </Modal.Header>
              <ModalClose dismiss={dismissModal} />
              <Modal.Body>
                <JobDisruptionBudget formik={formik} />
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
