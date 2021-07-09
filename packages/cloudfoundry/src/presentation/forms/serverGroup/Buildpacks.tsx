import { FieldArray, getIn } from 'formik';
import React from 'react';

import { FormikFormField, TextInput } from '@spinnaker/core';
import { ICloudFoundryCreateServerGroupCommand } from '../../../serverGroup/configure/serverGroupConfigurationModel.cf';

export interface IBuildpacksProps {
  fieldName: string;
  onChange?: (value: string[]) => void;
}

export class Buildpacks extends React.Component<IBuildpacksProps> {
  public render() {
    const { fieldName, onChange } = this.props;
    return (
      <div>
        <div className="form-group">
          <div className="col-md-12">
            <b>Buildpacks</b>
            <FieldArray
              name={fieldName}
              render={(arrayHelpers) => {
                const serverGroupCommand: ICloudFoundryCreateServerGroupCommand = arrayHelpers.form.values;
                const buildpacks: string[] = getIn(serverGroupCommand, fieldName) || [];

                return (
                  <table className="table table-condensed packed metadata">
                    <tbody>
                      {buildpacks.map((_, index: number) => (
                        <tr key={index}>
                          <td>
                            <div className="sp-margin-m-bottom">
                              <FormikFormField
                                name={`${fieldName}[${index}]`}
                                onChange={() => {
                                  onChange && onChange(getIn(serverGroupCommand, fieldName) || []);
                                }}
                                input={(props) => <TextInput {...props} />}
                                required={true}
                              />
                            </div>
                          </td>
                          <td>
                            <a className="btn btn-link sm-label" onClick={() => arrayHelpers.remove(index)}>
                              <span className="glyphicon glyphicon-trash" />
                            </a>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                    <tfoot>
                      <tr>
                        <td colSpan={2}>
                          <button type="button" className="add-new col-md-12" onClick={() => arrayHelpers.push('')}>
                            <span className="glyphicon glyphicon-plus-sign" /> Add New Buildpack
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
