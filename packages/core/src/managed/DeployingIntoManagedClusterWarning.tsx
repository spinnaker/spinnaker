import { FormikProps } from 'formik';
import React from 'react';

import { Application } from '../application';
import { IServerGroupCommand } from '../serverGroup';

import { toggleResourcePause } from './toggleResourceManagement';

export interface IDeployingIntoManagedClusterWarningProps {
  app: Application;
  formik: FormikProps<IServerGroupCommand>;
}

export const DeployingIntoManagedClusterWarning = ({ app, formik }: IDeployingIntoManagedClusterWarningProps) => {
  const [userPaused, setUserPaused] = React.useState(false);

  const command = formik.values;
  const pauseResource = React.useCallback(() => {
    const { resourceSummary, backingData } = formik.values;
    if (!resourceSummary) return;
    toggleResourcePause(resourceSummary, app).then(
      () => {
        backingData.managedResources = app.getDataSource('managedResources')?.data?.resources;
        setUserPaused(true);
        formik.setFieldValue('resourceSummary', null);
      },
      () => {},
    );
  }, [app, formik]);

  if (!command.resourceSummary && !userPaused) {
    return null;
  }

  if (userPaused) {
    return (
      <div className="alert alert-info">
        <div className="horizontal top">
          <div>
            <i className="fa fa-check-circle" />
          </div>
          <div className="sp-margin-m-left">Resource management has been paused.</div>
        </div>
      </div>
    );
  }

  return (
    <div className="alert alert-danger">
      <p>
        <b>🌈 Spinnaker is managing this resource.</b>
      </p>
      <p>Any changes you make to this cluster will be overridden in favor of the desired state.</p>
      <p>If you need to manually deploy a new version of this server group, you should pause management.</p>
      <div className="sp-margin-m-top">
        <button className="passive" onClick={pauseResource}>
          <i className="fa fa-pause" /> Pause management
        </button>
      </div>
    </div>
  );
};
