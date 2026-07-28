import type { FormikProps } from 'formik';
import { Form } from 'formik';
import React from 'react';
import { Button, Modal } from 'react-bootstrap';

import type { Application, IModalComponentProps, IModalProps } from '@spinnaker/core';
import {
  ConfirmationModalService,
  ManifestWriter,
  ModalClose,
  robotToHuman,
  SpinFormik,
  SubmitButton,
  TaskReason,
} from '@spinnaker/core';

import type { IManifestCoordinates } from '../IManifestCoordinates';
import type { IScaleCommand } from './ScaleSettingsForm';
import { ScaleSettingsForm } from './ScaleSettingsForm';

export interface IKubernetesScaleModalProps
  extends Pick<IModalProps, 'isOpen'>,
    Pick<IModalComponentProps, 'dismissModal'> {
  application: Application;
  coordinates: IManifestCoordinates;
  currentReplicas: number;
}

export interface IKubernetesScaleValues {
  reason?: string;
  replicas: number;
}

const REPLICA_SCALE_FACTOR_WARNING = 5;

export function ScaleModal({
  application,
  coordinates,
  currentReplicas,
  isOpen,
  dismissModal,
}: IKubernetesScaleModalProps) {
  const initialValues: IKubernetesScaleValues = {
    replicas: currentReplicas,
  };

  const submit = (values: IKubernetesScaleValues): void => {
    dismissModal();

    let body = `Are you <i>really</i> sure you want to scale ${coordinates.name} in ${coordinates.account} to ${values.replicas} replicas?`;
    if (values.replicas === 0) {
      body +=
        ' <br><br><span style="color:red"><b>If this service does not have replicas in another cluster, this WILL cause an outage.</b></span>';
    } else if (currentReplicas > 0 && values.replicas >= currentReplicas * REPLICA_SCALE_FACTOR_WARNING) {
      body += ` <br><br><span style="color:red"><b>This will create more than ${REPLICA_SCALE_FACTOR_WARNING} times the current number of replicas.</b></span>`;
    }

    ConfirmationModalService.confirm({
      header: `Really scale ${coordinates.name} in ${coordinates.account}?`,
      body,
      taskMonitorConfig: {
        application,
        title: `Scaling ${coordinates.name} in ${coordinates.account}`,
        onTaskComplete: () => application.serverGroups.refresh(true),
      },
      verificationLabel: 'Enter the name of the service to confirm',
      textToVerify: application.name,
      submitMethod: () => {
        const payload = {
          cloudProvider: 'kubernetes',
          manifestName: coordinates.name,
          location: coordinates.namespace,
          account: coordinates.account,
          reason: values.reason,
          replicas: values.replicas,
        };
        return ManifestWriter.scaleManifest(payload, application);
      },
    });
  };

  const onOptionChange = (formik: FormikProps<IKubernetesScaleValues>, values: IScaleCommand): void => {
    formik.setFieldValue('replicas', values.replicas);
  };

  return (
    <Modal show={isOpen} onHide={dismissModal}>
      <SpinFormik<IKubernetesScaleValues>
        initialValues={initialValues}
        onSubmit={submit}
        render={(formik) => (
          <>
            <ModalClose dismiss={dismissModal} />
            <Modal.Header>
              <Modal.Title>
                Scale {robotToHuman(coordinates.name)} in {coordinates.namespace}
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
              <Button onClick={dismissModal}>Cancel</Button>
              <SubmitButton
                onClick={() => submit(formik.values)}
                isDisabled={formik.isSubmitting}
                isFormSubmit={true}
                submitting={formik.isSubmitting}
                label="Submit"
              />
            </Modal.Footer>
          </>
        )}
      />
    </Modal>
  );
}
