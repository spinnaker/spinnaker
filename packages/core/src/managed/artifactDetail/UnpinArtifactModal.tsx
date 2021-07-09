import React, { memo, useEffect, useState } from 'react';

import { Illustration } from '@spinnaker/presentation';

import { Button } from '../Button';
import { ManagedWriter } from '../ManagedWriter';
import { Application } from '../../application';
import { getArtifactVersionDisplayName } from '../displayNames';
import { IManagedArtifactVersion, IManagedResourceSummary } from '../../domain';
import {
  IModalComponentProps,
  IRequestStatus,
  ModalBody,
  ModalFooter,
  ModalHeader,
  showModal,
  ValidationMessage,
} from '../../presentation';
import { logger } from '../../utils';

const PINNING_DOCS_URL = 'https://www.spinnaker.io/guides/user/managed-delivery/pinning';

const logEvent = (label: string, application: string, environment?: string, reference?: string) =>
  logger.log({
    category: 'Environments - unpin version modal',
    action: label,
    data: { label: environment ? `${application}:${environment}:${reference}` : application },
  });

export const UnpinVersionIntro = ({ application, environment }: { application: string; environment: string }) => (
  <div className="flex-container-h middle sp-margin-xl-bottom">
    <span className="sp-margin-m-right" style={{ minWidth: 145 }}>
      <Illustration name="unpinArtifactVersion" />
    </span>
    <span>
      <p>
        When you unpin this version from {environment.toUpperCase()}, Spinnaker will use the latest version that's
        approved for deployment.
      </p>{' '}
      <a target="_blank" onClick={() => logEvent('Pinning docs link clicked', application)} href={PINNING_DOCS_URL}>
        Check out our documentation
      </a>{' '}
      for more information.
    </span>
  </div>
);

export interface IUnpinArtifactModalProps extends IModalComponentProps {
  application: Application;
  reference: string;
  version: IManagedArtifactVersion;
  resourcesByEnvironment: { [environment: string]: IManagedResourceSummary[] };
  environment: string;
}

export const showUnpinArtifactModal = (props: IUnpinArtifactModalProps) =>
  showModal(UnpinArtifactModal, props, { maxWidth: 628 }).then((result) => {
    if (result.status === 'DISMISSED') {
      logEvent('Modal dismissed', props.application.name);
    }
    return result;
  });

export const UnpinArtifactModal = memo(
  ({ application, reference, version, environment, dismissModal, closeModal }: IUnpinArtifactModalProps) => {
    const [submitStatus, setSubmitStatus] = useState<IRequestStatus>('NONE');
    const [error, setError] = useState<{ title: string; message: string } | undefined>(undefined);

    useEffect(() => logEvent('Modal seen', application.name), []);

    const submit = () => {
      setSubmitStatus('PENDING');

      ManagedWriter.unpinArtifactVersion({
        environment,
        reference,
        application: application.name,
      })
        .then(() => {
          logEvent('Version unpinned', application.name, environment, reference);
          closeModal?.();
        })
        .catch((error: { data: { error: string; message: string } }) => {
          setSubmitStatus('REJECTED');
          setError({ title: error.data?.error, message: error.data.message });
          logEvent('Error unpinning version', application.name, environment, reference);
        });
    };

    return (
      <>
        <ModalHeader>Unpin {getArtifactVersionDisplayName(version)}</ModalHeader>
        <ModalBody>
          <div className="flex-container-v middle sp-padding-xl-yaxis">
            <UnpinVersionIntro application={application.name} environment={environment} />

            {error && (
              <ValidationMessage
                type="error"
                message={
                  <span className="flex-container-v">
                    <span className="text-bold">Something went wrong:</span>
                    {error.title && <span className="text-semibold">{error.title}</span>}
                    {error.message && <span>{error.message}</span>}
                  </span>
                }
              />
            )}
          </div>
        </ModalBody>
        <ModalFooter
          primaryActions={
            <div className="flex-container-h sp-group-margin-s-xaxis">
              <Button onClick={() => dismissModal?.()}>Cancel</Button>
              <Button appearance="primary" disabled={submitStatus === 'PENDING'} onClick={() => submit()}>
                Unpin
              </Button>
            </div>
          }
        />
      </>
    );
  },
);
