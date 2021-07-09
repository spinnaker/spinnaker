import { FormikProps } from 'formik';
import { get } from 'lodash';
import { useEffect, useState } from 'react';

import { usePrevious } from '../../hooks';

/**
 * Sometimes a form allows the user to choose between multiple mutually exclusive sets of fields.
 * This hook saves and restores mutually exclusive fields when the user interactively toggles between them.
 *
 * For example, an order form may have separate fields for ordering a pizza or a sandwich. When the user selects
 * "pizza", this hook saves a copy of the user's currently entered "sandwich" data and then clears the "sandwich" fields.
 * If the user then switches back to "sandwich", the hook restores the "sandwich" data and saves/clears the "pizza" fields.
 *
 * Note: this hook does not explicitly attempt to manage default values for form fields
 *
 * @param formik
 * @param currentFieldSetKey the key for the current field (the form mode, e.g., the value of the selected radio button)
 * @param mutuallyExclusiveFieldSets an object with:
 *        - keys: one for each mutually exclusive fieldset
 *        - values: lodash paths to the form fields exclusively owned by that fieldset
 *
 * Example:
 *
 * // Restores previous "pizza" form data ('toppings', 'crust', 'sauce') when the form mode switches back to 'pizza'
 * // Restores previous "sandwich" form data ('bread', 'meat', 'cheese') when the form mode switches back to 'sandwich'
 *
 * useSaveRestoreMutuallyExclusiveFields(formik, pizzaOrSandwich, {
 *   { pizza: ["toppings", "crust", "sauce" ] },
 *   { sandwich: ["bread", "meat", "cheese" ] },
 * });
 *
 * <FormikFormField name="pizzaOrSandwich" input={props => <SelectInput {...props} options={['pizza', 'sandwich']}>} />
 *
 * { formik.values.pizzaOrSandwich === 'pizza' && (<>
 *   <FormikFormField name="toppings" input={props => <SelectInput {...props} options={toppingsOptions}>} />
 *   <FormikFormField name="crust" input={props => <SelectInput {...props} options={crustOptions}>} />
 *   <FormikFormField name="sauce" input={props => <SelectInput {...props} options={sauceOptions}>} />
 *   <FormikFormField name="cheese" input={props => <SelectInput {...props} options={pizzaCheeseOptions}>} />
 * </>)}
 *
 * { formik.values.pizzaOrSandwich === 'sandwich' && (<>
 *   <FormikFormField name="bread" input={props => <SelectInput {...props} options={breadOptions}>} />
 *   <FormikFormField name="meat" input={props => <SelectInput {...props} options={meatOptions}>} />
 *   <FormikFormField name="cheese" input={props => <SelectInput {...props} options={sandwichCheeseOptions}>} />
 * </>)}
 */
export function useSaveRestoreMutuallyExclusiveFields(
  formik: FormikProps<any>,
  currentFieldSetKey: string,
  mutuallyExclusiveFieldSets: { [fieldSetKey: string]: string[] },
) {
  interface SavedFieldsets {
    [fieldSetKey: string]: FieldsetData;
  }
  interface FieldsetData {
    [path: string]: any;
  }

  const previousFieldSetKey = usePrevious(currentFieldSetKey);
  const [savedValues, setSavedValues] = useState<SavedFieldsets>({});
  const [savedTouched, setSavedTouched] = useState<SavedFieldsets>({});

  // Whenever the fieldset key changes, save and clear out the
  // previous fieldset's values and restore the current fieldset's values.
  useEffect(() => {
    // Only run the effect if the the value is changing. Do not run on first render.
    if (!!previousFieldSetKey && currentFieldSetKey !== previousFieldSetKey) {
      const fieldsToSave = mutuallyExclusiveFieldSets[previousFieldSetKey] ?? [];
      const fieldsToRestore = mutuallyExclusiveFieldSets[currentFieldSetKey] ?? [];

      const valuesToSave = fieldsToSave.reduce((data, path) => {
        data[path] = get(formik.values, path);
        return data;
      }, {} as FieldsetData);
      setSavedValues({ ...savedValues, [previousFieldSetKey]: valuesToSave });

      const touchedToSave = fieldsToSave.reduce((data, path) => {
        data[path] = get(formik.touched, path);
        return data;
      }, {} as FieldsetData);
      setSavedTouched({ ...savedTouched, [previousFieldSetKey]: touchedToSave });

      fieldsToSave.forEach((field) => {
        formik.setFieldValue(field, undefined);
        formik.setFieldTouched(field, null);
      });

      // Restore the saved touched/values
      const valuesToRestore = savedValues[currentFieldSetKey] || {};
      const touchedToRestore = savedTouched[currentFieldSetKey] || {};
      fieldsToRestore.forEach((field) => {
        if (valuesToRestore.hasOwnProperty(field)) {
          formik.setFieldValue(field, savedValues[currentFieldSetKey][field]);
        }
        if (touchedToRestore.hasOwnProperty(field)) {
          formik.setFieldTouched(field, savedTouched[currentFieldSetKey][field]);
        }
      });
    }
  }, [currentFieldSetKey]);
}
