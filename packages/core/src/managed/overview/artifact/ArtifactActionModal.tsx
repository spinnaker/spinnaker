import React from 'react';

import { Illustration } from '@spinnaker/presentation';

import { MarkAsBadIntro } from '../../artifactDetail/MarkArtifactAsBadModal';
import { PinVersionIntro } from '../../artifactDetail/PinArtifactModal';
import { UnpinVersionIntro } from '../../artifactDetail/UnpinArtifactModal';
import { ActionModal, IArtifactActionModalProps } from '../../utils/ActionModal';

type InternalModalProps = Omit<IArtifactActionModalProps, 'logCategory'> & { application: string };

export const PinActionModal = ({ application, ...props }: InternalModalProps) => {
  return (
    <ActionModal logCategory="Environments::Artifact" {...props}>
      <PinVersionIntro application={application} />
    </ActionModal>
  );
};

export const UnpinActionModal = ({
  application,
  environment,
  ...props
}: InternalModalProps & { environment: string }) => {
  return (
    <ActionModal logCategory="Environments::Artifact" {...props}>
      <UnpinVersionIntro application={application} environment={environment} />
    </ActionModal>
  );
};

export const MarkAsBadActionModal = ({ application, ...props }: InternalModalProps) => {
  return (
    <ActionModal logCategory="Environments::Artifact" {...props}>
      <MarkAsBadIntro application={application} />
    </ActionModal>
  );
};

const MarkAsGoodIntro = () => (
  <div className="flex-container-h middle sp-margin-xl-bottom">
    <span className="sp-margin-m-right" style={{ minWidth: 145 }}>
      <Illustration name="markArtifactVersionAsGood" />
    </span>
    <span>
      <p>
        By marking this version as good, Spinnaker will be able to deploy it again. If this is the latest version, it
        will be deployed immediately.
      </p>
    </span>
  </div>
);

export const MarkAsGoodActionModal = ({ application, ...props }: InternalModalProps) => {
  return (
    <ActionModal logCategory="Environments::Artifact" {...props}>
      <MarkAsGoodIntro />
    </ActionModal>
  );
};
