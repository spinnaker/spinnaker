import { ApolloProvider } from '@apollo/client';
import { module } from 'angular';
import classNames from 'classnames';
import React from 'react';
import { react2angular } from 'react2angular';

import type { Application } from '../../application';
import { createApolloClient } from '../graphql/client';
import {
  FetchApplicationManagementDataDocument,
  useFetchApplicationManagementStatusQuery,
  useToggleManagementMutation,
} from '../graphql/graphql-sdk';
import { withErrorBoundary } from '../../presentation/SpinErrorBoundary';
import { ValidationMessage } from '../../presentation/forms/validation';
import { logger } from '../../utils';
import { getDocsUrl } from '../utils/defaults';
import { Spinner } from '../../widgets/spinners/Spinner';

import './ManagedResourceConfig.less';

export interface IManagedResourceConfigProps {
  application: Application;
}

const logClick = (label: string, application: string) =>
  logger.log({
    category: 'Managed Resource Config',
    action: `${label} clicked`,
    data: { label: application },
  });

const getManagementStatus = (paused: boolean) => {
  if (paused) {
    return (
      <>
        <div className="sp-padding-m sp-margin-m-bottom paused-warning">
          <i className="fa fa-pause sp-margin-xs-right" /> <b>Resource management is paused.</b>
        </div>
        <p className="sp-margin-l-bottom">
          Spinnaker is configured to manage some of this application's resources, but management has been paused. When
          resumed, Spinnaker will take action to make each resource match its desired state.
        </p>
      </>
    );
  } else {
    return (
      <>
        <p>
          <span className="rainbow-icon">🌈</span> <b>Spinnaker is managing some resources.</b>
        </p>
        <p className="sp-margin-l-bottom">
          If you need to temporarily stop Spinnaker from managing resources — for example, if something is wrong and
          manual intervention is required — you can pause management and resume it later. Pausing affects all managed
          resources within this application.
        </p>
      </>
    );
  }
};

const ManagedResourceConfigInternal = ({ application }: IManagedResourceConfigProps) => {
  const appName = application.name;
  const { data, loading } = useFetchApplicationManagementStatusQuery({ variables: { appName } });
  const [toggleManagement, { loading: pausePending, error: pauseFailed }] = useToggleManagementMutation({
    refetchQueries: [{ query: FetchApplicationManagementDataDocument, variables: { appName } }],
  });

  if (loading) {
    return <Spinner size="medium" message="Loading management state..." />;
  }

  if (!data) return null;

  const paused = Boolean(data?.application?.isPaused);
  const iconClass = paused ? 'fa-play' : 'fa-pause';

  return (
    <div className="ManagedResourceConfig">
      {getManagementStatus(paused)}
      <button
        className="btn btn-primary"
        disabled={pausePending}
        onClick={() => toggleManagement({ variables: { isPaused: !paused, application: appName } })}
        type="button"
      >
        {(!pausePending && <i className={classNames('fa sp-margin-xs-right', iconClass)} />) || (
          <Spinner mode="circular" />
        )}{' '}
        {paused ? 'Resume Management' : 'Pause Management'}
      </button>
      {pauseFailed && (
        <div className="sp-margin-l-top">
          <ValidationMessage type="error" message="Saving failed." />
        </div>
      )}
      <div className="color-text-caption sp-margin-l-top">
        Not sure what this means?{' '}
        <a target="_blank" onClick={() => logClick('Documentation', application.name)} href={getDocsUrl('root')}>
          Check out our documentation
        </a>
      </div>
    </div>
  );
};

const ManagedResourceConfig = (props: IManagedResourceConfigProps) => {
  const { client } = React.useMemo(createApolloClient, []);

  return (
    <ApolloProvider client={client}>
      <ManagedResourceConfigInternal {...props} />
    </ApolloProvider>
  );
};

export const MANAGED_RESOURCE_CONFIG = 'spinnaker.core.managedResourceConfig.component';
module(MANAGED_RESOURCE_CONFIG, []).component(
  'managedResourceConfig',
  react2angular(withErrorBoundary(ManagedResourceConfig, 'managedResourceConfig'), ['application']),
);
