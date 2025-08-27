import { UISref } from '@uirouter/react';
import { UIRouterContextComponent } from '@uirouter/react-hybrid';
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
  ValidationMessage,
} from '@spinnaker/core';

import type { IDeleteOptions } from './delete.controller';
import type { IAnyKubernetesResource } from '../../interfaces';
import DeleteManifestOptionsForm from '../../pipelines/stages/deleteManifest/DeleteManifestOptionsForm';

export interface IKubernetesDeleteModalProps
  extends Pick<IModalProps, 'isOpen'>,
    Pick<IModalComponentProps, 'dismissModal'> {
  application: Application;
  resource: IAnyKubernetesResource;
  manifestController: string | undefined;
}

export interface IKubernetesDeleteValues extends IDeleteOptions {
  reason?: string;
}

export function DeleteModal({
  application,
  resource,
  manifestController,
  isOpen,
  dismissModal,
}: IKubernetesDeleteModalProps) {
  const initialValues: IKubernetesDeleteValues = {
    cascading: true,
  };
  const [verified, setVerified] = useState<boolean>(false);
  const taskMonitor = useTaskMonitor(
    {
      application,
      title: `Deleting ${resource.name}`,
      onTaskComplete: () => application.serverGroups.refresh(true),
    },
    dismissModal,
  );

  const submit = (values: IKubernetesDeleteValues): void => {
    const payload = {
      cloudProvider: 'kubernetes',
      manifestName: resource.name,
      location: resource.namespace,
      account: resource.account,
      reason: values.reason,
      options: {
        gracePeriodSeconds: values.gracePeriodSeconds,
        orphanDependants: !values.cascading,
      },
    };
    return taskMonitor.submit(() => ManifestWriter.deleteManifest(payload, application));
  };

  const onOptionChange = (formik: FormikProps<IKubernetesDeleteValues>, values: IKubernetesDeleteValues): void => {
    formik.setFieldValue('gracePeriodSeconds', values.gracePeriodSeconds);
    formik.setFieldValue('cascading', values.cascading);
  };

  return (
    <UIRouterContextComponent>
      <Modal show={isOpen} onHide={dismissModal}>
        <TaskMonitorWrapper monitor={taskMonitor} />
        <SpinFormik<IKubernetesDeleteValues>
          initialValues={initialValues}
          onSubmit={submit}
          render={(formik) => (
            <>
              <ModalClose dismiss={dismissModal} />
              <Modal.Header>
                <Modal.Title>
                  Delete {robotToHuman(resource.name)} in {resource.namespace}
                </Modal.Title>
              </Modal.Header>
              {manifestController && (
                <div className="alert alert-warning">
                  Manifest is controlled by{' '}
                  <UISref
                    to="^.serverGroupManager"
                    params={{
                      accountId: resource.account,
                      region: resource.region,
                      serverGroupManager: manifestController,
                      provider: 'kubernetes',
                    }}
                  >
                    <a> {robotToHuman(manifestController)} </a>
                  </UISref>{' '}
                  and may be recreated after deletion.
                </div>
              )}
              <Modal.Body>
                <Form className="form-horizontal">
                  <DeleteManifestOptionsForm
                    onOptionsChange={(options: IDeleteOptions) => onOptionChange(formik, options)}
                    options={{
                      gracePeriodSeconds: formik.values.gracePeriodSeconds,
                      cascading: formik.values.cascading,
                    }}
                  />
                  <TaskReason reason={formik.values.reason} onChange={(val) => formik.setFieldValue('reason', val)} />
                </Form>
                {formik.status?.error && (
                  <div className="sp-margin-xl-top">
                    <ValidationMessage
                      type="error"
                      message={
                        <span className="flex-container-v">
                          <span className="text-bold">Something went wrong:</span>
                          {formik.status.error.message && <span>{formik.status.error.message}</span>}
                        </span>
                      }
                    />
                  </div>
                )}
              </Modal.Body>
              <Modal.Footer>
                <UserVerification account={resource.account} onValidChange={setVerified} />
                <Button onClick={dismissModal}>Cancel</Button>
                <SubmitButton
                  onClick={() => submit(formik.values)}
                  isDisabled={!formik.isValid || formik.isSubmitting || !verified}
                  isFormSubmit={true}
                  submitting={formik.isSubmitting}
                  label={`Delete ${resource.name}`}
                />
              </Modal.Footer>
            </>
          )}
        />
      </Modal>
    </UIRouterContextComponent>
  );
}
