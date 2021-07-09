import * as React from 'react';

import { createFakeReactSyntheticEvent, IFormInputProps } from '../inputs';

/**
 * This hook can be used to create a FormInput wrapper that maps/transforms the data as needed by the caller's model.
 *
 * Example map a checkbox value to "ENABLED"/"DISABLED":
 * ```tsx
 * function EnabledDisabledCheckbox(props: ICheckboxInputProps) {
 *   const mappedProps = useFormInputValueMapper(
 *     props,
 *     (val) => val === 'ENABLED', // model -> input
 *     (val, event) => event.target.checked ? 'ENABLED' : 'DISABLED', // input -> model
 *   )
 *   return <CheckboxInput {...mappedProps} />;
 * }
 ```
 *
 * @param props the incoming props
 * @param toInputValue a function that converts from the caller's model to the value the FormInput expects
 * @param fromInputValue a function that converts from the value the FormInput returns (via the onChange callback) to the caller's model
 */
export function useFormInputValueMapper<INT, EXT>(
  props: IFormInputProps,
  toInputValue: (externalValue: EXT) => INT,
  fromInputValue: (inputValue: INT, e: React.ChangeEvent<any>) => EXT,
) {
  const value = toInputValue(props.value);
  const onChange = React.useCallback(
    (e: React.ChangeEvent<any>) => {
      props.onChange(
        createFakeReactSyntheticEvent({
          name: e.target.name,
          value: fromInputValue(e.target.value, e),
        }),
      );
    },
    [props.onChange, fromInputValue],
  );

  return { ...props, value, onChange };
}
