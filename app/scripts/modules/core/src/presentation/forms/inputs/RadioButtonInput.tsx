import React from 'react';
import { Option } from 'react-select';

import { Markdown } from '../../Markdown';
import { OmitControlledInputPropsFrom } from './interface';

import { isStringArray, orEmptyString, validationClassName } from './utils';
import { IFormInputProps } from './interface';

interface IRadioButtonInputProps
  extends IFormInputProps,
    OmitControlledInputPropsFrom<React.TextareaHTMLAttributes<any>> {
  stringOptions?: string[];
  options?: IRadioButtonOptions[];
  inputClassName?: string;
  inline?: boolean;
}

interface IRadioButtonOptions extends Option {
  help?: React.ReactNode;
}

export const RadioButtonInput = (props: IRadioButtonInputProps) => {
  const { inline, validation, value, inputClassName, options, stringOptions, ...otherProps } = props;
  const radioOptions: IRadioButtonOptions[] = isStringArray(stringOptions)
    ? stringOptions.map(opt => ({ value: opt, label: opt }))
    : options;

  const userClassName = orEmptyString(inputClassName);
  const validClassName = validationClassName(validation);
  const layoutClassName = inline ? 'flex-container-h margin-between-md' : 'flex-container-v margin-between-sm';
  const elementClassName = `RadioButtonInput radio ${userClassName} ${validClassName} ${layoutClassName}`;

  return (
    <div className={elementClassName}>
      {radioOptions.map(option => (
        <RadioButton key={option.label} value={value} option={option} {...otherProps} />
      ))}
    </div>
  );
};

interface IRadioButtonProps {
  option: IRadioButtonOptions;
  value: any;
}
const RadioButton = ({ option, value, ...otherProps }: IRadioButtonProps) => (
  <label className={'clickable'}>
    <input type="radio" {...otherProps} value={option.value as any} checked={option.value === value} />
    <Markdown message={option.label} style={option.help && { display: 'inline-block' }} />
    {option.help && <>{option.help}</>}
  </label>
);
