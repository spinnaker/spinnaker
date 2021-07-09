import classnames from 'classnames';
import React from 'react';

import { Illustration } from '@spinnaker/presentation';

import { ApplicationQueryError } from '../ApplicationQueryError';
import { DeliveryConfig } from './DeliveryConfig';
import { useFetchApplicationManagementStatusQuery, useToggleManagementMutation } from '../graphql/graphql-sdk';
import { showModal, useApplicationContextSafe } from '../../presentation';
import { ActionModal, IArtifactActionModalProps } from '../utils/ActionModal';
import { MODAL_MAX_WIDTH, spinnerProps } from '../utils/defaults';
import { useLogEvent } from '../utils/logging';
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
  return (
    <div className="full-width">
      <ManagementToggle />
      <DeliveryConfig />
    </div>
  );
};

const ManagementToggle = () => {
  const app = useApplicationContextSafe();
  const appName = app.name;
  const logEvent = useLogEvent('Management');
  const { data, error, loading, refetch } = useFetchApplicationManagementStatusQuery({ variables: { appName } });
  const [toggleManagement, { loading: mutationInFlight }] = useToggleManagementMutation();

  const onShowToggleManagementModal = React.useCallback((shouldPause: boolean) => {
    logEvent({ action: 'OpenModal', data: { shouldPause } });
    showModal(
      shouldPause ? DisableManagementModal : ResumeManagementModal,
      {
        application: appName,
        onAction: async () => {
          await toggleManagement({ variables: { application: appName, isPaused: shouldPause } });
          refetch();
        },
        logCategory: 'Management',
        onSuccess: refetch,
        withComment: false,
      },
      { maxWidth: MODAL_MAX_WIDTH },
    );
  }, []);

  if (loading) {
    return <Spinner {...spinnerProps} message="Loading settings..." />;
  }

  if (error) {
    return <ApplicationQueryError hasApplicationData={Boolean(data?.application)} error={error} />;
  }

  const isPaused = Boolean(data?.application?.isPaused);
  const state = managementStatusToContent[isPaused ? 'PAUSED' : 'ENABLED'];

  return (
    <div>
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
            <a href="https://www.spinnaker.io/guides/user/managed-delivery" target="_blank">
              Check our documentation for more information
            </a>
            .
          </p>
          <p>
            Need to rollback?{' '}
            <a href="https://www.spinnaker.io/guides/user/managed-delivery/pinning/" target="_blank">
              Try pinning a version instead
            </a>
            .
          </p>
        </span>
      </div>
    </ActionModal>
  );
};
