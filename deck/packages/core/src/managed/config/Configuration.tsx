import classnames from 'classnames';
import React from 'react';

import { Illustration } from '@spinnaker/presentation';

import { ApplicationQueryError } from '../ApplicationQueryError';
import { DeliveryConfig } from './DeliveryConfig';
import { GitIntegration } from './GitIntegration';
import { UnmanagedMessage } from '../UnmanagedMessage';
import { SETTINGS } from '../../config/settings';
import {
  FetchApplicationManagementDataDocument,
  useFetchApplicationManagementDataQuery,
  useToggleManagementMutation,
} from '../graphql/graphql-sdk';
import { Messages } from '../messages/Messages';
import { showModal, useApplicationContextSafe } from '../../presentation';
import type { IArtifactActionModalProps } from '../utils/ActionModal';
import { ActionModal } from '../utils/ActionModal';
import { getIsDebugMode } from '../utils/debugMode';
import { getDocsUrl, MODAL_MAX_WIDTH, spinnerProps } from '../utils/defaults';
import { useLogEvent } from '../utils/logging';
import { useNotifyOnError } from '../utils/useNotifyOnError.hook';
import { Spinner } from '../../widgets';

const BTN_CLASSNAMES = 'btn md-btn';

const managementStatusToContent = {
  PAUSED: {
    title: 'Application management is disabled',
    btnText: 'Resume management...',
    btnClassName: 'md-btn-success',
  },
  ENABLED: {
    title: 'Application is managed by Spinnaker 🙌',
    btnText: 'Disable management...',
    btnClassName: 'md-btn-danger',
  },
};

export const Configuration = () => {
  const appName = useApplicationContextSafe().name;
  const { data, error, loading } = useFetchApplicationManagementDataQuery({
    variables: { appName },
    errorPolicy: 'all',
  });
  const logError = useLogEvent('DeliveryConfig');

  React.useEffect(() => {
    if (error) {
      logError({ action: 'LoadingFailed', data: { error } });
    }
  }, [error, logError]);

  if (loading || !data) {
    return <Spinner {...spinnerProps} message="Loading configuration..." />;
  }

  if (error && !Boolean(data?.application)) {
    return <UnmanagedMessage />;
  }

  const gitIntegration = data.application?.gitIntegration;
  const isDebug = getIsDebugMode();
  const config = data.application?.config;

  return (
    <div className="full-width">
      <Messages showManagementWarning={false} />
      {error && <ApplicationQueryError hasApplicationData={Boolean(data?.application)} error={error} />}
      <ManagementToggle isPaused={data.application?.isPaused} />
      <DeliveryConfig config={config?.rawConfig} updatedAt={config?.updatedAt}>
        {SETTINGS.feature.mdGitIntegration && gitIntegration && <GitIntegration {...gitIntegration} />}
      </DeliveryConfig>
      {isDebug && <DeliveryConfig config={config?.processedConfig} isProcessed />}
    </div>
  );
};

interface IManagementToggleProps {
  isPaused?: boolean;
}

const ManagementToggle = ({ isPaused }: IManagementToggleProps) => {
  const appName = useApplicationContextSafe().name;
  const logEvent = useLogEvent('Management');
  const [toggleManagement, { loading: mutationInFlight, error }] = useToggleManagementMutation({
    refetchQueries: [{ query: FetchApplicationManagementDataDocument, variables: { appName } }],
  });

  useNotifyOnError({
    key: 'toggleManagement',
    content: `Failed to ${isPaused ? 'enable' : 'disable'} management`,
    error,
  });

  const onShowToggleManagementModal = React.useCallback((shouldPause: boolean) => {
    logEvent({ action: 'OpenModal', data: { shouldPause } });
    showModal(
      shouldPause ? DisableManagementModal : ResumeManagementModal,
      {
        application: appName,
        onAction: async () => {
          toggleManagement({ variables: { application: appName, isPaused: shouldPause } });
        },
        logCategory: 'Management',
        withComment: false,
      },
      { maxWidth: MODAL_MAX_WIDTH },
    );
  }, []);

  const state = managementStatusToContent[isPaused ? 'PAUSED' : 'ENABLED'];

  return (
    <div>
      <h4>Management</h4>
      <div>{state.title}</div>
      <div className="horizontal middle sp-margin-s-top">
        <button
          className={classnames(BTN_CLASSNAMES, state.btnClassName)}
          onClick={() => onShowToggleManagementModal(!isPaused)}
        >
          {state.btnText}
        </button>
        {mutationInFlight && (
          <span className="sp-margin-s-left">
            <Spinner mode="circular" size="nano" color="var(--color-accent)" />
          </span>
        )}
      </div>
    </div>
  );
};

type InternalModalProps = Omit<IArtifactActionModalProps, 'title' | 'actionName'> & { application: string };

export const ResumeManagementModal = ({ application, ...props }: InternalModalProps) => {
  return (
    <ActionModal actionName="Resume" title="Resume Management" {...props}>
      <div className="flex-container-h middle sp-margin-xl-bottom">
        <span className="sp-margin-m-right" style={{ minWidth: 145 }}>
          <Illustration name="runManagement" />
        </span>
        <span>
          <p>
            You’re about to resume management for this application. The latest good version approved for deployment will
            be deployed to each environment, and any configuration changes made while disabled will take effect.
          </p>
        </span>
      </div>
    </ActionModal>
  );
};

export const DisableManagementModal = ({ application, ...props }: InternalModalProps) => {
  return (
    <ActionModal actionName="Disable" title="Disable Management" {...props}>
      <div className="flex-container-h middle sp-margin-xl-bottom">
        <span className="sp-margin-m-right" style={{ minWidth: 145 }}>
          <Illustration name="disableManagement" />
        </span>
        <span>
          <p>
            <span className="bold">
              Careful! You’re about to stop Spinnaker from managing all resources in your application.
            </span>
            This feature should only be used if management is not working properly and manual intervention is required.{' '}
            <a href={getDocsUrl('root')} target="_blank">
              Check our documentation for more information
            </a>
            .
          </p>
          <p>
            Need to rollback?{' '}
            <a href={getDocsUrl('pinning')} target="_blank">
              Try pinning a version instead
            </a>
            .
          </p>
        </span>
      </div>
    </ActionModal>
  );
};
