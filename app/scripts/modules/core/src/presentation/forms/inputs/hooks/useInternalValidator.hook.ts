import React from 'react';

import { useIsMountedRef } from '../../../hooks/useIsMountedRef.hook';
import { IFormInputValidation } from '../interface';
import { IValidator } from '../../validation';

export function useInternalValidator(
  validation: IFormInputValidation,
  validator: IValidator,
  revalidateDeps: any[] = [],
) {
  // Use a ref so the memoized internalValidator can always access the latest data
  const isMounted = useIsMountedRef().current;
  const validatorRef = React.useRef(validator);
  validatorRef.current = validator;
  const internalValidator: IValidator = React.useCallback((value, label) => validatorRef.current(value, label), []);

  // add the internal validator on mount and remove on unmount
  React.useEffect(() => {
    validation && validation.addValidator && validation.addValidator(internalValidator);
    return () => validation && validation.removeValidator && validation.removeValidator(internalValidator);
  }, []);

  React.useEffect(() => {
    isMounted && validation && validation.revalidate && validation.revalidate();
  }, revalidateDeps);
}
