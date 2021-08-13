import React from 'react';

import {
  FetchApplicationManagementDataDocument,
  FetchApplicationManagementDataQuery,
  useUpdateGitIntegrationMutation,
} from '../graphql/graphql-sdk';
import { CheckboxInput, useApplicationContextSafe } from '../../presentation';
import { Spinner } from '../../widgets/spinners/Spinner';

type IGitIntegrationProps = NonNullable<
  NonNullable<FetchApplicationManagementDataQuery['application']>['gitIntegration']
>;

export const GitIntegration = ({ isEnabled, branch, link, repository }: IGitIntegrationProps) => {
  const appName = useApplicationContextSafe().name;
  const [updateIntegration, { loading }] = useUpdateGitIntegrationMutation({
    refetchQueries: [{ query: FetchApplicationManagementDataDocument, variables: { appName } }],
  });

  const repoAndBranch = [repository, branch].join(':');

  return (
    <div className="sp-margin-xl-top">
      <h4>Git integration</h4>
      <CheckboxInput
        checked={Boolean(isEnabled)}
        text={
          <div>
            <span className="horizontal">
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
            <small>
              Turning this on will automatically import your config from git when a new commit is made to{' '}
              {branch || 'your main branch'}
            </small>
          </div>
        }
        disabled={loading}
        onChange={(e: React.ChangeEvent<HTMLInputElement>) => {
          updateIntegration({ variables: { payload: { application: appName, isEnabled: e.target.checked } } });
        }}
      />
    </div>
  );
};
