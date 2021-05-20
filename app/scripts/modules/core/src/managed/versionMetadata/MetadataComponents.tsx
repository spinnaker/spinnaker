import classnames from 'classnames';
import { DateTime } from 'luxon';
import React from 'react';
import { Dropdown, MenuItem } from 'react-bootstrap';

import { Icon } from '@spinnaker/presentation';
import { Tooltip } from 'core/presentation';

import { RelativeTimestamp } from '../RelativeTimestamp';
import { TOOLTIP_DELAY } from '../utils/defaults';

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

export interface IVersionMetadataProps {
  buildNumber?: string;
  buildLink?: string;
  author?: string;
  deployedAt?: string;
  createdAt?: IVersionCreatedAtProps['createdAt'];
  buildDuration?: string;
  buildsBehind?: number;
  isDeploying?: boolean;
  isPinned?: boolean;
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
      <Tooltip delayShow={TOOLTIP_DELAY} value="Created at">
        <i className="far fa-calendar-alt metadata-icon" />
      </Tooltip>
      <RelativeTimestamp timestamp={createdAt} delayShow={TOOLTIP_DELAY} removeStyles withSuffix />
    </MetadataElement>
  );
};

export const DeployingBadge = () => {
  return (
    <MetadataElement>
      <span className="version-deploying version-badge">Deploying</span>
    </MetadataElement>
  );
};

export const PinnedBadge = () => {
  return (
    <MetadataElement>
      <span className="version-pinned version-badge">
        <Icon name="pin" size="12px" color="black" /> Pinned
      </span>
    </MetadataElement>
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
  build: { buildNumber?: string; buildLink?: string };
  withPrefix?: boolean;
}

export const VersionBuild = ({ build, withPrefix }: IVersionBuildProps) => {
  return (
    <>
      {build.buildLink ? (
        <a href={build.buildLink}>
          {withPrefix ? `Build ` : ''}#{build.buildNumber}
        </a>
      ) : (
        `Build #${build.buildNumber}`
      )}
    </>
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
