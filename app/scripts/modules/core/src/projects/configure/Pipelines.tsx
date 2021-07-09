import { FieldArray, FormikErrors, FormikProps, getIn } from 'formik';
import { chain, get, isEqual } from 'lodash';
import React from 'react';

import { IPipeline, IProject, IProjectPipeline } from '../../domain';
import { IWizardPageComponent } from '../../modal';
import { PipelineConfigService } from '../../pipeline';
import { FormikFormField, ReactSelectInput, StringsAsOptions } from '../../presentation';
import { Spinner } from '../../widgets';

export interface IPipelinesProps {
  formik: FormikProps<IProject>;
}

export interface IPipelinesState {
  appsPipelines: {
    [appName: string]: IPipeline[];
  };
  initialized: boolean;
}

export class Pipelines
  extends React.Component<IPipelinesProps, IPipelinesState>
  implements IWizardPageComponent<IProject> {
  private static readonly pipelineConfigsPath = 'config.pipelineConfigs';

  public state: IPipelinesState = {
    appsPipelines: {},
    initialized: false,
  };

  public validate = (value: IProject): FormikErrors<IProject> => {
    const projectApplications = (value.config && value.config.applications) || [];
    const { appsPipelines, initialized } = this.state;

    if (initialized && value.config && value.config.pipelineConfigs && value.config.pipelineConfigs.length) {
      const pipelineConfigErrors = value.config.pipelineConfigs.map((config) => {
        const pipelineIdsForApp = appsPipelines[config.application].map((p) => p.id);

        if (!config.application) {
          return { application: 'Application must be specified' };
        } else if (!projectApplications.includes(config.application)) {
          return { application: 'This application is not part of the project' };
        } else if (!config.pipelineConfigId) {
          return { pipelineConfigId: 'Pipeline must be specified' };
        } else if (!pipelineIdsForApp.includes(config.pipelineConfigId)) {
          return { pipelineConfigId: `Pipeline does not exist in ${config.application}` };
        }

        return null;
      });

      if (pipelineConfigErrors.some((val) => !!val)) {
        return {
          config: {
            pipelineConfigs: pipelineConfigErrors as any,
          },
        };
      }
    }

    return {};
  };

  private getProjectPipelines = (props: IPipelinesProps): IProjectPipeline[] => {
    return get(props.formik.values, Pipelines.pipelineConfigsPath, []);
  };

  private fetchPipelinesForApps = (projectPipelines: IProjectPipeline[]) => {
    const appsToFetch = chain(projectPipelines)
      .map('application')
      .uniq()
      // Only fetch for apps we don't already have results for
      .filter((appName) => appName && !this.state.appsPipelines[appName])
      .value();

    const appsPipelines: { [appName: string]: IPipeline[] } = { ...this.state.appsPipelines };

    Promise.all(
      appsToFetch.map((appName) => {
        return PipelineConfigService.getPipelinesForApplication(appName)
          .then((pipelines) => {
            appsPipelines[appName] = pipelines;
          })
          .catch(() => {
            appsPipelines[appName] = [];
          });
      }),
    ).then(() => {
      this.setState({ appsPipelines, initialized: true });
    });
  };

  public componentDidMount() {
    this.fetchPipelinesForApps(this.getProjectPipelines(this.props));
  }

  public componentDidUpdate(prevProps: IPipelinesProps) {
    if (!isEqual(this.getProjectPipelines(prevProps), this.getProjectPipelines(this.props))) {
      this.fetchPipelinesForApps(this.getProjectPipelines(this.props));
    }
  }

  public render() {
    const { appsPipelines, initialized } = this.state;

    if (!initialized) {
      return (
        <div style={{ height: '200px' }}>
          <Spinner size="medium" />
        </div>
      );
    }

    const tableHeader = (
      <tr>
        <td>App</td>
        <td>Pipeline</td>
        <td style={{ width: '30px' }} />
      </tr>
    );

    return (
      <FieldArray
        name={Pipelines.pipelineConfigsPath}
        render={(pipelinesArrayHelper) => {
          const project: IProject = pipelinesArrayHelper.form.values;
          const configs: IProjectPipeline[] = getIn(project, Pipelines.pipelineConfigsPath);
          const apps: string[] = getIn(project, 'config.applications');

          return (
            <div className="ConfigureProject-Pipelines vertical center">
              <div className="vertical center" style={{ width: '100%' }}>
                <table style={{ width: '100%' }} className="table-condensed">
                  <thead>{tableHeader}</thead>
                  <tbody>
                    {configs.map((config, idx) => {
                      const pipelinePath = `${Pipelines.pipelineConfigsPath}[${idx}]`;
                      const application = config && config.application;
                      const appPipelines = application && appsPipelines[application];
                      const pipelineOptions = appPipelines && appPipelines.map((p) => ({ label: p.name, value: p.id }));

                      const key = `${application}-${config && config.pipelineConfigId}-${idx}`;

                      return (
                        <tr key={key}>
                          <td>
                            <FormikFormField
                              name={`${pipelinePath}.application`}
                              layout={({ input }) => <div>{input}</div>}
                              input={(props) => (
                                <StringsAsOptions strings={apps}>
                                  {(options) => <ReactSelectInput {...props} clearable={false} options={options} />}
                                </StringsAsOptions>
                              )}
                            />
                          </td>

                          <td>
                            {!application ? null : !pipelineOptions ? (
                              <Spinner />
                            ) : (
                              <FormikFormField
                                name={`${pipelinePath}.pipelineConfigId`}
                                layout={({ input }) => <div>{input}</div>}
                                input={(props) => (
                                  <ReactSelectInput {...props} clearable={false} options={pipelineOptions} />
                                )}
                              />
                            )}
                          </td>

                          <td>
                            <button className="nostyle" onClick={() => pipelinesArrayHelper.remove(idx)}>
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
                  onClick={() => pipelinesArrayHelper.push({})}
                >
                  <i className="fas fa-plus-circle" /> Add Pipeline
                </a>
              </div>
            </div>
          );
        }}
      />
    );
  }
}
