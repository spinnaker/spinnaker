import { module } from 'angular';
import classNames from 'classnames';
import React from 'react';
import { react2angular } from 'react2angular';

import { Application } from '../../application.model';
import { ManagedWriter } from '../../../managed';
import { useLatestCallback, ValidationMessage } from '../../../presentation';
import { withErrorBoundary } from '../../../presentation/SpinErrorBoundary';
import { logger } from '../../../utils';
import { Spinner } from '../../../widgets/spinners/Spinner';

import './ManagedResourceConfig.less';

const { useState, useEffect } = React;

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

const ManagedResourceConfig = ({ application }: IManagedResourceConfigProps) => {
  const [pausePending, setPausePending] = useState(false);
  const [pauseFailed, setPauseFailed] = useState(false);
  const [paused, setPaused] = useState(application.isManagementPaused);

  const onRefresh = useLatestCallback(() => {
    setPaused(application.isManagementPaused);
  });
  useEffect(() => application.managedResources.onRefresh(null, onRefresh), [application]);

  const pauseManagement = () => {
    setPausePending(true);
    setPauseFailed(false);
    logClick('Pause Management', application.name);

    ManagedWriter.pauseApplicationManagement(application.name)
      .then(() => {
        setPaused(true);
        application.managedResources.refresh(true);
      })
      .catch(() => setPauseFailed(true))
      .finally(() => setPausePending(false));
  };

  const resumeManagement = () => {
    setPausePending(true);
    setPauseFailed(false);
    logClick('Resume Management', application.name);

    ManagedWriter.resumeApplicationManagement(application.name)
      .then(() => {
        setPaused(false);
        application.managedResources.refresh(true);
      })
      .catch(() => setPauseFailed(true))
      .finally(() => setPausePending(false));
  };

  const iconClass = paused ? 'fa-play' : 'fa-pause';

  return (
    <div className="ManagedResourceConfig">
      {getManagementStatus(paused)}
      <button
        className="btn btn-primary"
        disabled={pausePending}
        onClick={paused ? resumeManagement : pauseManagement}
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
        <a
          target="_blank"
          onClick={() => logClick('Documentation', application.name)}
          href="https://www.spinnaker.io/guides/user/managed-delivery"
        >
          Check out our documentation
        </a>
      </div>
    </div>
  );
};

export const MANAGED_RESOURCE_CONFIG = 'spinnaker.core.managedResourceConfig.component';
module(MANAGED_RESOURCE_CONFIG, []).component(
  'managedResourceConfig',
  react2angular(withErrorBoundary(ManagedResourceConfig, 'managedResourceConfig'), ['application']),
);
