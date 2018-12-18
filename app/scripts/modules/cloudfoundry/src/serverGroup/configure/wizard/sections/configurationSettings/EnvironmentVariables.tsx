import * as React from 'react';
import { FieldArray, getIn } from 'formik';

import { FormikFormField, IWizardPageProps, TextInput } from '@spinnaker/core';

import { ICloudFoundryCreateServerGroupCommand } from 'cloudfoundry/serverGroup/configure/serverGroupConfigurationModel.cf';
import { ICloudFoundryEnvVar } from 'cloudfoundry/domain';

export interface IEnvironmentVariablesProps extends IWizardPageProps<ICloudFoundryCreateServerGroupCommand> {}

export class EnvironmentVariables extends React.Component<IEnvironmentVariablesProps> {
  public render() {
    return (
      <div>
        <div className="form-group">
          <div className="col-md-12">
            <b>Environment Variables</b>
            <FieldArray
              name="manifest.environment"
              render={arrayHelpers => {
                const serverGroupCommand: ICloudFoundryCreateServerGroupCommand = arrayHelpers.form.values;
                const environmentVariables: string[] = getIn(serverGroupCommand, 'manifest.environment')
                  ? getIn(serverGroupCommand, 'manifest.environment')
                  : [];

                return (
                  <table className="table table-condensed packed tags">
                    <thead>
                      <tr>
                        <th>Name</th>
                        <th>Value</th>
                      </tr>
                    </thead>
                    <tbody>
                      {environmentVariables.map((_, index: number) => {
                        const envPath = `manifest.environment[${index}]`;
                        return (
                          <tr key={index}>
                            <td>
                              <FormikFormField
                                name={`${envPath}.key`}
                                input={props => <TextInput {...props} />}
                                required={true}
                              />
                            </td>
                            <td>
                              <FormikFormField
                                name={`${envPath}.value`}
                                input={props => <TextInput {...props} />}
                                required={true}
                              />
                            </td>
                            <td>
                              <a className="btn btn-link sm-label">
                                <span
                                  className="glyphicon glyphicon-trash"
                                  onClick={() => arrayHelpers.remove(index)}
                                />
                              </a>
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                    <tfoot>
                      <tr>
                        <td colSpan={3}>
                          <button
                            type="button"
                            className="add-new col-md-12"
                            onClick={() => arrayHelpers.push({ key: '', value: '' } as ICloudFoundryEnvVar)}
                          >
                            <span className="glyphicon glyphicon-plus-sign" /> Add New Environment Variable
                          </button>
                        </td>
                      </tr>
                    </tfoot>
                  </table>
                );
              }}
            />
          </div>
        </div>
      </div>
    );
  }
}
