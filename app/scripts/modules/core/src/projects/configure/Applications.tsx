import * as React from 'react';
import { FormikErrors, getIn } from 'formik';
import { Effect } from 'formik-effect';
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
    };
  }

  public render() {
    const { allApplications } = this.props;

    return (
      <>
        <Effect<IProject>
          onChange={(prev, next) => {
            const prevApps = getIn(prev.values, 'config.applications', []);
            const nextApps = getIn(next.values, 'config.applications', []);

            if (!isEqual(prevApps, nextApps)) {
              this.props.onApplicationsChanged && this.props.onApplicationsChanged(nextApps);
            }
          }}
        />

        <FormikApplicationsPicker
          className="ConfigureProject-Applications"
          applications={allApplications}
          name="config.applications"
        />
      </>
    );
  }
}

export const Applications = wizardPage(ApplicationsImpl);
