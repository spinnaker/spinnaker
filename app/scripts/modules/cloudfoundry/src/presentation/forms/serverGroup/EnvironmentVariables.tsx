import { FieldArray, getIn } from 'formik';
import React from 'react';

import { FormikFormField, TextInput } from '@spinnaker/core';
import { ICloudFoundryEnvVar } from '../../../domain';
import { ICloudFoundryCreateServerGroupCommand } from '../../../serverGroup/configure/serverGroupConfigurationModel.cf';

export interface IEnvironmentVariablesProps {
  fieldName: string;
  onChange?: (value: string[]) => void;
}

export class EnvironmentVariables extends React.Component<IEnvironmentVariablesProps> {
  public render() {
    const { fieldName, onChange } = this.props;
    return (
      <div>
        <div className="form-group">
          <div className="col-md-12">
            <b>Environment Variables</b>
            <FieldArray
              name={fieldName}
              render={(arrayHelpers) => {
                const serverGroupCommand: ICloudFoundryCreateServerGroupCommand = arrayHelpers.form.values;
                const environmentVariables: string[] = getIn(serverGroupCommand, fieldName) || [];

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
                        const envPath = `${fieldName}[${index}]`;
                        return (
                          <tr key={index}>
                            <td>
                              <FormikFormField
                                name={`${envPath}.key`}
                                onChange={() => {
                                  onChange && onChange(getIn(serverGroupCommand, fieldName) || []);
                                }}
                                input={(props) => <TextInput {...props} />}
                                required={true}
                              />
                            </td>
                            <td>
                              <FormikFormField
                                name={`${envPath}.value`}
                                onChange={() => {
                                  onChange && onChange(getIn(serverGroupCommand, fieldName) || []);
                                }}
                                input={(props) => <TextInput {...props} />}
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
