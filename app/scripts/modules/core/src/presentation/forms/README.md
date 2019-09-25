# React Form Components

# FormikFormField

`FormikFormField` is a component used to render form fields in Spinnaker
using Formik for form state management and data validation.

![basic formik form](https://user-images.githubusercontent.com/2053478/46762922-3ff0c980-cc8d-11e8-81d6-a2baebc6de2e.gif)

This example sets up a Formik form and renders two `FormikFormField`s.
Validation errors on each field are surfaced when the field is blurred.
It has a submit button which is disabled when the form is invalid or being submitted.

```js
<Formik
  initialValues={{ name: '', email: '' }}
  onSubmit={(_values, formik) => setTimeout(() => formik.setSubmitting(false), 2000)}
  render={formik => {
    return (
      <Form>
        <FormikFormField
          name="name"
          label="Your Name"
          input={props => <TextInput {...props} />}
          validate={value => (!value ? 'Please enter your name' : undefined)}
        />

        <FormikFormField
          name="email"
          label="Your Email"
          input={props => <TextInput {...props} />}
          validate={value => {
            if (!value) return 'Please enter your email';
            if (!/[^@]+@[^@]+/.exec(value)) return 'Please enter a valid email';
          }}
        />

        <button disabled={!formik.isValid || formik.isSubmitting} className={`pull-right primary`}>
          {formik.isSubmitting ? 'Submitting...' : 'Submit Form'}
        </button>
      </Form>
    );
  }}
/>
```

The `FormikFormField` component should be a descendent of a `Formik` component.
It accepts "all the props", organizes them, and passes them down to the Input and Layout in the correct spots.

- `input`: a render prop used to render an Input component (see Input section below for details)
- `layout`: an (optional) render propu sed to render a Layout component (see Layout section for details. `StandardFieldLayout` is used by default)
- `validate`: an (optional) formik [field validation function](https://jaredpalmer.com/formik/docs/api/field#validate) which receives the value and should return an error message
- `name`: the path to the field's value in the formik `values`
- `label`, `help`, `required`, `actions` (see `IFieldLayoutPropsWithoutInput`)
- `touched`, `validationMessage`, `validationStatus` (see: `IValidationProps`)

In addition to the `validate` prop, the field can also be validated at the form level using the `Formik` component's `validate` prop.

### Form level validation Example:

This example shows validation of a formik field using the `Formik` component's `validate` prop.

```js
import { FormValidator } from 'core/presentation';

<Formik
  initialValues={{ email: '' }}
  validate={values => {
    const emailRegexp = /[^@]+@[^@]+/;
    const formValidator = new FormValidator(values);
    formValidator
      .field('email')
      .required()
      .withValidators(value => (emailRegexp.exec(value) ? null : 'Please enter a valid email'));
    return formValidator.result();
  }}
  render={formik => {
    return (
      <Form>
        <FormikFormField name="email" label="Your Email" input={props => <TextInput {...props} />} />
      </Form>
    );
  }}
/>;
```

# FormField

`FormField` is a [controlled component](https://reactjs.org/docs/forms.html#controlled-components)
used to render form fields in Spinnaker using externally managed form state and validation.

### TL;DR Example:

This example renders two `FormField`s and does field level validation.
This trivial example does not try to manage form-level validation state.
It has a submit button which is disabled when the form is being submitted.

```js
<form
  onSubmit={() => {
    this.setState({ isSubmitting: true });
    const stopSubmitting = () => this.setState({ isSubmitting: false });
    this.submit().then(stopSubmitting, stopSubmitting);
  }}
>
  <FormField
    name="name"
    label="your name"
    input={props => <TextInput {...props} />}
    value={this.state.name}
    onChange={evt => this.setState({ name: evt.target.value })}
    validate={val => !val && <span>Please enter your name</span>}
  />
  <FormField
    name="email"
    label="email address"
    input={props => <TextInput {...props} />}
    value={this.state.email}
    onChange={evt => this.setState({ email: evt.target.value })}
    validate={val => val && val.indexOf('@') === -1 && <span>Please enter a valid email</span>}
  />
  <button disabled={this.state.isSubmitting} className={`pull-right primary`}>
    {this.state.isSubmitting ? 'Submitting...' : 'Submit Form'}
  </button>
</form>
```

The `FormField` component accepts "all the props", organizes them,
and passes them down to the Input and Layout in the correct spots.

The props accepted by FormField are:

- `input`: the Input component to use
- `layout`: the Layout component to use. (optional, `StandardFieldLayout` is used by default)
- `validate`: an (optional) validation function which receives the value and should return an error message
- `label`, `help`, `required`, `actions` (see `IFieldLayoutPropsWithoutInput`)
- `name`, `value`, `onChange`, `onBlur`, (see `IControlledInputProps`)
- `touched`, `validationMessage`, `validationStatus` (see: `IValidationProps`)

### Custom Input Example:

This example uses an input which is defined inline:

```js
<FormField
  name="email"
  label="email address"
  input={({ field, validation }) => <input type="text" className={!!validation.error && 'error'} {...field} />}
  value={this.state.email}
  onChange={evt => this.setState({ email: evt.target.value })}
  validate={val => val && val.indexOf('@') === -1 && <span>Please enter a valid email</span>}
/>
```

### Layout Example:

This example uses a custom layout to render the error above the input

```js
<FormField
  name="email"
  label="email address"
  input={props => <TextInput {...props} />}
  layout={({ error, input }) => (
    <div>
      <div style={{ background: 'red' }}>{error}</div>
      <div>{input}</div>
    </div>
  )}
  value={this.state.email}
  onChange={evt => this.setState({ email: evt.target.value })}
  validate={val => val && val.indexOf('@') === -1 && <span>Please enter a valid email</span>}
/>
```

# Inputs

An Input is a react component which is rendered by a `FormField` or `FormikFormField` and:

- accepts user input
- emits change events when the input changes
- visually indicates an invalid validation state

An Input receives two important props (see `IFormInputProps`):

- `field`: an `IControlledInputProps` object which provides convenient props for rendering controlled form inputs.
  - `name`, `value`, `onChange`, `onBlur`
  - The contents of this prop is typically spread onto an input, e.g., `<input {...field} />`
- `validation`: Can be used to visually indicate the current validation state of the input
  - `validationMessage`: The validation message -- in general, render the message in a Layout, not here
  - `validationStatus`: The validation status -- visually indicate the validation status (See IValidationStatus)
  - `touched`: a flag which indicates if the the input has been "touched", which generally means "blurred"

### Example:

```
const SimpleTextInput = ({ field, validation }: IFormInputProps) =>
  <input type="text" className={validation.validationStatus} {...field} />
```

An Input can accept additional props, as needed.

### Example:

```
const SimpleTextInput = ({ field, validation, text }: IFormInputProps & { text: string }) =>
  <span> <input type="checkbox" {...field} /> {text} </span>
```

Commonly used inputs such as `type="text"` should be should be encapsulated in a reusable component, such as `TextInput`.

# Layouts

A Layout component accepts the various elements of full form field.
It defines how these components are visually rendered in relation to each other.
The Layout is also responsible for implementing responsive design, and can adjust element positions based on screen size.

The components that a Layout manages are:

- Input
- Label
- Help widget (i.e., popover)
- Required field indicator
- Action Icons (trash, etc)
- Validation message

### Example Layout:

```
+-----------------------------------------------------------------+
+ * Label Text [Help] | Text Input Field                | [Trash] |
+-----------------------------------------------------------------+
+ Validation message                                              |
+-----------------------------------------------------------------+
```

Both the `FormField` and `FormikFormField` accept a `layout` prop, and default to `StandardFieldLayout` if ommitted.
