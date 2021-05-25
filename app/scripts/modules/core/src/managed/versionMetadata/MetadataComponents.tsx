import classnames from 'classnames';
import { DateTime } from 'luxon';
import React from 'react';
import { Dropdown, MenuItem } from 'react-bootstrap';

import { Icon, IconNames } from '@spinnaker/presentation';
import { Tooltip } from 'core/presentation';

import { RelativeTimestamp } from '../RelativeTimestamp';
import { LifecycleEventSummary } from '../overview/artifact/utils';
import { TOOLTIP_DELAY_SHOW } from '../utils/defaults';

import './VersionMetadata.less';

export const MetadataElement: React.FC<{ className?: string }> = ({ className, children }) => {
  return <span className={classnames('metadata-element', className)}>{children}</span>;
};

export interface VersionAction {
  onClick?: () => void;
  href?: string;
  content: React.ReactNode;
  disabled?: boolean;
}

export interface VersionMessageData {
  by?: string;
  at?: string;
  comment?: string;
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

export interface IVersionMetadataProps {
  build?: IVersionBuildProps['build'];
  author?: string;
  deployedAt?: string;
  createdAt?: IVersionCreatedAtProps['createdAt'];
  buildDuration?: string;
  buildsBehind?: number;
  isDeploying?: boolean;
  baking?: LifecycleEventSummary;
  pinned?: VersionMessageData;
  actions?: VersionAction[];
}

export interface IVersionMetadataActionsProps {
  id: string;
  actions: VersionAction[];
}

export const VersionMetadataActions = ({ id, actions }: IVersionMetadataActionsProps) => {
  return (
    <MetadataElement>
      <Dropdown id={id}>
        <Dropdown.Toggle className="element-actions-menu-toggle">Actions</Dropdown.Toggle>
        <Dropdown.Menu>
          {actions.map((action, index) => (
            <MenuItem
              key={index}
              disabled={action.disabled}
              onClick={action.onClick}
              href={action.href}
              target="_blank"
            >
              {action.content}
            </MenuItem>
          ))}
        </Dropdown.Menu>
      </Dropdown>
    </MetadataElement>
  );
};

interface IVersionCreatedAtProps {
  createdAt?: string | DateTime;
}

export const VersionCreatedAt = ({ createdAt }: IVersionCreatedAtProps) => {
  if (!createdAt) return null;
  return (
    <MetadataElement>
      <Tooltip delayShow={TOOLTIP_DELAY_SHOW} value="Created at">
        <i className="far fa-calendar-alt metadata-icon" />
      </Tooltip>
      <RelativeTimestamp timestamp={createdAt} delayShow={TOOLTIP_DELAY_SHOW} removeStyles withSuffix />
    </MetadataElement>
  );
};

const badgeTypeToDetails = {
  deploying: { className: 'version-deploying', text: 'Deploying' },
  baking: { className: 'version-baking', text: 'Baking' },
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
    text: 'Marked as bad by',
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
        <Icon name={typeProps.icon} size="12px" color="black" className="sp-margin-s-right sp-margin-2xs-top" />
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

interface IVersionBuildProps {
  build: { buildNumber?: string; buildLink?: string; version?: string };
  withPrefix?: boolean;
}

export const VersionBuild = ({ build, withPrefix }: IVersionBuildProps) => {
  const content = build.buildLink ? (
    <a href={build.buildLink}>
      {withPrefix ? `Build ` : ''}#{build.buildNumber}
    </a>
  ) : (
    `Build #${build.buildNumber}`
  );

  return build.version ? (
    <Tooltip value={build.version} delayShow={TOOLTIP_DELAY_SHOW}>
      {content}
    </Tooltip>
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
          {builds.map((build, index) => (
            <VersionBuild key={index} build={build} />
          ))}
        </>
      )}
    </MetadataElement>
  );
};

export const BaseVersionMetadata: React.FC = ({ children }) => {
  return <div className="VersionMetadata">{children}</div>;
};
