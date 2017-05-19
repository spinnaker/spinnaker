import { HTMLAttributes } from 'react';

/** An interface which Formsy form field layouts should accept as props */
export interface IFormFieldLayoutProps extends HTMLAttributes<any> {
  Label?: React.ReactElement<any> | string;
  Input?: React.ReactElement<any>;
  Help?: React.ReactElement<any>;
  Error?: React.ReactElement<any>;
  showRequired?: boolean;
  showError?: boolean;
}
