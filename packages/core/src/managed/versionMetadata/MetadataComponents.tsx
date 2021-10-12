import { useSref } from '@uirouter/react';
import classnames from 'classnames';
import { sortBy } from 'lodash';
import type { DateTime } from 'luxon';
import React from 'react';

import type { IconNames } from '@spinnaker/presentation';
import { Icon } from '@spinnaker/presentation';

import { formatToRelativeTimestamp, RelativeTimestamp } from '../RelativeTimestamp';
import type { LifecycleEventSummary } from '../overview/artifact/utils';
import { HoverablePopover, IconTooltip, LabeledValue, Tooltip } from '../../presentation';
import { copyTextToClipboard } from '../../utils/clipboard/copyTextToClipboard';
import { ABSOLUTE_TIME_FORMAT, TOOLTIP_DELAY_SHOW } from '../utils/defaults';
import { useLogEvent } from '../utils/logging';

import './VersionMetadata.less';

export const MetadataElement: React.FC<{ className?: string }> = ({ className, children }) => {
  return <span className={classnames('delimited-element horizontal middle', className)}>{children}</span>;
};

export const METADATA_TEXT_COLOR = 'nobel';

export interface VersionMessageData {
  by?: string;
  at?: string;
  comment?: string;
}

export interface ICompareLinks {
  previous?: string;
  current?: string;
}

export const toPinnedMetadata = (data: {
  pinnedAt?: string;
  pinnedBy?: string;
  comment?: string;
}): VersionMessageData => ({
  by: data.pinnedBy,
  at: data.pinnedAt,
  comment: data.comment,
});

export const toVetoedMetadata = (data: {
  vetoedAt?: string;
  vetoedBy?: string;
  comment?: string;
}): VersionMessageData => ({
  by: data.vetoedBy,
  at: data.vetoedAt,
  comment: data.comment,
});

export interface IVersionMetadataProps {
  build?: IVersionBuildProps['build'];
  author?: string;
  deployedAt?: string;
  buildsBehind?: number;
  isDeploying?: boolean;
  isPending?: boolean;
  bake?: LifecycleEventSummary;
  pinned?: VersionMessageData;
  vetoed?: VersionMessageData;
  isCurrent?: boolean;
}

const useCreateVersionLink = (linkProps: IVersionCreatedAtProps['linkProps']) => {
  return useSref('home.applications.application.environments.history', linkProps);
};
interface IVersionCreatedAtProps {
  createdAt?: string | DateTime;
  linkProps: Record<string, string>;
}

export const VersionCreatedAt = ({ createdAt, linkProps }: IVersionCreatedAtProps) => {
  const { href } = useCreateVersionLink(linkProps);
  if (!createdAt) return null;

  return (
    <MetadataElement className="created-at">
      <Tooltip delayShow={TOOLTIP_DELAY_SHOW} value="Created at">
        <i className="far fa-calendar-alt metadata-icon" />
      </Tooltip>
      <a
        href={href}
        onClick={(e) => {
          href && copyTextToClipboard([window.location.origin, href].join('/'));
          e.stopPropagation();
        }}
      >
        <RelativeTimestamp timestamp={createdAt} delayShow={TOOLTIP_DELAY_SHOW} removeStyles withSuffix />
      </a>
    </MetadataElement>
  );
};

const badgeTypeToDetails = {
  deploying: { className: 'version-deploying', text: 'Deploying' },
  baking: { className: 'version-baking', text: 'Baking' },
  deployed: { className: 'version-deployed', text: 'Live' },
};

interface IMetadataBadgeProps {
  type: keyof typeof badgeTypeToDetails;
  tooltip?: string;
  link?: string;
}

export const MetadataBadge = ({ type, link, tooltip }: IMetadataBadgeProps) => {
  const details = badgeTypeToDetails[type];
  const className = classnames('version-badge', details.className);
  const baseBadge = link ? (
    <a href={link} className={className}>
      {details.text}
    </a>
  ) : (
    <span className={className}>{details.text}</span>
  );
  return (
    <MetadataElement>
      {tooltip ? (
        <Tooltip value={tooltip} delayShow={TOOLTIP_DELAY_SHOW}>
          {baseBadge}
        </Tooltip>
      ) : (
        baseBadge
      )}
    </MetadataElement>
  );
};

interface IVersionMessage {
  data: VersionMessageData;
  type: 'pinned' | 'vetoed';
  newRow?: boolean;
}

const versionTypeProps: { [key in IVersionMessage['type']]: { text: string; className: string; icon: IconNames } } = {
  pinned: {
    text: 'Pinned by',
    className: 'version-pinned',
    icon: 'pin',
  },
  vetoed: {
    text: 'Rejected by',
    className: 'version-vetoed',
    icon: 'artifactBad',
  },
};

export const VersionMessage = ({ data, type, newRow = true }: IVersionMessage) => {
  const typeProps = versionTypeProps[type];
  return (
    <>
      {newRow && <div className="flex-break sp-margin-s-top" />}
      <div className={classnames('version-message', typeProps.className)}>
        <Icon name={typeProps.icon} size="14px" color={METADATA_TEXT_COLOR} className="sp-margin-2xs-top" />
        <div>
          <div>
            {typeProps.text} {data.by},{' '}
            {data.at && (
              <RelativeTimestamp timestamp={data.at} delayShow={TOOLTIP_DELAY_SHOW} withSuffix removeStyles />
            )}
          </div>
          {data.comment && <div>Reason: {data.comment}</div>}
        </div>
      </div>
    </>
  );
};

interface IVersionAuthorProps {
  author?: string;
}

export const VersionAuthor = ({ author }: IVersionAuthorProps) => {
  if (!author) return null;
  return <MetadataElement>By {author}</MetadataElement>;
};

interface IVersionBranchProps {
  branch?: string;
}

export const VersionBranch = ({ branch }: IVersionBranchProps) => {
  if (!branch) return null;
  return (
    <MetadataElement>
      <Icon name="spCIBranch" size="11px" className="sp-margin-xs-right" color={METADATA_TEXT_COLOR} /> {branch}
    </MetadataElement>
  );
};

interface IVersionBuildProps {
  build: { buildNumber?: string; version?: string } & Partial<LifecycleEventSummary>;
  withPrefix?: boolean;
}

export const VersionBuild = ({ build, withPrefix }: IVersionBuildProps) => {
  const logEvent = useLogEvent('ArtifactBuild', 'OpenBuild');
  const text = `${withPrefix ? `Build ` : ''}#${build.buildNumber}`;
  const content = build.link ? (
    <a href={build.link} onClick={() => logEvent({ data: { build: build.buildNumber } })}>
      {text}
    </a>
  ) : (
    text
  );

  return build.version ? (
    <HoverablePopover
      Component={() => <LifecycleEventDetails {...build} title="Build" showLink={false} />}
      delayShow={TOOLTIP_DELAY_SHOW}
    >
      <span>{content}</span>
    </HoverablePopover>
  ) : (
    <>{content}</>
  );
};

interface IVersionBuildsProps {
  builds: Array<IVersionBuildProps['build']>;
}

export const VersionBuilds = ({ builds }: IVersionBuildsProps) => {
  if (!builds.length) return null;

  return (
    <MetadataElement>
      {builds.length === 1 ? (
        <VersionBuild build={builds[0]} withPrefix />
      ) : (
        <>
          Builds{' '}
          {sortBy(builds, (build) => build.buildNumber).map((build, index) => (
            <React.Fragment key={index}>
              <VersionBuild build={build} withPrefix={false} />
              {Boolean(index < builds.length - 1) && ', '}
            </React.Fragment>
          ))}
        </>
      )}
    </MetadataElement>
  );
};

export const BaseVersionMetadata: React.FC = ({ children }) => {
  return <div className="VersionMetadata">{children}</div>;
};

interface ILifecycleEventDetailsProps extends Partial<LifecycleEventSummary> {
  title: string;
  showLink?: boolean;
  version?: string;
}

export const LifecycleEventDetails = ({
  version,
  duration,
  link,
  startedAt,
  title,
  showLink = true,
}: ILifecycleEventDetailsProps) => {
  return (
    <div className="LifecycleEventDetails">
      <div>
        <div className="title sp-margin-xs-bottom">{title}</div>
        <dl className="details sp-margin-s-bottom">
          {version && <LabeledValue label="Version" value={version} />}
          <LabeledValue
            label="Started at"
            value={
              `${startedAt?.toFormat(ABSOLUTE_TIME_FORMAT) || 'N/A'}` +
              ` ` +
              `${startedAt ? ` (${formatToRelativeTimestamp(startedAt, true)})` : ''}`
            }
          />
          <LabeledValue label="Duration" value={duration || 'N/A'} />
          {showLink && link && <LabeledValue label="Link" value={<a href={link}>Open</a>} />}
        </dl>
      </div>
    </div>
  );
};

type IDeploymentStatusProps = Pick<IVersionMetadataProps, 'deployedAt' | 'isPending' | 'isCurrent' | 'isDeploying'>;

const statusToProps = {
  CURRENT: {
    icon: 'cloudDeployed',
    tooltip: 'Deployed at',
  },
  PENDING: {
    icon: 'cloudWaiting',
    tooltip: 'Deployment pending',
  },
  PREVIOUS: {
    icon: 'cloudDecommissioned',
    tooltip: 'Replaced by another version',
  },
  SKIPPED: {
    icon: 'artifactSkipped',
    tooltip: 'Deployment skipped',
  },
} as const;

export const DeploymentStatus = ({ deployedAt, isCurrent, isPending, isDeploying }: IDeploymentStatusProps) => {
  if (!deployedAt && isDeploying) return null; // We'll show the deploying badge so no reason to show this component if this version is being deployed for the first time
  const props = statusToProps[deployedAt ? (isCurrent ? 'CURRENT' : 'PREVIOUS') : isPending ? 'PENDING' : 'SKIPPED'];
  return (
    <MetadataElement>
      <IconTooltip
        tooltip={props.tooltip}
        name={props.icon}
        size="14px"
        wrapperClassName="metadata-icon"
        delayShow={TOOLTIP_DELAY_SHOW}
        color={METADATA_TEXT_COLOR}
      />
      {deployedAt ? (
        <>
          Deployed <RelativeTimestamp timestamp={deployedAt} delayShow={TOOLTIP_DELAY_SHOW} removeStyles withSuffix />
        </>
      ) : isPending ? (
        'Pending'
      ) : (
        'Not deployed'
      )}
    </MetadataElement>
  );
};
