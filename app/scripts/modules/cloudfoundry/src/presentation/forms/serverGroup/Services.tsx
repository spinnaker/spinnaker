import * as React from 'react';
import { FieldArray, getIn } from 'formik';

import { FormikFormField, TextInput } from '@spinnaker/core';

import { ICloudFoundryCreateServerGroupCommand } from 'cloudfoundry/serverGroup/configure/serverGroupConfigurationModel.cf';

export interface IServicesProps {}

export class Services extends React.Component<IServicesProps> {
  public render() {
    return (
      <div>
        <div className="form-group">
          <div className="col-md-12">
            <b>Bind Services</b>
            <FieldArray
              name="manifest.services"
              render={arrayHelpers => {
                const serverGroupCommand: ICloudFoundryCreateServerGroupCommand = arrayHelpers.form.values;
                const services: string[] = getIn(serverGroupCommand, 'manifest.services') || [];

                return (
                  <table className="table table-condensed packed metadata">
                    <tbody>
                      {services.map((_, index: number) => (
                        <tr key={index}>
                          <td>
                            <div className="sp-margin-m-bottom">
                              <FormikFormField
                                name={`manifest.services[${index}]`}
                                input={props => <TextInput {...props} />}
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
                            <span className="glyphicon glyphicon-plus-sign" /> Add New Service
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
