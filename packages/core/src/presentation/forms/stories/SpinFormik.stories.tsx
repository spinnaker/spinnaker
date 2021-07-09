import { Meta } from '@storybook/react';
import { isEmpty } from 'lodash';
import React from 'react';

import {
  ChecklistInput,
  DayPickerInput,
  FormikFormField,
  NumberInput,
  RadioButtonInput,
  ReactSelectInput,
  SpinFormik,
  TextAreaInput,
  TextInput,
} from '../';
import { URLComponentInput } from './SpinFormikStoriesHelper';

export default {
  component: SpinFormik,
  title: 'Forms/SpinFormik',
  decorators: [
    (Story) => (
      <div style={{ background: '#fff', border: '1px solid #eee', borderRadius: 10, padding: 20, width: 500 }}>
        <Story />
      </div>
    ),
  ],
  parameters: {
    layout: 'centered',
  },
} as Meta;

export const SimpleForm = () => (
  <SpinFormik
    initialValues={{
      data: {
        account: 'prod',
        applicationName: 'spinnaker',
        comment: '',
        enableAutoScaling: 'enabled',
        instanceCount: 1,
        launchDate: new Date(Date.now()).toISOString().slice(0, 10),
        regions: ['us-east'],
      },
    }}
    render={(formik) => (
      <form style={{}} onSubmit={formik.handleSubmit}>
        <FormikFormField
          name="data.applicationName"
          label="Application Name"
          input={(props) => <TextInput {...props} />}
        />
        <FormikFormField
          name="data.account"
          label="Account"
          input={(props) => <ReactSelectInput {...props} clearable={false} stringOptions={['prod', 'test']} />}
        />
        <FormikFormField
          name="data.regions"
          label="Regions"
          input={(props) => (
            <ChecklistInput
              {...props}
              inline={true}
              options={[
                { label: 'us-east', value: 'us-east' },
                { label: 'us-west', value: 'us-west' },
                { label: 'eu-west', value: 'eu-west' },
              ]}
            />
          )}
        />
        <FormikFormField
          name="data.enableAutoScaling"
          label="Auto Scaling"
          input={(props) => (
            <RadioButtonInput
              {...props}
              inline={true}
              options={[
                { label: 'Enable', value: 'enabled' },
                { label: 'Disable', value: 'disabled' },
              ]}
            />
          )}
        />
        <FormikFormField
          name="data.launchDate"
          label="Launch Date"
          input={(props) => <DayPickerInput {...props} format="yyyy-mm-dd" />}
        />
        <FormikFormField
          name="data.instanceCount"
          label="Instances"
          input={(props) => <NumberInput {...props} min={1} max={10} />}
        />
        <FormikFormField name="data.comment" label="Comment" input={(props) => <TextAreaInput {...props} />} />
        <div style={{ marginTop: 12, textAlign: 'right' }}>
          <button className="btn btn-primary" type="submit">
            Submit
          </button>
        </div>
      </form>
    )}
    onSubmit={(data) => /*eslint-disable no-console*/ console.log(JSON.stringify(data, null, 2))}
  />
);

export const Validation = () => (
  <SpinFormik
    initialValues={{
      firstName: 'foo',
      lastName: 'bar',
      email: '',
    }}
    render={(formikProps) => (
      <form onSubmit={formikProps.handleSubmit}>
        <FormikFormField
          label="First Name"
          name="firstName"
          input={(props) => <TextInput {...props} />}
          required={true}
        />
        <FormikFormField
          label="Last Name"
          name="lastName"
          input={(props) => <TextInput {...props} />}
          required={true}
        />
        <FormikFormField
          label="Email"
          name="email"
          input={(props) => <TextInput {...props} />}
          required={true}
          validate={(value) =>
            /^[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,4}$/i.test(value) ? null : 'Invalid email address'
          }
        />
        <div style={{ marginTop: 12, textAlign: 'right' }}>
          <button className="btn btn-primary" type="submit" disabled={!isEmpty(formikProps.errors)}>
            Submit
          </button>
        </div>
      </form>
    )}
    onSubmit={(data) => /*eslint-disable no-console*/ console.log(JSON.stringify(data, null, 2))}
  />
);

export const CustomInputField = () => (
  <SpinFormik
    initialValues={{
      url: 'https://www.google.com',
    }}
    render={(formikProps) => (
      <form onSubmit={formikProps.handleSubmit}>
        <FormikFormField label="URL" name="url" input={(props) => <URLComponentInput {...props} />} />
        <div style={{ marginTop: 12, textAlign: 'right' }}>
          <button className="btn btn-primary" type="submit" disabled={!isEmpty(formikProps.errors)}>
            Submit
          </button>
        </div>
      </form>
    )}
    onSubmit={(data) => /*eslint-disable no-console*/ console.log(JSON.stringify(data, null, 2))}
  />
);
