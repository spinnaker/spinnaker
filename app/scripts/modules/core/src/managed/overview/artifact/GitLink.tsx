import React from 'react';

import { HoverablePopover, Markdown } from 'core/presentation';

import { QueryGitMetadata } from '../types';
import { TOOLTIP_DELAY } from '../../utils/defaults';

import './GitLink.less';

interface IGitLinkProps {
  gitMetadata: NonNullable<QueryGitMetadata>;
  asLink?: boolean;
}

export const GitLink = ({ gitMetadata: { commit, commitInfo, pullRequest }, asLink = true }: IGitLinkProps) => {
  const link = pullRequest?.link || commitInfo?.link;
  const sha = commit ? `SHA: ${commit}` : undefined;
  const tooltip = [sha, commitInfo?.message].filter(Boolean).join('\n\n');
  let message = commitInfo?.message || commit;
  message = message?.split('\n')[0];
  return (
    <div className="GitLink">
      <HoverablePopover
        wrapperClassName="git-link-inner"
        delayShow={TOOLTIP_DELAY}
        delayHide={0}
        placement="top"
        Component={() => <Markdown className="git-commit-tooltip" message={tooltip} />}
      >
        {asLink ? (
          <a href={link || '#'} className="commit-message" target="_blank" rel="noopener noreferrer">
            {message}
          </a>
        ) : (
          <span className="commit-message">{message}</span>
        )}
      </HoverablePopover>
    </div>
  );
};
