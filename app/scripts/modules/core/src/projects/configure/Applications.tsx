import React from 'react';
import { FormikErrors, getIn, FormikProps } from 'formik';
import { isEqual } from 'lodash';

import { IProject, IProjectPipeline } from 'core/domain';
import { IWizardPageComponent } from 'core/modal';
import { FormikApplicationsPicker } from './FormikApplicationsPicker';

export interface IApplicationsProps {
  formik: FormikProps<IProject>;
  allApplications: string[];
}

export class Applications extends React.Component<IApplicationsProps> implements IWizardPageComponent<IProject> {
  public validate(project: IProject): FormikErrors<IProject> {
    const configuredApps = (project.config && project.config.applications) || [];
    const getApplicationError = (app: string) =>
      this.props.allApplications.includes(app) ? undefined : `Application '${app}' does not exist.`;

    const applicationErrors = configuredApps.map(getApplicationError);
    if (!applicationErrors.some(err => !!err)) {
      return {};
    }

    return {
      config: {
        applications: applicationErrors,
      },
    } as any;
  }

  public componentDidUpdate(prevProps: IApplicationsProps) {
    const prevApps = getIn(prevProps.formik.values, 'config.applications', []);
    const nextApps = getIn(this.props.formik.values, 'config.applications', []);

    if (!isEqual(prevApps, nextApps)) {
      // Remove any pipelines associated with the applications removed.
      const existingPipelineConfigs: IProjectPipeline[] = getIn(this.props.formik.values, 'config.pipelineConfigs', []);
      const newPipelineConfigs = existingPipelineConfigs.filter(({ application }) => nextApps.includes(application));
      this.props.formik.setFieldValue('config.pipelineConfigs', newPipelineConfigs);
    }
  }

  public render() {
    const { allApplications } = this.props;

    return (
      <FormikApplicationsPicker
        className="ConfigureProject-Applications"
        name="config.applications"
        applications={allApplications}
      />
    );
  }
}
