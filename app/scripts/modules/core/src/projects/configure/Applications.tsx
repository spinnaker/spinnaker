import * as React from 'react';
import { FormikErrors, getIn } from 'formik';
import { isEqual } from 'lodash';

import { IProject } from 'core/domain';
import { IWizardPageProps, wizardPage } from 'core/modal';
import { FormikApplicationsPicker } from 'core/projects/configure/FormikApplicationsPicker';

export interface IApplicationsProps extends IWizardPageProps<IProject> {
  allApplications: string[];
  onApplicationsChanged: (applications: string[]) => void;
}

class ApplicationsImpl extends React.Component<IApplicationsProps> {
  public static LABEL = 'Applications';

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

  public componentDidMount() {
    const apps = getIn(this.props.formik.values, 'config.applications', []);
    this.props.onApplicationsChanged && this.props.onApplicationsChanged(apps);
  }

  public componentDidUpdate(prevProps: IApplicationsProps) {
    const prevApps = getIn(prevProps.formik.values, 'config.applications', []);
    const nextApps = getIn(this.props.formik.values, 'config.applications', []);

    if (!isEqual(prevApps, nextApps)) {
      this.props.onApplicationsChanged && this.props.onApplicationsChanged(nextApps);
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

export const Applications = wizardPage(ApplicationsImpl);
