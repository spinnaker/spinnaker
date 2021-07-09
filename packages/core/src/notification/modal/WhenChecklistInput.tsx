import React from 'react';

import {
  createFakeReactSyntheticEvent,
  IFormInputProps,
  OmitControlledInputPropsFrom,
  orEmptyString,
  validationClassName,
} from '../../presentation';

const { useEffect } = React;

interface IWhenChecklistInputProps
  extends IFormInputProps,
    OmitControlledInputPropsFrom<React.InputHTMLAttributes<any>> {
  options?: IWhenChecklistInputOption[];
}

interface IWhenChecklistInputOption {
  label: string;
  value: string;
  additionalFields?: React.ReactNode;
}

interface ICheckBoxProps {
  option: IWhenChecklistInputOption;
  selected: string[];
  onChange: (event: React.ChangeEvent<string[]>) => any;
  inputClassName: string;
  inputProps: any;
}

function CheckBox({ option, selected, onChange, inputClassName, inputProps }: ICheckBoxProps) {
  function handleChange({ target }: React.ChangeEvent<HTMLInputElement>) {
    const newValue = !selected.includes(target.value)
      ? selected.concat(target.value)
      : selected.filter((v: string) => v !== target.value);
    onChange(createFakeReactSyntheticEvent({ value: newValue, name: target.name }));
  }

  return (
    <>
      <label className="clickable" key={option.value}>
        <input
          className={inputClassName}
          type="checkbox"
          value={option.value}
          onChange={handleChange}
          checked={selected.includes(option.value)}
          {...inputProps}
        />
        {option.label}
      </label>
      {selected.includes(option.value) && option.additionalFields}
    </>
  );
}

export function WhenChecklistInput(props: IWhenChecklistInputProps) {
  const { value, validation, inputClassName, options, onChange, ...inputProps } = props;

  // Naively call the the field's onBlur handler
  // This is what Formik uses to mark the field as touched
  function touchField() {
    props.onBlur && props.onBlur(createFakeReactSyntheticEvent({ name: props.name, value }));
  }
  useEffect(touchField, []);

  const selectedValues: string[] = value || [];

  return (
    <div className="checkbox">
      <ul className="checklist">
        {options.map((option: IWhenChecklistInputOption) => (
          <li key={option.label}>
            <CheckBox
              option={option}
              selected={selectedValues}
              inputClassName={`${orEmptyString(inputClassName)} ${validationClassName(validation)}`}
              inputProps={inputProps}
              onChange={onChange}
            />
          </li>
        ))}
      </ul>
    </div>
  );
}
