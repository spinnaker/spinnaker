import { DateTime } from 'luxon';
import React, { useMemo } from 'react';

import { AbsoluteTimestamp } from '../AbsoluteTimestamp';
import { ArtifactDetailHeader } from './ArtifactDetailHeader';
import { Button } from '../Button';
import { EnvironmentCards } from './EnvironmentCards';
import { ManagedResourceObject } from '../ManagedResourceObject';
import { showMarkArtifactAsBadModal } from './MarkArtifactAsBadModal';
import { showPinArtifactModal } from './PinArtifactModal';
import { PreDeploymentRow } from './PreDeploymentRow';
import { PreDeploymentStepCard } from './PreDeploymentStepCard';
import { Application } from '../../application';
import {
  IManagedArtifactSummary,
  IManagedArtifactVersion,
  IManagedEnvironmentSummary,
  IManagedResourceSummary,
} from '../../domain';
import { EnvironmentRow } from '../environment/EnvironmentRow';
import { CollapsibleElement, Markdown, useEventListener } from '../../presentation';
import { resourceManager } from '../resources/resourceRegistry';
import { logCategories, useLogEvent } from '../utils/logging';

import './ArtifactDetail.less';

const SUPPORTED_PRE_DEPLOYMENT_TYPES = ['BUILD', 'BAKE'];

function shouldDisplayResource(reference: string, resource: IManagedResourceSummary) {
  return resourceManager.isSupported(resource.kind) && reference === resource.artifact?.reference;
}

const VersionMetadataItem = ({ label, value }: { label: string; value: JSX.Element | string }) => (
  <div className="flex-container-h sp-margin-xs-bottom">
    <div className="metadata-label text-bold text-right sp-margin-l-right flex-none">{label}</div>
    <CollapsibleElement maxHeight={150}>{value}</CollapsibleElement>
  </div>
);

export interface IArtifactDetailProps {
  application: Application;
  name: string;
  reference: string;
  version: IManagedArtifactVersion;
  allVersions: IManagedArtifactSummary['versions'];
  allEnvironments: IManagedEnvironmentSummary[];
  showReferenceNames: boolean;
  resourcesByEnvironment: { [environment: string]: IManagedResourceSummary[] };
  onRequestClose: () => any;
}

export const ArtifactDetail = ({
  application,
  reference,
  version: versionDetails,
  allVersions,
  allEnvironments,
  showReferenceNames,
  resourcesByEnvironment,
  onRequestClose,
}: IArtifactDetailProps) => {
  const { environments, lifecycleSteps, git, createdAt } = versionDetails;
  const logEvent = useLogEvent(logCategories.artifactDetails);

  const keydownCallback = ({ key }: KeyboardEvent) => {
    if (key === 'Escape') {
      onRequestClose();
    }
  };
  useEventListener(document, 'keydown', keydownCallback);

  const isPinnedEverywhere = environments.every(({ pinned }) => pinned);
  const isBadEverywhere = environments.every(({ state }) => state === 'vetoed');
  const createdAtTimestamp = useMemo(() => createdAt && DateTime.fromISO(createdAt), [createdAt]);

  // These steps come in with chronological ordering, but we need reverse-chronological orddering for display
  const preDeploymentSteps = lifecycleSteps
    ?.filter(({ scope, type }) => scope === 'PRE_DEPLOYMENT' && SUPPORTED_PRE_DEPLOYMENT_TYPES.includes(type))
    .reverse();

  const getPinnedVersion = (environmentName: string) => {
    const envData = allEnvironments.find(({ name }) => name === environmentName);
    const artifactData = envData?.artifacts.find(({ reference: referenceToMatch }) => referenceToMatch === reference);
    return artifactData?.pinnedVersion;
  };

  return (
    <>
      <ArtifactDetailHeader
        reference={showReferenceNames ? reference : undefined}
        version={versionDetails}
        onRequestClose={onRequestClose}
      />

      <div className="ArtifactDetail flex-grow">
        <div className="flex-container-h top sp-margin-xl-bottom">
          <div className="flex-container-h sp-group-margin-s-xaxis flex-none">
            <Button
              iconName="pin"
              appearance="primary"
              disabled={isPinnedEverywhere || isBadEverywhere}
              onClick={() =>
                showPinArtifactModal({ application, reference, version: versionDetails, resourcesByEnvironment }).then(
                  ({ status }) => status === 'CLOSED' && application.getDataSource('environments').refresh(),
                )
              }
            >
              Pin...
            </Button>
            <Button
              iconName="artifactBad"
              appearance="primary"
              disabled={isPinnedEverywhere || isBadEverywhere}
              onClick={() =>
                showMarkArtifactAsBadModal({
                  application,
                  reference,
                  version: versionDetails,
                  resourcesByEnvironment,
                }).then(({ status }) => status === 'CLOSED' && application.getDataSource('environments').refresh())
              }
            >
              Mark as bad...
            </Button>
          </div>
          <div className="detail-section-right flex-container-v flex-pull-right sp-margin-l-right">
            {createdAtTimestamp && (
              <VersionMetadataItem
                label="Created"
                value={<AbsoluteTimestamp timestamp={createdAtTimestamp} clickToCopy={true} />}
              />
            )}
            {git?.author && <VersionMetadataItem label="Author" value={git.author} />}
            {git?.pullRequest?.number && git?.pullRequest?.url && (
              <VersionMetadataItem
                label="Pull Request"
                value={
                  <a
                    href={git.pullRequest.url}
                    onClick={() => logEvent({ action: 'PR link clicked', label: reference })}
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    #{git.pullRequest.number}
                  </a>
                }
              />
            )}
            {git?.commitInfo && (
              <>
                <VersionMetadataItem
                  label="Commit"
                  value={
                    <a
                      href={git.commitInfo.link}
                      onClick={() => logEvent({ action: 'Commit link clicked', label: reference })}
                      target="_blank"
                      rel="noopener noreferrer"
                    >
                      {git.commitInfo.sha.substring(0, 7)}
                    </a>
                  }
                />
                <VersionMetadataItem
                  label="Message"
                  value={
                    <span style={{ wordBreak: 'break-word', whiteSpace: 'pre-wrap' }}>
                      <Markdown message={git.commitInfo.message} tag="span" />
                    </span>
                  }
                />
              </>
            )}
            {git?.branch && <VersionMetadataItem label="Branch" value={git.branch} />}
            {git?.repo && <VersionMetadataItem label="Repository" value={`${git.project}/${git.repo.name}`} />}
          </div>
        </div>
        {environments.map((environment) => {
          const { name: environmentName } = environment;

          return (
            <EnvironmentRow
              key={environmentName}
              name={environmentName}
              resources={resourcesByEnvironment[environmentName]}
            >
              <EnvironmentCards
                application={application}
                environment={environment}
                reference={reference}
                version={versionDetails}
                allVersions={allVersions}
                pinnedVersion={getPinnedVersion(environmentName)}
                resourcesByEnvironment={resourcesByEnvironment}
              />
              <div className="resources-section">
                {resourcesByEnvironment[environmentName]
                  .filter((resource) => shouldDisplayResource(reference, resource))
                  .sort((a, b) => `${a.kind}${a.displayName}`.localeCompare(`${b.kind}${b.displayName}`))
                  .map((resource) => (
                    <ManagedResourceObject application={application} key={resource.id} resource={resource} depth={1} />
                  ))}
              </div>
            </EnvironmentRow>
          );
        })}
        {preDeploymentSteps && preDeploymentSteps.length > 0 && (
          <PreDeploymentRow>
            {preDeploymentSteps.map((step) => (
              <PreDeploymentStepCard key={step.id} step={step} application={application} reference={reference} />
            ))}
          </PreDeploymentRow>
        )}
      </div>
    </>
  );
};
