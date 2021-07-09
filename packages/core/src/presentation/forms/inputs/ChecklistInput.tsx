import React from 'react';

import { CheckboxInput } from './CheckboxInput';
import { IFormInputProps, OmitControlledInputPropsFrom } from './interface';
import { createFakeReactSyntheticEvent, isStringArray, orEmptyString, validationClassName } from './utils';

import './ChecklistInput.less';

const { useEffect } = React;

interface IChecklistInputProps extends IFormInputProps, OmitControlledInputPropsFrom<React.InputHTMLAttributes<any>> {
  options?: IChecklistInputOption[];
  stringOptions?: readonly string[];
  inline?: boolean;
  showSelectAll?: boolean;
}

export interface IChecklistInputOption {
  label: string;
  value: string;
}

export function ChecklistInput(props: IChecklistInputProps) {
  const { inline, showSelectAll, value, validation, inputClassName, options, stringOptions, onChange } = props;

  // Naively call the the field's onBlur handler
  // This is what Formik uses to mark the field as touched
  function touchField() {
    props.onBlur && props.onBlur(createFakeReactSyntheticEvent({ name: props.name, value }));
  }

  useEffect(touchField, []);

  const checkListOptions = isStringArray(stringOptions)
    ? stringOptions.map((s) => ({ label: s, value: s }))
    : options || [];

  const selectedValues: string[] = value || [];
  const updateSelectedValues = (selectedValues: string[]) => {
    onChange(createFakeReactSyntheticEvent({ value: selectedValues, name: props.name }));
  };

  const isChecked = (checkboxValue: any) => selectedValues.includes(checkboxValue);

  const toggleValue = (value: string) => {
    const newValues = isChecked(value) ? selectedValues.filter((x) => x !== value) : selectedValues.concat(value);
    onChange(createFakeReactSyntheticEvent({ value: newValues, name: props.name }));
  };

  const allSelected = checkListOptions.every((option) => isChecked(option.value));
  const selectNone = () => updateSelectedValues([]);
  const selectAll = () => updateSelectedValues(checkListOptions.map((o) => o.value));

  const checklistClassName = `ChecklistInput ${inline ? 'ChecklistInput_inline' : ''}`;
  const className = `${checklistClassName} }${orEmptyString(inputClassName)} ${validationClassName(validation)}`;

  return (
    <div className={className}>
      <ul className="checklist">
        {checkListOptions.map((option) => (
          <li key={option.label}>
            <CheckboxInput
              text={option.label}
              value={option.value}
              onChange={() => toggleValue(option.value)}
              checked={isChecked(option.value)}
            />
          </li>
        ))}

        {showSelectAll && checkListOptions.length > 1 && (
          <li key={'select_all_button'}>
            <a type="button" onClick={allSelected ? selectNone : selectAll}>
              {allSelected ? 'Deselect All' : 'Select All'}
            </a>
          </li>
        )}
      </ul>
    </div>
  );
}
