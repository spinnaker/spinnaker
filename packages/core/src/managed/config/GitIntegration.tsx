import React from 'react';

import {
  FetchApplicationManagementDataDocument,
  FetchApplicationManagementDataQuery,
  useUpdateGitIntegrationMutation,
} from '../graphql/graphql-sdk';
import { CheckboxInput, useApplicationContextSafe } from '../../presentation';
import { useLogEvent } from '../utils/logging';
import { Spinner } from '../../widgets/spinners/Spinner';

import './GitIntegration.less';

type IGitIntegrationProps = NonNullable<
  NonNullable<FetchApplicationManagementDataQuery['application']>['gitIntegration']
>;

export const GitIntegration = ({ isEnabled, branch, link, repository }: IGitIntegrationProps) => {
  const appName = useApplicationContextSafe().name;
  const [updateIntegration, { loading }] = useUpdateGitIntegrationMutation({
    refetchQueries: [{ query: FetchApplicationManagementDataDocument, variables: { appName } }],
  });
  const logEvent = useLogEvent('GitIntegration');

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
    </div>
  );
};
