import React from 'react';

import { SETTINGS } from '../../config';
import type { FetchApplicationManagementDataQuery } from '../graphql/graphql-sdk';
import { FetchApplicationManagementDataDocument, useUpdateGitIntegrationMutation } from '../graphql/graphql-sdk';
import { CheckboxInput, useApplicationContextSafe } from '../../presentation';
import { useLogEvent } from '../utils/logging';
import { useNotifyOnError } from '../utils/useNotifyOnError.hook';
import { Spinner } from '../../widgets/spinners/Spinner';

import './GitIntegration.less';

type IGitIntegrationProps = NonNullable<
  NonNullable<FetchApplicationManagementDataQuery['application']>['gitIntegration']
>;

const ManifestPath = ({ manifestPath }: Pick<IGitIntegrationProps, 'manifestPath'>) => {
  const [currentPath, setCurrentPath] = React.useState(manifestPath);
  const [isEditing, setIsEditing] = React.useState(false);
  const appName = useApplicationContextSafe().name;
  const [updateIntegration, { loading }] = useUpdateGitIntegrationMutation({
    refetchQueries: [{ query: FetchApplicationManagementDataDocument, variables: { appName } }],
    onCompleted: () => {
      setIsEditing(false);
    },
  });

  const baseManifestPath = SETTINGS.managedDelivery?.manifestBasePath + '/';

  return (
    <div className="sp-margin-s-top horizontal middle ManifestPath">
      Config file path:{' '}
      {isEditing ? (
        <form
          className="horizontal middle"
          onSubmit={(e) => {
            e.preventDefault();
            updateIntegration({ variables: { payload: { application: appName, manifestPath: currentPath } } });
          }}
        >
          <code>{baseManifestPath}</code>
          <input
            value={currentPath}
            className="form-control horizontal manifest-input input-sm sp-margin-xs-left"
            onChange={(e) => setCurrentPath(e.target.value)}
            autoFocus
          />
          <button
            className="btn md-btn md-btn-accent sp-padding-xs-yaxis sp-padding-s-xaxis sp-margin-s-left"
            type="submit"
            disabled={loading}
          >
            Save
          </button>
        </form>
      ) : (
        <code>
          {baseManifestPath}
          {manifestPath}
        </code>
      )}
      {loading ? (
        <Spinner mode="circular" size="nano" color="var(--color-accent)" className="sp-margin-s-left" />
      ) : (
        <button
          className="btn-link no-padding no-border sp-margin-s-left"
          onClick={() => {
            setIsEditing((state) => !state);
          }}
        >
          <i className={isEditing ? 'fas fa-times' : 'far fa-edit'} />
        </button>
      )}
    </div>
  );
};

export const GitIntegration = ({ isEnabled, branch, link, repository, manifestPath }: IGitIntegrationProps) => {
  const appName = useApplicationContextSafe().name;
  const [updateIntegration, { loading, error }] = useUpdateGitIntegrationMutation({
    refetchQueries: [{ query: FetchApplicationManagementDataDocument, variables: { appName } }],
  });
  const logEvent = useLogEvent('GitIntegration');

  useNotifyOnError({
    key: 'toggleGitIntegration',
    content: `Failed to ${isEnabled ? 'disable' : 'enable'} auto-import`,
    error,
  });

  const repoAndBranch = [repository, branch].join(':');

  return (
    <div className="GitIntegration sp-margin-m-bottom">
      <CheckboxInput
        checked={Boolean(isEnabled)}
        wrapperClassName="sp-margin-3xs-yaxis"
        text={
          <div>
            <span className="horizontal middle">
              Auto-import delivery config from&nbsp;
              {link ? (
                <a href={link} target="_blank" onClick={(e) => e.stopPropagation()}>
                  {repoAndBranch}
                </a>
              ) : (
                repoAndBranch
              )}
              {loading && (
                <Spinner mode="circular" size="nano" color="var(--color-accent)" className="sp-margin-s-left" />
              )}
            </span>
          </div>
        }
        disabled={loading}
        onChange={(e: React.ChangeEvent<HTMLInputElement>) => {
          updateIntegration({ variables: { payload: { application: appName, isEnabled: e.target.checked } } });
          logEvent({ action: e.target.checked ? 'EnableIntegration' : 'DisableIntegration' });
        }}
      />
      <div className="help-text">
        Turning this on will automatically import your config from git when a new commit is made to{' '}
        {branch || 'your main branch'}
      </div>
      <ManifestPath manifestPath={manifestPath} />
    </div>
  );
};
