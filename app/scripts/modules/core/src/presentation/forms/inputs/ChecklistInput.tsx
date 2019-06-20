import * as React from 'react';
import { IFormInputProps, OmitControlledInputPropsFrom } from '../interface';

import { createFakeReactSyntheticEvent, isStringArray, orEmptyString, validationClassName } from './utils';

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
  const {
    inline,
    showSelectAll,
    value,
    validation,
    inputClassName,
    options,
    stringOptions,
    onChange,
    ...otherProps
  } = props;

  // Naively call the the field's onBlur handler
  // This is what Formik uses to mark the field as touched
  function touchField() {
    props.onBlur && props.onBlur(createFakeReactSyntheticEvent({ name: props.name, value }));
  }
  useEffect(touchField, []);

  const className = `${orEmptyString(inputClassName)} ${validationClassName(validation)}`;

  const selectedValues = value || [];
  const isChecked = (checkboxValue: any) => selectedValues.includes(checkboxValue);

  const checkListOptions = isStringArray(stringOptions)
    ? stringOptions.map(s => ({ label: s, value: s }))
    : options || [];

  const labelClassName = !!inline ? 'clickable checkbox-inline' : 'clickable';

  function CheckBox(checkboxProps: { option: IChecklistInputOption }) {
    const { option } = checkboxProps;

    function handleChange(e: React.ChangeEvent<HTMLInputElement>) {
      const selected = e.target.value;
      const alreadyHasValue = isChecked(selected);
      const newValue = !alreadyHasValue ? selectedValues.concat(selected) : value.filter((v: string) => v !== selected);
      onChange(createFakeReactSyntheticEvent({ value: newValue, name: e.target.name }));
    }

    return (
      <label className={labelClassName} key={option.value}>
        <input
          className={className}
          type="checkbox"
          value={option.value}
          onChange={handleChange}
          checked={isChecked(option.value)}
          {...otherProps}
        />
        {option.label}
      </label>
    );
  }

  function SelectAllButton() {
    const allSelected = checkListOptions.every(option => isChecked(option.value));
    const anchorClassName = `btn btn-default btn-xs ${inline ? '' : 'push-left'}`;
    const style = inline ? { margin: '8px 0 0 10px' } : {};

    const selectValue = (selected: string[]) => {
      return onChange(createFakeReactSyntheticEvent({ name: props.name, value: selected }));
    };
    const selectNone = () => selectValue([]);
    const selectAll = () => selectValue(checkListOptions.map(o => o.value));

    return (
      <a className={anchorClassName} style={style} type="button" onClick={allSelected ? selectNone : selectAll}>
        {allSelected ? 'Deselect All' : 'Select All'}
      </a>
    );
  }

  function InlineOptions() {
    return (
      <>
        {checkListOptions.map(option => (
          <CheckBox key={option.label} option={option} />
        ))}

        {showSelectAll && checkListOptions.length > 1 && <SelectAllButton />}
      </>
    );
  }

  function VerticalOptions() {
    return (
      <ul className="checklist">
        {checkListOptions.map(option => (
          <li key={option.label}>
            <CheckBox option={option} />
          </li>
        ))}

        {showSelectAll && checkListOptions.length > 1 && (
          <li key={'select_all_button'}>
            <SelectAllButton />
          </li>
        )}
      </ul>
    );
  }

  return <div className="checkbox">{inline ? <InlineOptions /> : <VerticalOptions />}</div>;
}
