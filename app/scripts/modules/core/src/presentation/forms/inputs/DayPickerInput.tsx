import { defaults } from 'lodash';
import React from 'react';
import { DayPickerInputProps } from 'react-day-picker';
import DayPicker from 'react-day-picker/DayPickerInput';

import { IFormInputProps } from './interface';
import { createFakeReactSyntheticEvent, orEmptyString, validationClassName } from './utils';

import './DayPickerInput.less';
import 'react-day-picker/lib/style.css';

export function DayPickerInput(props: IFormInputProps & DayPickerInputProps) {
  const { validation, inputClassName, classNames, onChange, onBlur, name, ...rest } = props;
  const className = `${orEmptyString(inputClassName)} ${validationClassName(validation)}`;

  React.useEffect(() => onBlur(createFakeReactSyntheticEvent({ name, value: props.value })), []);

  const defaultClassNames = { container: '', overlayWrapper: '', overlay: '' };
  const managedClassNames = defaults({}, classNames, defaultClassNames);
  managedClassNames.container = `DayPickerInput ${orEmptyString(managedClassNames.container)} ${className}`;

  return (
    <DayPicker
      {...rest}
      classNames={managedClassNames}
      onDayChange={(date: Date) => {
        const newValue = date && date.toISOString().slice(0, 10);
        props.onChange(createFakeReactSyntheticEvent({ name, value: newValue }));
      }}
    />
  );
}
