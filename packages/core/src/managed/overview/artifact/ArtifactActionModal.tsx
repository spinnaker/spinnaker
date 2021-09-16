import React from 'react';

import { Illustration } from '@spinnaker/presentation';
import { logger } from '../../../utils';
import { ActionModal, IArtifactActionModalProps } from '../../utils/ActionModal';
import { getDocsUrl } from '../../utils/defaults';

type InternalModalProps = Omit<IArtifactActionModalProps, 'logCategory'> & { application: string };

const MARK_BAD_DOCS_URL = getDocsUrl('markAsBad');
const PINNING_DOCS_URL = getDocsUrl('pinning');

export const PinActionModal = ({ application, ...props }: InternalModalProps) => {
  return (
    <ActionModal logCategory="Environments::Artifact" {...props}>
      <div className="flex-container-h middle sp-margin-xl-bottom">
        <span className="sp-margin-m-right" style={{ minWidth: 145 }}>
          <Illustration name="pinArtifactVersion" />
        </span>
        <span>
          <p>
            Pinning ensures an environment uses a specific version, even if Spinnaker would've normally deployed a
            different one. If you pin a version, it'll remain pinned until you manually unpin it.
          </p>{' '}
          <a
            target="_blank"
            onClick={() =>
              logger.log({
                category: 'Environments - pin version modal',
                action: 'Pinning docs link clicked',
                data: { label: application, application },
              })
            }
            href={PINNING_DOCS_URL}
          >
            Check out our documentation
          </a>{' '}
          for more information.
        </span>
      </div>
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
      <div className="flex-container-h middle sp-margin-xl-bottom">
        <span className="sp-margin-m-right" style={{ minWidth: 145 }}>
          <Illustration name="unpinArtifactVersion" />
        </span>
        <span>
          <p>
            When you unpin this version from {environment.toUpperCase()}, Spinnaker will use the latest version that's
            approved for deployment.
          </p>{' '}
          <a
            target="_blank"
            onClick={() =>
              logger.log({
                category: 'Environments - unpin version modal',
                action: 'Pinning docs link clicked',
                data: { label: application, application },
              })
            }
            href={PINNING_DOCS_URL}
          >
            Check out our documentation
          </a>{' '}
          for more information.
        </span>
      </div>
    </ActionModal>
  );
};

export const MarkAsBadActionModal = ({ application, ...props }: InternalModalProps) => {
  return (
    <ActionModal logCategory="Environments::Artifact" {...props}>
      <div className="flex-container-h middle sp-margin-xl-bottom">
        <span className="sp-margin-m-right" style={{ minWidth: 145 }}>
          <Illustration name="markArtifactVersionAsBad" />
        </span>
        <span>
          <p>
            If you mark a version as bad in an environment, Spinnaker will never deploy it there. If the version is
            already deployed there, Spinnaker will immediately replace it with the latest good version approved for
            deployment.
          </p>{' '}
          <a
            target="_blank"
            onClick={() =>
              logger.log({
                category: 'Environments - mark version as bad modal',
                action: 'Mark as bad docs link clicked',
                data: { label: application, application },
              })
            }
            href={MARK_BAD_DOCS_URL}
          >
            Check out our documentation
          </a>{' '}
          for more information.
        </span>
      </div>
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
