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
  const verticalClassName = inline ? '' : 'vertical left';
  const elementClassName = `RadioButtonInput radio ${userClassName} ${validClassName} ${verticalClassName}`;

  return (
    <div className={elementClassName}>
      {radioOptions.map(option => (
        <RadioButton inline={inline} key={option.label} value={value} option={option} {...otherProps} />
      ))}
    </div>
  );
};

interface IRadioButtonProps {
  option: IRadioButtonOptions;
  inline: boolean;
  value: any;
}
const RadioButton = ({ option, inline, value, ...otherProps }: IRadioButtonProps) => (
  <label className={inline ? 'radio-inline clickable' : 'inline clickable'}>
    <input type="radio" {...otherProps} value={option.value as any} checked={option.value === value} />
    <Markdown message={option.label} style={option.help && { display: 'inline-block' }} />
    {option.help && <> {option.help}</>}
  </label>
);
