import { Form } from 'formik';
import { orderBy } from 'lodash';
import React, { useState } from 'react';
import { Button, Modal } from 'react-bootstrap';
import type { Option } from 'react-select';

import type { Application, IModalComponentProps, IModalProps, IServerGroupManager } from '@spinnaker/core';
import {
  ManifestWriter,
  ModalClose,
  NameUtils,
  ReactSelectInput,
  robotToHuman,
  SpinFormik,
  SubmitButton,
  TaskMonitorWrapper,
  TaskReason,
  UserVerification,
  useTaskMonitor,
} from '@spinnaker/core';

import type { IAnyKubernetesResource } from '../../interfaces';
import type { IRolloutRevision } from './undo.controller';

export interface IKubernetesUndoRolloutModalProps
  extends Pick<IModalProps, 'isOpen'>,
    Pick<IModalComponentProps, 'dismissModal'> {
  application: Application;
  resource: IAnyKubernetesResource;
}

export interface IKubernetesUndoRolloutValues {
  revision?: number;
  reason?: string;
}

const fetchRevisions = (serverGroupManager: IServerGroupManager): IRolloutRevision[] => {
  const [, ...rest] = orderBy(serverGroupManager.serverGroups, ['moniker.sequence'], ['desc']);
  return rest.map((serverGroup, index) => ({
    label: `${NameUtils.getSequence(serverGroup.moniker.sequence)}${index > 0 ? '' : ' - previous revision'}`,
    revision: serverGroup.moniker.sequence,
  }));
};

export function UndoRolloutModal({ application, resource, isOpen, dismissModal }: IKubernetesUndoRolloutModalProps) {
  const initialValues: IKubernetesUndoRolloutValues = {};
  const revisions = fetchRevisions(resource as IServerGroupManager);
  const [verified, setVerified] = useState<boolean>(false);
  const taskMonitor = useTaskMonitor(
    {
      application,
      title: `Undo rollout of ${resource.name} in ${resource.namespace}`,
      onTaskComplete: () => application.serverGroups.refresh(true),
    },
    dismissModal,
  );

  const submit = (values: IKubernetesUndoRolloutValues): void => {
    const payload = {
      cloudProvider: 'kubernetes',
      manifestName: resource.name,
      location: resource.namespace,
      account: resource.account,
      reason: values.reason,
      revision: values.revision,
    };
    return taskMonitor.submit(() => ManifestWriter.undoRolloutManifest(payload, application));
  };

  return (
    <Modal show={isOpen} onHide={dismissModal}>
      <TaskMonitorWrapper monitor={taskMonitor} />
      <SpinFormik<IKubernetesUndoRolloutValues>
        initialValues={initialValues}
        onSubmit={submit}
        render={(formik) => (
          <>
            <ModalClose dismiss={dismissModal} />
            <Modal.Header>
              <Modal.Title>
                Undo rollout of {robotToHuman(resource.name)} in {resource.namespace}
              </Modal.Title>
            </Modal.Header>
            <Modal.Body>
              <Form className="form-horizontal">
                <div className="form-group form-inline">
                  <label className="col-md-3 sm-label-right">
                    <span className="label-text">Revision </span>
                  </label>
                  <div className="col-md-7">
                    <ReactSelectInput<number>
                      clearable={false}
                      value={formik.values.revision}
                      options={revisions.map((revision) => ({
                        label: revision.label,
                        value: revision.revision,
                      }))}
                      onChange={(option: Option<number>) => {
                        formik.setFieldValue('revision', option.target.value);
                      }}
                    />
                  </div>
                </div>
                <TaskReason reason={formik.values.reason} onChange={(val) => formik.setFieldValue('reason', val)} />
              </Form>
            </Modal.Body>
            <Modal.Footer>
              <UserVerification account={resource.account} onValidChange={setVerified} />
              <Button onClick={dismissModal}>Cancel</Button>
              <SubmitButton
                onClick={() => submit(formik.values)}
                isDisabled={!formik.isValid || formik.isSubmitting || !verified}
                isFormSubmit={true}
                submitting={formik.isSubmitting}
                label={`Undo rollout of ${resource.name}`}
              />
            </Modal.Footer>
          </>
        )}
      />
    </Modal>
  );
}
