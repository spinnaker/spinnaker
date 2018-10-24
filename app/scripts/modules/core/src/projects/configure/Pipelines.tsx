import * as React from 'react';
import { FieldArray, FormikErrors, getIn } from 'formik';

import { FormikFormField, ReactSelectInput, StringsAsOptions } from 'core/presentation';
import { Spinner } from 'core/widgets';
import { IPipeline, IProject, IProjectPipeline } from 'core/domain';
import { IWizardPageProps, wizardPage } from 'core/modal';

export interface IPipelinesProps extends IWizardPageProps<{}> {
  appsPipelines: {
    [appName: string]: IPipeline[];
  };
}

class PipelinesImpl extends React.Component<IPipelinesProps> {
  public static LABEL = 'Pipelines';

  public validate = (value: IProject): FormikErrors<IProject> | void => {
    const projectApplications = (value.config && value.config.applications) || [];
    const { appsPipelines } = this.props;

    if (value.config && value.config.pipelineConfigs && value.config.pipelineConfigs.length) {
      const pipelineConfigErrors = value.config.pipelineConfigs.map(config => {
        const pipelineIdsForApp = (appsPipelines[config.application] || []).map(p => p.id);

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

      if (pipelineConfigErrors.some(val => !!val)) {
        return {
          config: {
            pipelineConfigs: pipelineConfigErrors,
          },
        };
      }
    }

    return {};
  };

  public render() {
    const { appsPipelines } = this.props;

    const tableHeader = (
      <tr>
        <td>App</td>
        <td>Pipeline</td>
        <td style={{ width: '30px' }} />
      </tr>
    );

    const pipelineConfigsPath = 'config.pipelineConfigs';

    return (
      <FieldArray
        name={pipelineConfigsPath}
        render={pipelinesArrayHelper => {
          const project: IProject = pipelinesArrayHelper.form.values;
          const configs: IProjectPipeline[] = getIn(project, pipelineConfigsPath);
          const apps: string[] = getIn(project, 'config.applications');

          return (
            <div className="ConfigureProject-Pipelines vertical center">
              <div className="vertical center" style={{ width: '100%' }}>
                <table style={{ width: '100%' }} className="table-condensed">
                  <thead>{tableHeader}</thead>
                  <tbody>
                    {configs.map((config, idx) => {
                      const pipelinePath = `${pipelineConfigsPath}[${idx}]`;
                      const application = config && config.application;
                      const appPipelines = application && appsPipelines[application];
                      const pipelineOptions = appPipelines && appPipelines.map(p => ({ label: p.name, value: p.id }));

                      const key = `${application}-${config && config.pipelineConfigId}-${idx}`;

                      return (
                        <tr key={key}>
                          <td>
                            <FormikFormField
                              name={`${pipelinePath}.application`}
                              layout={({ input }) => <div>{input}</div>}
                              input={props => (
                                <StringsAsOptions strings={apps}>
                                  {options => <ReactSelectInput {...props} clearable={false} options={options} />}
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
                                input={props => (
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

export const Pipelines = wizardPage(PipelinesImpl);
