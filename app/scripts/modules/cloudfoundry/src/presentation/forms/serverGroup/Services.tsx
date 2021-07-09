import { FieldArray, getIn } from 'formik';
import React from 'react';

import { FormikFormField, TextInput } from '@spinnaker/core';
import { ICloudFoundryCreateServerGroupCommand } from '../../../serverGroup/configure/serverGroupConfigurationModel.cf';

export interface IServicesProps {
  fieldName: string;
}

export const Services: React.SFC<IServicesProps> = ({ fieldName }: IServicesProps) => {
  return (
    <div>
      <div className="form-group">
        <div className="col-md-12">
          <b>Bind Services</b>
          <FieldArray
            name={fieldName}
            render={(arrayHelpers) => {
              const serverGroupCommand: ICloudFoundryCreateServerGroupCommand = arrayHelpers.form.values;
              const services: string[] = getIn(serverGroupCommand, fieldName) || [];

              return (
                <table className="table table-condensed packed metadata">
                  <tbody>
                    {services.map((_, index: number) => (
                      <tr key={index}>
                        <td>
                          <div className="sp-margin-m-bottom">
                            <FormikFormField
                              name={`${fieldName}[${index}]`}
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
};
