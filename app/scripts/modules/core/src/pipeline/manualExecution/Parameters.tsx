import { FormikProps } from 'formik';
import React from 'react';

import { IParameter, IPipelineCommand } from '../../domain';
import { HelpField } from '../../help';
import { DayPickerInput, FormikFormField, ReactSelectInput, TextInput } from '../../presentation';

export interface IParametersProps {
  formik: FormikProps<IPipelineCommand>;
  parameters: IParameter[];
}

export class Parameters extends React.Component<IParametersProps> {
  private shouldInclude = (p: IParameter) => {
    const { values } = this.props.formik;
    if (p.conditional) {
      const comparingTo = values.parameters[p.conditional.parameter];
      const value = p.conditional.comparatorValue;
      switch (p.conditional.comparator) {
        case '>':
          return parseFloat(comparingTo) > parseFloat(value);
        case '>=':
          return parseFloat(comparingTo) >= parseFloat(value);
        case '<':
          return parseFloat(comparingTo) < parseFloat(value);
        case '<=':
          return parseFloat(comparingTo) <= parseFloat(value);
        case '!=':
          return comparingTo !== value;
        case '=':
          return comparingTo === value;
      }
    }
    return true;
  };

  public render() {
    const { parameters } = this.props;
    const hasRequiredParameters = parameters.some((p) => p.required);
    const visibleParameters = parameters.filter((p) => !p.conditional || this.shouldInclude(p));
    /* We need to use bracket notation because parameter names are strings that can have all sorts of characters */
    const formikFieldNameForParam = (param: IParameter) => `parameters["${param.name}"]`;
    return (
      <>
        <p className="manual-execution-parameters-description">
          This pipeline is parameterized. Please enter values for the parameters below.
        </p>
        {visibleParameters &&
          visibleParameters.map((parameter, i) => {
            const fieldProps = {
              name: formikFieldNameForParam(parameter),
              label: parameter.label || parameter.name,
              help: parameter.description && <HelpField content={parameter.description} />,
              fastField: false,
              required: parameter.required,
            };

            return (
              <React.Fragment key={i}>
                {!parameter.hasOptions && parameter.constraint === 'date' && (
                  <FormikFormField
                    {...fieldProps}
                    input={(props) => <DayPickerInput {...props} format="yyyy-MM-dd" />}
                  />
                )}
                {!parameter.hasOptions && !parameter.constraint && (
                  <FormikFormField
                    {...fieldProps}
                    input={(props) => <TextInput {...props} inputClassName="form-control input-sm" />}
                  />
                )}
                {parameter.hasOptions && (
                  <FormikFormField
                    {...fieldProps}
                    input={(props) => (
                      <ReactSelectInput
                        {...props}
                        clearable={false}
                        inputClassName="parameter-option-select"
                        options={parameter.options.map((o) => ({ label: `${o.value}`, value: o.value }))}
                      />
                    )}
                  />
                )}
              </React.Fragment>
            );
          })}
        {hasRequiredParameters && (
          <div className="form-group sp-margin-l-top">
            <div className="col-md-4 col-md-offset-4">
              <em>* Required</em>
            </div>
          </div>
        )}
      </>
    );
  }
}
