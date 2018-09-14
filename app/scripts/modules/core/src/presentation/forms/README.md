# React Form Components

## Inputs

An input is a controlled component that receives a `value` and fires `onChange` events.
The most common input is the native `<input/>` component, but these may also be custom React components.

### Examples:

* Text Input `<input type="text" />`
* Text Area `<textarea/>`
* Integer Input `<input type="number"/>`
* Select/Dropdown `<select><option/></select>`
* Radio button group
* Checkbox `<input type="checkbox"/>`
* Typeahead -- custom component
* Expressions -- custom component
* Key/Value pairs -- custom component
  * i.e., “Tags” in Advanced Deploy Settings

## Layouts

A layout component is composed of an Input and other field components.
The layout defines how these components are rendered in relation to each other.
The layout is also typically responsible for implementing any responsive behaviors.

The components that a Layout manages are:

* Input Field
* Label
* Help widget (i.e., popover)
* Required indicator
* Action Icons (trash, etc)
* Validation
  * Internal (built into component, I.e., date picker, expressions)
  * External

### Example Layout:

```
+-----------------------------------------------------------------+
+ * Label Text [Help] | Text Input Field                | [Trash] |
+-----------------------------------------------------------------+
+ Validation error message                                        |
+-----------------------------------------------------------------+
```

## Fields

A Field component combines an Input with a Layout.
The most commonly used combinations of Input + Layout can be pre-defined and re-used as a Field.
For example, a `<TextField/>` component combines a text Input `<input type="text/>` with the default Layout `<BasicLayout/>`.

The `<TextField/>` can then be re-used as the basic building block in a specific form component.

### Example

```js
<TextField
  value={this.state.timelineName}
  onChange={timelineName => this.setState({ timelineName })}
  label="Timeline Name"
/>
```

<img width="892" alt="screen shot 2018-06-09 at 3 44 03 pm" src="https://user-images.githubusercontent.com/2053478/41196693-1d579b48-6bfc-11e8-8ff4-059085fb9151.png">

### Example (providing custom Layout component)

```js
<TextField
  value={this.state.timelineName}
  onChange={timelineName => this.setState({ timelineName })}
  label="Timeline Name"
  FieldLayout={MyFancyFieldLayoutComponent}
/>
```

### Example with an error message:

```js
<TextField
  value={this.state.timelineName}
  onChange={timelineName => this.setState({ timelineName })}
  label="Timeline Name"
  error={this.state.error && <div>Error: {this.state.error}</div>}
/>
```

<img width="814" alt="screen shot 2018-06-09 at 3 44 19 pm" src="https://user-images.githubusercontent.com/2053478/41196697-34723ad6-6bfc-11e8-90da-8b46973c2461.png">

### Example using Formik

Field components wrap a controlled input component.
However, a Formik wrapper is added to Field components, as a static `Formik` property.

```js
<Formik
  initialValues={initialValues}
  validate={this.validate}
  render={(formik: FormikProps<IChronosTimelineConfig>) => (
    <TextField.Formik
      formik={formik}
      name="timelineName"
      label="Timeline Name"
    />
/>
```

### Example Complex Field (ExpressionRegexField)

```jsx
<ExpressionRegexField.Formik
  formik={formik}
  name="rowLabel"
  regex={config.rowLabelRegex || ''}
  replace={config.rowLabelReplacement || ''}
  onRegexChanged={val => setFieldValue('rowLabelRegex', val)}
  onReplaceChanged={val => setFieldValue('rowLabelReplacement', val)}
  label="Row Label"
  placeholder="Row Label (Optional, Expressions OK)"
  help={RowLabelHelp}
  RegexHelp={RegexHelp}
  context={sampleRowContext}
  markdown={true}
/>
```

<img width="843" alt="screen shot 2018-06-09 at 3 48 28 pm" src="https://user-images.githubusercontent.com/2053478/41196720-9b12afe6-6bfc-11e8-9e63-f4ef86b05569.png">
