import { UISref, useCurrentStateAndParams } from '@uirouter/react';
import classNames from 'classnames';
import * as React from 'react';

import { BuildInfoArtifactsTab } from './BuildInfoArtifactsTab';
import { BuildInfoLogsTab } from './BuildInfoLogsTab';
import { ICiBuild } from '../domain';
import { duration, relativeTime } from '../../utils';

const infoConfigByBuildStatus: { [key: string]: { statusClass?: string; text: string } } = {
  SUCCEEDED: {
    statusClass: 'pass',
    text: 'Passed',
  },
  FAILED: {
    statusClass: 'fail',
    text: 'Failed',
  },
  INCOMPLETE: {
    statusClass: 'building',
    text: 'Running',
  },
  ABORTED: {
    statusClass: 'canceled',
    text: 'Canceled',
  },
};

interface IBuildInfoDetailsProps {
  build: ICiBuild;
}
export function BuildInfoDetails({ build }: IBuildInfoDetailsProps) {
  const { params } = useCurrentStateAndParams();
  return (
    <div className="ci-builds-details">
      <div className="ci-builds-details-header">
        <div className="ci-builds-details-header-left">
          <h3>Build #{build.number}</h3>
          <span className={`ci-pill ${infoConfigByBuildStatus[build.result]?.statusClass ?? ''}`}>
            <span>{infoConfigByBuildStatus[build.result]?.text}</span>
          </span>
          <span className="note">{relativeTime(build.startTime + build.duration)}</span>
          <br />

          <div className="horizontal">
            <dt className="sp-margin-l-right">Commit</dt>
            <dd>
              <i className="commit icon-ci-commit" />
              <a href={build.commitLink} target="_blank">
                {' '}
                {build.commitId}
              </a>{' '}
              by {build.author}
            </dd>
          </div>

          <div className="horizontal">
            <dt className="sp-margin-l-right">Message</dt>
            <dd>{build.commitMessage}</dd>
          </div>
        </div>

        <div className="ci-builds-details-header-right">
          <dl className="dl-horizontal">
            <br />

            <dt>Duration</dt>
            <dd>{duration(build.duration)}</dd>

            <dt>Job</dt>
            <dd>
              <a href={build.url} target="_blank">
                {build.fullDisplayName}
              </a>
            </dd>

            <dt>Branch</dt>
            <dd>{build.branchName}</dd>

            {build.repoLink && (
              <>
                <dt>Repository</dt>
                <dd>
                  <a href={build.repoLink} target="_blank">
                    {build.projectKey}/{build.repoSlug}
                  </a>
                </dd>
              </>
            )}

            <dt>Pull Request</dt>
            {build.pullRequestNumber?.length < 1 ? (
              <dd>N/A</dd>
            ) : (
              <dd>
                <a href={build.pullRequestUrl} target="_blank">
                  {build.pullRequestNumber}
                </a>
              </dd>
            )}
          </dl>
        </div>
      </div>

      <BuildInfoOutput build={build} tab={params.tab} />
    </div>
  );
}

interface IBuildInfoOutputProps {
  build: ICiBuild;
  tab: 'logs' | 'artifacts';
}

function BuildInfoOutput({ build, tab }: IBuildInfoOutputProps) {
  return (
    <>
      <div className="ci-output-tabs">
        <div className={classNames('ci-output-tab', { active: tab === 'logs' })}>
          <UISref to="home.applications.application.builds.build.buildTab" params={{ tab: 'logs' }}>
            <a>Build Log</a>
          </UISref>
        </div>

        <div className={classNames('ci-output-tab', { active: tab === 'artifacts' })}>
          <UISref to="home.applications.application.builds.build.buildTab" params={{ tab: 'artifacts' }}>
            <a>
              {build.artifacts.length} {build.artifacts.length === 1 ? 'Artifact' : 'Artifacts'} generated
            </a>
          </UISref>
        </div>
      </div>

      <div className="ci-output-main">
        {tab === 'logs' ? <BuildInfoLogsTab build={build} /> : null}
        {tab === 'artifacts' ? <BuildInfoArtifactsTab build={build} /> : null}
      </div>
    </>
  );
}
