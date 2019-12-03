import React from 'react';

import {
  createFakeReactSyntheticEvent,
  IFormInputProps,
  OmitControlledInputPropsFrom,
  orEmptyString,
  validationClassName,
} from 'core/presentation';

const { useEffect, useState } = React;

interface IWhenChecklistInputProps
  extends IFormInputProps,
    OmitControlledInputPropsFrom<React.InputHTMLAttributes<any>> {
  options?: IWehnChecklistInputOption[];
}

export interface IWehnChecklistInputOption {
  label: string;
  value: string;
  additionalFields?: React.ReactNode;
}

export function WhenChecklistInput(props: IWhenChecklistInputProps) {
  const { value, validation, inputClassName, options, onChange, ...otherProps } = props;

  // Naively call the the field's onBlur handler
  // This is what Formik uses to mark the field as touched
  function touchField() {
    props.onBlur && props.onBlur(createFakeReactSyntheticEvent({ name: props.name, value }));
  }
  useEffect(touchField, []);

  const className = `${orEmptyString(inputClassName)} ${validationClassName(validation)}`;

  const selectedValues = value || [];
  const isChecked = (checkboxValue: any) => selectedValues.includes(checkboxValue);

  function CheckBox(checkboxProps: { option: IWehnChecklistInputOption }) {
    const [checked, setChecked] = useState(isChecked(checkboxProps.option.value));
    const { option } = checkboxProps;

    function handleChange(e: React.ChangeEvent<HTMLInputElement>) {
      const selected = e.target.value;
      const alreadyHasValue = isChecked(selected);
      const newValue = !alreadyHasValue ? selectedValues.concat(selected) : value.filter((v: string) => v !== selected);
      onChange(createFakeReactSyntheticEvent({ value: newValue, name: e.target.name }));
      setChecked(isChecked(selected));
    }

    return (
      <>
        <label className="clickable" key={option.value}>
          <input
            className={className}
            type="checkbox"
            value={option.value}
            onChange={handleChange}
            checked={checked}
            {...otherProps}
          />
          {option.label}
        </label>
        {checked && option.additionalFields}
      </>
    );
  }

  return (
    <div className="checkbox">
      <ul className="checklist">
        {options.map((option: IWehnChecklistInputOption) => (
          <li key={option.label}>
            <CheckBox option={option} />
          </li>
        ))}
      </ul>
    </div>
  );
}
