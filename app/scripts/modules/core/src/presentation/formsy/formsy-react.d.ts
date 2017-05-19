// https://github.com/christianalfoni/formsy-react/issues/191#issuecomment-144872142

// FormComponent.ts
// Type wrapper for Formsy-React
// Project: https://github.com/christianalfoni/formsy-react

declare module 'formsy-react' {

  interface ValidationErrors {
    [key: string]: string;
  }

  // This is declared for a reference to Formsy.Mixin in FormComponent.ts
  const Mixin: any;

  interface IFormProps {
    [key: string]: string | Function | boolean | ValidationErrors | React.ReactElement<any>[];
    className?: string;
    mapping?: Function;
    onSuccess?: Function;
    onError?: Function;
    onSubmit?: Function;
    onValidSubmit?: Function;
    onInvalidSubmit?: Function;
    onSubmitted?: Function;
    onValid?: Function;
    onInvalid?: Function;
    onChange?: Function;
    validationErrors?: ValidationErrors;
    preventExternalValidation?: boolean;
  }

  interface obj {
    [key: string]: any;
  }

  class Form extends React.Component<IFormProps, any> {
    public displayName: 'Formsy';
    /** Allow resetting to specified data */
    public reset(data: any): void;
    /** Update model, submit to url prop and send the model */
    public submit(event?: any): void;
    public mapModel(model: any): any;
    public getModel(): any;
    /** Reset each key in the model to the original / initial / specified value */
    public resetModel(data): void;
    public setInputValidationErrors(errors: obj): void;
    /** Checks if the values have changed from their initial value */
    public isChanged(): boolean;
    public getPristineValues(): obj;
    /**
     * Go through errors from server and grab the components
     * stored in the inputs map. Change their state to invalid
     * and set the serverError message
     */
    public updateInputsWithError(errors: obj): void;
    public isFormDisabled(): boolean;
    public getCurrentValues(): { [key: string]: any };
    public setFormPristine(isPristine: boolean): void;
    /**
     * Use the binded values and the actual input value to
     * validate the input and set its state. Then check the
     * state of the form itself
     */
    public validate(isPristine: boolean): void;

    /** Checks validation on current value or a passed value **/
    public runValidation(component: any, value?: any): {
      isRequired: boolean;
      isValid: boolean;
      error: any[];
    };
    public runRules(value: any, currentValues: any, validations: obj): void;
    public validateForm(): void;

    /** Method put on each input component to register itself to the form */
    attachToForm(component: any): void;
    /** Method put on each input component to unregister itself from the form */
    detachFromForm(component: any): void;
  }

  interface IFormsyContext {
    attachToForm: typeof Form.attachToForm;
    detachFromForm: typeof Form.detachFromForm;
    validate: typeof Form.validate;
    isFormDisabled: typeof Form.isFormDisabled;
    isValidValue(component: any, value?: any): boolean;
  }

  const Decorator: () => (target) => any;
}
