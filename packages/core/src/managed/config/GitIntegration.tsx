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

export const GitIntegration = ({ isEnabled, branch, repository }: IGitIntegrationProps) => {
  const appName = useApplicationContextSafe().name;
  const [updateIntegration, { loading }] = useUpdateGitIntegrationMutation({
    refetchQueries: [{ query: FetchApplicationManagementDataDocument, variables: { appName } }],
  });

  return (
    <div className="sp-margin-xl-top">
      <h4>Git integration</h4>
      <CheckboxInput
        checked={Boolean(isEnabled)}
        text={
          <span className="horizontal middle">
            Auto-import delivery config from {repository}/{branch}
            {loading && (
              <Spinner mode="circular" size="nano" color="var(--color-accent)" className="sp-margin-s-left" />
            )}
          </span>
        }
        disabled={loading}
        onChange={(e: React.ChangeEvent<HTMLInputElement>) => {
          updateIntegration({ variables: { payload: { application: appName, isEnabled: e.target.checked } } });
        }}
      />
    </div>
  );
};
