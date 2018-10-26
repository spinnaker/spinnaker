export type ValidationFunctionFactory = (...args: any) => ValidationFunction;
export type ValidationFunction = (value: any) => string | Function | Promise<any>;

export class Validation {
  public static compose = (...validationFns: ValidationFunction[]): ValidationFunction => {
    return (value: any) => validationFns.reduce((error, validationFn) => error || validationFn(value), null);
  };

  public static isRequired: ValidationFunctionFactory = (message?: string) => {
    message = message || 'This field is required';
    return (val: any) => (val === undefined || val === null || val === '') && message;
  };

  public static minValue: ValidationFunctionFactory = (minValue: number, message?: string) => {
    message = message || `Cannot be less than ${minValue}`;
    return (val: number) => val < minValue && message;
  };

  public static maxValue: ValidationFunctionFactory = (maxValue: number, message?: string) => {
    message = message || `Cannot be greater than ${maxValue}`;
    return (val: number) => val > maxValue && message;
  };
}
