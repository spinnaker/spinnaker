import rule from './react2angular-with-error-boundary';
import ruleTester from '../utils/ruleTester';
const errorMessage = `Wrap react2angular components in an error boundary using 'withErrorBoundary()'`;

ruleTester.run('api-no-slashes', rule, {
  valid: [
    {
      code: `react2angular(withErrorBoundary(MyComponent), ['foo', 'bar']);`,
    },
  ],

  invalid: [
    {
      code: `react2angular(MyComponent, ['foo', 'bar']);`,
      errors: [errorMessage],
      output: `import { withErrorBoundary } from '@spinnaker/core';\nreact2angular(withErrorBoundary(MyComponent, 'react2angular component'), ['foo', 'bar']);`,
    },

    {
      errors: [errorMessage],
      code: `import { SpinnakerContainer } from '@spinnaker/core';
module(SPINNAKER_CONTAINER_COMPONENT, []).component('spinnakerContainer',
react2angular(SpinnakerContainer, ['authenticating', 'routing']));`,
      output: `import { SpinnakerContainer, withErrorBoundary } from '@spinnaker/core';
module(SPINNAKER_CONTAINER_COMPONENT, []).component('spinnakerContainer',
react2angular(withErrorBoundary(SpinnakerContainer, 'spinnakerContainer'), ['authenticating', 'routing']));`,
    },
  ],
});
