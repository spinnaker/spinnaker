import React from 'react';
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
