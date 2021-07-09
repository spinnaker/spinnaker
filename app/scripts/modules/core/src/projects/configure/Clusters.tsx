import { FieldArray, FormikErrors, FormikProps, getIn } from 'formik';
import React from 'react';

import { IAccount } from '../../account';
import { IProject, IProjectCluster } from '../../domain';
import { HelpField } from '../../help';
import { IWizardPageComponent } from '../../modal';
import { FormikFormField, ReactSelectInput, TextInput } from '../../presentation';

export interface IClustersProps {
  accounts: IAccount[];
}

export class Clusters extends React.Component<IClustersProps> implements IWizardPageComponent<IProject> {
  public validate = (value: IProject): FormikErrors<IProject> => {
    const applications = value.config.applications || [];
    if (value.config.clusters && value.config.clusters.length) {
      const clusterErrors = value.config.clusters.map((cluster) => {
        const errors: any = {};
        if (!cluster.account) {
          errors.account = 'Account must be specified';
        }

        const apps = cluster.applications || [];
        const applicationErrors = apps.map((app) => !applications.includes(app) && 'This app is not in the project');

        if (applicationErrors.some((val) => !!val)) {
          errors.applications = applicationErrors;
        }

        const areStackAndDetailDisabled = this.areStackAndDetailDisabled(cluster);

        const stackErrors =
          areStackAndDetailDisabled && cluster.stack !== '*' && 'Only * is valid for the selected account';
        if (stackErrors) {
          errors.stack = stackErrors;
        }
        const detailErrors =
          areStackAndDetailDisabled && cluster.detail !== '*' && 'Only * is valid for the selected account';
        if (detailErrors) {
          errors.detail = detailErrors;
        }

        return Object.keys(errors).length ? errors : null;
      });

      if (clusterErrors.some((val) => !!val)) {
        return {
          config: {
            clusters: clusterErrors,
          },
        } as any;
      }
    }

    return {};
  };

  private toggleAllApps(formik: FormikProps<any>, path: string) {
    const isChecked = !getIn(formik.values, path);
    formik.setFieldValue(path, isChecked ? [] : null);
  }

  private areStackAndDetailDisabled(cluster: IProjectCluster): boolean {
    if (!cluster || !cluster.account) {
      return false;
    }
    const account = this.props.accounts.find(({ name }) => name === cluster.account);
    return account.type === 'kubernetes';
  }

  public render() {
    const { accounts } = this.props;

    const tableHeader = (
      <tr>
        <td style={{ width: '200px' }}>Application</td>
        <td style={{ width: '200px' }}>Account</td>
        <td>
          Stack <HelpField id="project.cluster.stack" />
        </td>
        <td>
          Detail <HelpField id="project.cluster.detail" />
        </td>
        <td style={{ width: '30px' }} />
      </tr>
    );

    return (
      <FieldArray
        name="config.clusters"
        render={(clustersArrayHelpers) => {
          const formik = clustersArrayHelpers.form;
          const values: IProject = formik.values;
          const clusters: IProjectCluster[] = values.config.clusters || [];
          const applications: string[] = values.config.applications || [];
          const accountNames = accounts.map((account) => account.name);

          return (
            <section className="ConfigureProject-Clusters vertical center">
              <table style={{ width: '100%' }} className="table-condensed">
                <thead>{tableHeader}</thead>

                <tbody>
                  {clusters.map((cluster, idx) => {
                    const clusterPath = `config.clusters[${idx}]`;
                    const applicationsPath = `${clusterPath}.applications`;

                    return (
                      <tr key={idx}>
                        <td className="vertical">
                          <label className="sp-group-margin-s-xaxis">
                            <input
                              type="checkbox"
                              onChange={() => this.toggleAllApps(formik, applicationsPath)}
                              checked={!Array.isArray(cluster.applications)}
                            />
                            <span>All</span>
                          </label>

                          {!!cluster.applications && (
                            <FormikFormField
                              name={applicationsPath}
                              input={(props) => (
                                <ReactSelectInput multi={true} stringOptions={applications} {...props} />
                              )}
                            />
                          )}
                        </td>

                        <td>
                          <FormikFormField
                            name={`${clusterPath}.account`}
                            layout={({ input }) => <div>{input}</div>}
                            input={(props) => (
                              <ReactSelectInput {...props} clearable={false} stringOptions={accountNames} />
                            )}
                          />
                        </td>

                        <td>
                          <FormikFormField
                            name={`${clusterPath}.stack`}
                            input={(props) => <TextInput {...props} inputClassName="sp-padding-xs-xaxis" />}
                          />
                        </td>

                        <td>
                          <FormikFormField
                            name={`${clusterPath}.detail`}
                            input={(props) => <TextInput {...props} inputClassName="sp-padding-xs-xaxis" />}
                          />
                        </td>

                        <td>
                          <button className="nostyle" onClick={() => clustersArrayHelpers.remove(idx)}>
                            <i className="fas fa-trash-alt" />
                          </button>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>

              <a
                className="button zombie sp-margin-m horizontal middle center"
                onClick={() => clustersArrayHelpers.push({ stack: '*', detail: '*' })}
              >
                <i className="fas fa-plus-circle" /> Add Cluster
              </a>
            </section>
          );
        }}
      />
    );
  }
}
