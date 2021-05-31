import classNames from 'classnames';
import * as React from 'react';

import { ICiBuild } from '../domain';
import { relativeTime } from '../../utils';

const configByBuildStatus: { [key: string]: { statusClass: string; text: string } } = {
  SUCCEEDED: {
    statusClass: 'ci-pass',
    text: 'passed',
  },
  FAILED: {
    statusClass: 'ci-fail',
    text: 'failed',
  },
  INCOMPLETE: {
    statusClass: 'ci-building',
    text: 'running',
  },
  ABORTED: {
    statusClass: 'ci-canceled',
    text: 'canceled',
  },
};

interface IBuildInfoCardProps {
  build: ICiBuild;
  isActive: boolean;
  onClick: () => void;
}
export function BuildInfoSummaryCard({ build, isActive, onClick }: IBuildInfoCardProps) {
  const config = configByBuildStatus[build.result];
  return (
    <div className={classNames('ci-build-box', { active: isActive })} onClick={onClick}>
      <div className="ci-build-box-title">
        <span className="ci-build-box-title-left">
          <i className={classNames(`icon-ci-build ${config?.statusClass ?? ''}`)} />
          <span className={`${config?.statusClass ?? ''}`}>
            #{build.number} {config?.text}
          </span>
        </span>
        <span className="note">{relativeTime(build.startTime + build.duration)}</span>
      </div>

      <div className="ci-build-box-pr horizontal">
        <i className="commit icon-ci-commit" />
        <a className="sp-margin-xs-right" href={build.commitLink} target="_blank">
          {build.commitId}
        </a>
        by {build.author.length > 15 ? build.author.substr(0, 15) + '...' : build.author}
        <span className="ci-pill-branch">
          {build.branchName?.length > 15 ? build.branchName.substr(0, 15) + '...' : build.branchName}
        </span>
      </div>
    </div>
  );
}
