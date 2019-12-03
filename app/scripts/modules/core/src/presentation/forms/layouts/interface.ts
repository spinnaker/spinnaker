import React from 'react';

import { IFormInputValidation } from '../inputs';

/** These props are used by FieldLayout components, such as StandardFieldLayout */
export interface ILayoutProps {
  required?: boolean;
  label?: React.ReactNode;
  help?: React.ReactNode;
  actions?: React.ReactNode;
  input: React.ReactNode;
  validation?: IFormInputValidation;
}
