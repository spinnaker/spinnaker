import * as React from 'react';
import { Option } from 'react-select';

import { IFormInputProps } from '../interface';

import { orEmptyString, validationClassName } from './utils';
import { Markdown } from 'core/presentation';

interface IRadioButtonInputProps extends IFormInputProps, React.TextareaHTMLAttributes<any> {
  options: Array<Option<string | number>>;
  inputClassName?: string;
}

export const RadioButtonInput = (props: IRadioButtonInputProps) => {
  const { field, validation, inputClassName, options } = props;
  const { value, onBlur, onChange, name } = field;

  const fieldProps = { name, onChange, onBlur };
  const className = `RadioButtonInput radio ${orEmptyString(inputClassName)} ${validationClassName(validation)}`;

  return (
    <div className="vertical left">
      {options.map(option => (
        <div key={option.label} className={className}>
          <label>
            <input type="radio" {...fieldProps} value={option.value} checked={option.value === value} />
            <span className="marked">
              <Markdown message={option.label} />
            </span>
          </label>
        </div>
      ))}
    </div>
  );
};
