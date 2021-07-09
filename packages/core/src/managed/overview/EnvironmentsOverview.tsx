import React from 'react';

import { ApplicationQueryError } from '../ApplicationQueryError';
import { EnvironmentOverview } from './EnvironmentOverview';
import { EnvironmentsRender, useOrderedEnvironment } from '../environmentBaseElements/EnvironmentsRender';
import { useFetchApplicationQuery } from '../graphql/graphql-sdk';
import { Messages } from '../messages/Messages';
import { useApplicationContextSafe } from '../../presentation';
import { OVERVIEW_VERSION_STATUSES } from './utils';
import { spinnerProps } from '../utils/defaults';
import { Spinner } from '../../widgets';

import './EnvironmentsOverview.less';

export const EnvironmentsOverview = () => {
  const app = useApplicationContextSafe();
  const { data, error, loading } = useFetchApplicationQuery({
    variables: { appName: app.name, statuses: OVERVIEW_VERSION_STATUSES },
  });
  const wrapperRef = React.useRef<HTMLDivElement>(null);

  const environments = data?.application?.environments || [];
  const { environments: regularEnvironments, ...regularEnvironmentsProps } = useOrderedEnvironment(
    wrapperRef,
    environments.filter((env) => !env.isPreview),
  );

  const { environments: previewEnvironments, ...previewEnvironmentsProps } = useOrderedEnvironment(
    wrapperRef,
    environments.filter((env) => env.isPreview),
  );

  let content;
  if (loading && !data) {
    content = <Spinner {...spinnerProps} message="Loading environments..." />;
  } else if (error) {
    content = <ApplicationQueryError hasApplicationData={Boolean(data?.application)} error={error} />;
  } else {
    content = (
      <>
        <Messages />
        {environments.length ? (
          <>
            <EnvironmentsRender {...regularEnvironmentsProps}>
              {regularEnvironments.map((env) => (
                <EnvironmentOverview key={env.name} environment={env} />
              ))}
            </EnvironmentsRender>
            {Boolean(previewEnvironments.length) && (
              <h4 className="sp-margin-2xl-top sp-margin-m-bottom self-left">
                <b>Preview Environments</b>
              </h4>
            )}
            <EnvironmentsRender {...previewEnvironmentsProps}>
              {previewEnvironments.map((env) => (
                <EnvironmentOverview key={env.name} environment={env} />
              ))}
            </EnvironmentsRender>
          </>
        ) : (
          <div className="error-message">No environments found</div>
        )}
      </>
    );
  }

  return (
    <div className="EnvironmentsOverview" ref={wrapperRef}>
      {content}
    </div>
  );
};
