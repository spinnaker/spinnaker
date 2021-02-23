import { isNil } from 'lodash';
import React, { useEffect, useMemo } from 'react';
import { Option } from 'react-select';

import { Markdown } from '../../Markdown';
import { OmitControlledInputPropsFrom } from './interface';
import { IFormInputProps } from './interface';
import { createFakeReactSyntheticEvent, isStringArray, orEmptyString, validationClassName } from './utils';

interface IRadioButtonInputProps
  extends IFormInputProps,
    OmitControlledInputPropsFrom<React.TextareaHTMLAttributes<any>> {
  stringOptions?: string[];
  options?: IRadioButtonOptions[];
  inputClassName?: string;
  /** When true, will render the radio buttons horizontally */
  inline?: boolean;
  /**
   * If the value prop does not match any of the options, this value will be used.
   * This can be used to ensures that a valid option is always selected (for initial state, for example).
   * This mechanism calls onChange with the defaultValue.
   * If this is used, the options prop provided should be stable (useMemo)
   */
  defaultValue?: string;
}

interface IRadioButtonOptions extends Option {
  help?: React.ReactNode;
}

export const RadioButtonInput = (props: IRadioButtonInputProps) => {
  const { inline, validation, value, defaultValue, inputClassName, options, stringOptions, ...otherProps } = props;
  const radioOptions: IRadioButtonOptions[] = useMemo(
    () => (isStringArray(stringOptions) ? stringOptions.map((opt) => ({ value: opt, label: opt })) : options),
    [options],
  );

  useEffect(() => {
    if (!isNil(defaultValue) && !radioOptions.map((opt) => opt.value).includes(value)) {
      props.onChange(createFakeReactSyntheticEvent({ name: props.name, value: defaultValue }));
    }
  }, [value, defaultValue, radioOptions]);

  const userClassName = orEmptyString(inputClassName);
  const validClassName = validationClassName(validation);
  const layoutClassName = inline ? 'flex-container-h margin-between-md' : 'flex-container-v margin-between-sm';
  const elementClassName = `RadioButtonInput radio ${userClassName} ${validClassName} ${layoutClassName}`;

  return (
    <div className={elementClassName}>
      {radioOptions.map((option) => (
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
