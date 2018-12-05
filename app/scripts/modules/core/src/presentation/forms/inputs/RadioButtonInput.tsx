import * as React from 'react';
import { Option } from 'react-select';

import { Markdown, OmitControlledInputPropsFrom, StringsAsOptions } from 'core/presentation';

import { isStringArray, orEmptyString, validationClassName } from './utils';
import { IFormInputProps } from '../interface';

interface IRadioButtonInputProps
  extends IFormInputProps,
    OmitControlledInputPropsFrom<React.TextareaHTMLAttributes<any>> {
  options: Array<string | Option<string | number>>;
  inputClassName?: string;
}

export const RadioButtonInput = (props: IRadioButtonInputProps) => {
  const { value, validation, inputClassName, options, ...otherProps } = props;
  const className = `RadioButtonInput radio ${orEmptyString(inputClassName)} ${validationClassName(validation)}`;

  const RadioButtonsElement = ({ opts }: { opts: Array<Option<string>> }) => (
    <div className="vertical left">
      {opts.map(option => (
        <div key={option.label} className={className}>
          <label>
            <input type="radio" {...otherProps} value={option.value} checked={option.value === value} />
            <span className="marked">
              <Markdown message={option.label} />
            </span>
          </label>
        </div>
      ))}
    </div>
  );

  if (isStringArray(options)) {
    return <StringsAsOptions strings={options}>{opts => <RadioButtonsElement opts={opts} />}</StringsAsOptions>;
  } else {
    return <RadioButtonsElement opts={options as Array<Option<string>>} />;
  }
};
