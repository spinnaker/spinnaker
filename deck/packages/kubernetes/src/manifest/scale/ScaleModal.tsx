import type { FormikProps } from 'formik';
import { Form } from 'formik';
import React, { useState } from 'react';
import { Button, Modal } from 'react-bootstrap';

import type { Application, IModalComponentProps, IModalProps } from '@spinnaker/core';
import {
  ManifestWriter,
  ModalClose,
  robotToHuman,
  SpinFormik,
  SubmitButton,
  TaskMonitorWrapper,
  TaskReason,
  UserVerification,
  useTaskMonitor,
} from '@spinnaker/core';

import { ScaleSettingsForm } from './ScaleSettingsForm';
import type { IAnyKubernetesResource } from '../../interfaces';
import type { IScaleCommand } from './scale.controller';

export interface IKubernetesScaleModalProps
  extends Pick<IModalProps, 'isOpen'>,
    Pick<IModalComponentProps, 'dismissModal'> {
  application: Application;
  resource: IAnyKubernetesResource;
  currentReplicas: number;
}

export interface IKubernetesScaleValues {
  reason?: string;
  replicas: number;
}

export function ScaleModal({
  application,
  resource,
  currentReplicas,
  isOpen,
  dismissModal,
}: IKubernetesScaleModalProps) {
  const initialValues: IKubernetesScaleValues = {
    replicas: currentReplicas,
  };
  const [verified, setVerified] = useState<boolean>(false);
  const taskMonitor = useTaskMonitor(
    {
      application,
      title: `Scaling ${resource.name} in ${resource.namespace}`,
      onTaskComplete: () => application.serverGroups.refresh(true),
    },
    dismissModal,
  );

  const submit = (values: IKubernetesScaleValues): void => {
    const payload = {
      cloudProvider: 'kubernetes',
      manifestName: resource.name,
      location: resource.namespace,
      account: resource.account,
      reason: values.reason,
      replicas: values.replicas,
    };
    return taskMonitor.submit(() => ManifestWriter.scaleManifest(payload, application));
  };

  const onOptionChange = (formik: FormikProps<IKubernetesScaleValues>, values: IScaleCommand): void => {
    formik.setFieldValue('replicas', values.replicas);
  };

  return (
    <Modal show={isOpen} onHide={dismissModal}>
      <TaskMonitorWrapper monitor={taskMonitor} />
      <SpinFormik<IKubernetesScaleValues>
        initialValues={initialValues}
        onSubmit={submit}
        render={(formik) => (
          <>
            <ModalClose dismiss={dismissModal} />
            <Modal.Header>
              <Modal.Title>
                Scale {robotToHuman(resource.name)} in {resource.namespace}
              </Modal.Title>
            </Modal.Header>
            <Modal.Body>
              <Form className="form-horizontal">
                <ScaleSettingsForm
                  onChange={(options: IScaleCommand) => onOptionChange(formik, options)}
                  options={
                    ({
                      replicas: formik.values.replicas,
                    } as unknown) as IScaleCommand
                  }
                />
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
                label={`Scale ${resource.name}`}
              />
            </Modal.Footer>
          </>
        )}
      />
    </Modal>
  );
}
