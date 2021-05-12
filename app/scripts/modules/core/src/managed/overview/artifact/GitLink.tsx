import React from 'react';

import { HoverablePopover, Markdown } from 'core/presentation';

import { QueryGitMetadata } from '../types';
import { TOOLTIP_DELAY } from '../../utils/defaults';

import './GitLink.less';

interface IGitLinkProps {
  gitMetadata: NonNullable<QueryGitMetadata>;
}

export const GitLink = ({ gitMetadata: { commit, commitInfo, pullRequest } }: IGitLinkProps) => {
  const link = pullRequest?.link || commitInfo?.link;
  const sha = commit ? `SHA: ${commit}` : undefined;
  const tooltip = [sha, commitInfo?.message].filter(Boolean).join('\n\n');
  const message = commitInfo?.message || commit;
  return (
    <div className="GitLink">
      <HoverablePopover
        wrapperClassName="git-link-inner"
        delayShow={TOOLTIP_DELAY}
        placement="top"
        Component={() => <Markdown className="git-commit-tooltip" message={tooltip} />}
      >
        <a href={link || '#'} target="_blank" rel="noopener noreferrer">
          {message?.split('\n')[0]}
        </a>
      </HoverablePopover>
    </div>
  );
};
