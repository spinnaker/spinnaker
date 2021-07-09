import React, { memo, useState } from 'react';

import { Illustration, IllustrationName } from '@spinnaker/presentation';

import { Button } from './Button';
import { ManagedWriter } from './ManagedWriter';
import { Application } from '../application';
import {
  IModalComponentProps,
  ModalBody,
  ModalFooter,
  ModalHeader,
  showModal,
  ValidationMessage,
} from '../presentation';
import { logger } from '../utils';

const logClick = (label: string, application: string) =>
  logger.log({
    category: 'Environments - toggle application management modal',
    action: `${label} clicked`,
    data: { label: application },
  });

export interface IToggleManagedResourceForApplicationModalProps extends IModalComponentProps {
  application: Application;
}

interface Error {
  data: { error: string; message: string };
}

export const MDRunningStateDescription = () => (
  <>
    <p>
      <span className="bold">
        Careful! You’re about to stop Spinnaker from managing all resources in your application.
      </span>
      This feature should only be used if management is not working properly and manual intervention is required.{' '}
      <a href="https://www.spinnaker.io/guides/user/managed-delivery" target="_blank">
        Check our documentation for more information
      </a>
      .
    </p>
    <p>
      Need to rollback?{' '}
      <a href="https://www.spinnaker.io/guides/user/managed-delivery/pinning/" target="_blank">
        Try pinning a version instead
      </a>
      .
    </p>
  </>
);

export const MDPausedStateDescription = () => {
  return (
    <p>
      You’re about to resume management for this application. The latest good version approved for deployment will be
      deployed to each environment, and any configuration changes made while disabled will take effect.
    </p>
  );
};

const modalViewInfo = {
  paused: {
    actionToBeTaken: 'Resume',
    description: <MDPausedStateDescription />,
    icon: 'runManagement' as IllustrationName,
  },
  running: {
    actionToBeTaken: 'Disable',
    description: <MDRunningStateDescription />,
    icon: 'disableManagement' as IllustrationName,
  },
};

export const showToggleManagedResourceModal = (props: IToggleManagedResourceForApplicationModalProps) =>
  showModal(ToggleManagedResourceForApplicationModal, props, { maxWidth: 800 });

export const ToggleManagedResourceForApplicationModal = memo(
  ({ application, closeModal, dismissModal }: IToggleManagedResourceForApplicationModalProps) => {
    const [isSubmitting, setSubmitting] = useState<boolean>(false);
    const [actionError, setActionError] = useState<{ error: string; message: string } | null>(null);
    const dataSource = application.getDataSource('environments');

    const { actionToBeTaken, description, icon } = modalViewInfo[application.isManagementPaused ? 'paused' : 'running'];

    const handleActionInitiation = () => {
      setActionError(null);
      setSubmitting(true);
      logClick(`${actionToBeTaken} Management`, application.name);
      const call = application.isManagementPaused
        ? ManagedWriter.resumeApplicationManagement(application.name)
        : ManagedWriter.pauseApplicationManagement(application.name);
      call
        .then(() => dataSource.refresh(true).catch(() => null))
        .then(() => {
          closeModal?.();
        })
        .catch((error: Error) => {
          setActionError(error.data);
          setSubmitting(false);
        });
    };
    return (
      <>
        <ModalHeader>{actionToBeTaken} management</ModalHeader>
        <ModalBody>
          <div className="flex-container-h sp-padding-xl-yaxis">
            <div style={{ minWidth: 145 }}>
              <Illustration name={icon} />
            </div>
            <div className="sp-padding-xl">
              <div>{description}</div>
              {actionError && (
                <div className="sp-margin-xl-top">
                  <ValidationMessage
                    type="error"
                    message={
                      <span className="flex-container-v">
                        <span className="text-bold">Something went wrong:</span>
                        {actionError.error && <span className="text-semibold">{actionError.error}</span>}
                        {actionError.message && <span>{actionError.message}</span>}
                      </span>
                    }
                  />
                </div>
              )}
            </div>
          </div>
        </ModalBody>
        <ModalFooter
          primaryActions={
            <div className="flex-container-h sp-group-margin-s-xaxis">
              <Button onClick={dismissModal}>Cancel</Button>
              <Button appearance="primary" disabled={isSubmitting} onClick={handleActionInitiation}>
                {actionToBeTaken}
              </Button>
            </div>
          }
        />
      </>
    );
  },
);
