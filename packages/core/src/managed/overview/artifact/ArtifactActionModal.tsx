import React from 'react';

import { Illustration } from '@spinnaker/presentation';

import { GitLink } from './GitLink';
import { LabeledValue } from '../../../presentation';
import { logger } from '../../../utils';
import type { ICurrentVersion, IVersionDetails, IVersionRelativeAgeToCurrent } from './utils';
import { extractVersionRollbackDetails } from './utils';
import type { IArtifactActionModalProps } from '../../utils/ActionModal';
import { ActionModal } from '../../utils/ActionModal';
import { getDocsUrl } from '../../utils/defaults';

import './ArtifactActionModal.less';

const MARK_BAD_DOCS_URL = getDocsUrl('markAsBad');
const PINNING_DOCS_URL = getDocsUrl('pinning');

const CLASS_NAME = 'ArtifactActionModal';

export interface IVersionActionsProps {
  application: string;
  environment: string;
  reference: string;
  version: string;
  selectedVersion: IVersionDetails;
  isPinned: boolean;
  isVetoed?: boolean;
  isCurrent?: boolean;
}

const VersionDetails = ({ buildNumber, commitMessage, commitSha, title }: IVersionDetails & { title: string }) => {
  return (
    <div className="sp-margin-xl-top">
      <div className="uppercase bold">{title}</div>
      <dl className="details sp-margin-s-top">
        {commitMessage && (
          <LabeledValue
            label="Commit"
            value={
              <GitLink gitMetadata={{ commitInfo: { message: commitMessage }, commit: commitSha }} asLink={false} />
            }
          />
        )}
        {buildNumber && <LabeledValue label="Build #" value={buildNumber} />}
      </dl>
    </div>
  );
};

type InternalModalProps<T = any> = Omit<IArtifactActionModalProps, 'logCategory'> & {
  actionProps: IVersionActionsProps & T;
};

export const PinActionModal = ({
  actionProps,
  ...props
}: InternalModalProps<
  IVersionActionsProps & { ageRelativeToCurrent: IVersionRelativeAgeToCurrent; currentVersion?: ICurrentVersion }
>) => {
  const {
    application,
    selectedVersion: { buildNumber },
    isCurrent,
    environment,
    ageRelativeToCurrent,
    currentVersion,
  } = actionProps;
  const rollingType = ageRelativeToCurrent === 'NEWER' ? 'forward' : 'back';
  return (
    <ActionModal logCategory="Environments::Artifact" className={CLASS_NAME} {...props}>
      <div className="flex-container-h middle sp-margin-xl-bottom">
        <span className="sp-margin-m-right" style={{ minWidth: 145 }}>
          <Illustration name="pinArtifactVersion" />
        </span>
        <span>
          <p>
            {isCurrent ? (
              `Pinning ensures an environment uses a specific version, even if Spinnaker would've normally deployed a
  different one. New versions won't be deployed until you unpin this version.`
            ) : (
              <>
                Rolling {rollingType} will:
                <ul className="sp-margin-xs-top">
                  <li>Ignore any constraints on this version</li>
                  <li>Deploy this version immediately</li>
                  <li>
                    Pin this version to {environment.toLocaleUpperCase()} so no other versions deploy until you unpin
                  </li>
                </ul>
              </>
            )}
          </p>
          For more information{' '}
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
            check out our documentation
          </a>
          .<p></p>
          {isCurrent ? (
            <p>Version #{buildNumber} is already live and no actions will be take immediately</p>
          ) : (
            <div>
              {currentVersion && (
                <VersionDetails title="Live Version" {...extractVersionRollbackDetails(currentVersion)} />
              )}
              <VersionDetails title={`Rolling ${rollingType} to`} {...actionProps.selectedVersion} />
            </div>
          )}
        </span>
      </div>
    </ActionModal>
  );
};

export const UnpinActionModal = ({ actionProps, ...props }: InternalModalProps) => {
  const { application, environment, isCurrent } = actionProps;
  return (
    <ActionModal logCategory="Environments::Artifact" className={CLASS_NAME} {...props}>
      <div className="flex-container-h middle sp-margin-xl-bottom">
        <span className="sp-margin-m-right" style={{ minWidth: 145 }}>
          <Illustration name="unpinArtifactVersion" />
        </span>
        <span>
          <p>
            When you unpin this version from {environment.toUpperCase()}, Spinnaker will use the latest version that's
            approved for deployment. For more information,{' '}
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
            </a>
            .
          </p>
          <VersionDetails title={isCurrent ? 'Current version' : 'Unpinned version'} {...actionProps.selectedVersion} />
        </span>
      </div>
    </ActionModal>
  );
};

export const MarkAsBadActionModal = ({ actionProps, ...props }: InternalModalProps) => {
  const {
    application,
    isCurrent,
    selectedVersion: { buildNumber },
  } = actionProps;
  const environment = actionProps.environment.toUpperCase();
  return (
    <ActionModal logCategory="Environments::Artifact" className={CLASS_NAME} {...props}>
      <div className="flex-container-h middle sp-margin-xl-bottom">
        <span className="sp-margin-m-right" style={{ minWidth: 145 }}>
          <Illustration name="markArtifactVersionAsBad" />
        </span>
        <span>
          <p>
            {isCurrent
              ? `Spinnaker will immediately deploy the latest version approved for deployment to ${environment}. Version #${buildNumber} will be rejected and will not be deployed again`
              : `This action will reject version ${buildNumber} and Spinnaker will not be deploy it to ${environment}. This will not affect the live version`}
            . For more information,{' '}
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
            </a>
            .
          </p>
          <VersionDetails title="Rejected version" {...actionProps.selectedVersion} />
        </span>
      </div>
    </ActionModal>
  );
};

export const MarkAsGoodActionModal = ({ actionProps, ...props }: InternalModalProps) => {
  return (
    <ActionModal logCategory="Environments::Artifact" className={CLASS_NAME} {...props}>
      <div className="flex-container-h middle sp-margin-xl-bottom">
        <span className="sp-margin-m-right" style={{ minWidth: 145 }}>
          <Illustration name="markArtifactVersionAsGood" />
        </span>
        <span>
          <p>
            This action will allow Spinnaker to deploy this version again. If this is the latest version, it will be
            deployed immediately.
          </p>
          <VersionDetails title="Allowed Version" {...actionProps.selectedVersion} />
        </span>
      </div>
    </ActionModal>
  );
};
