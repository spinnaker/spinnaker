import codeCoverageTask from '@cypress/code-coverage/task';
import { defineConfig } from 'cypress';

module.exports = defineConfig({
  e2e: {
    baseUrl: 'http://localhost:5173',
    specPattern: 'cypress/integration/**/*.spec.{js,jsx,ts,tsx}',
    supportFile: 'cypress/support/index.js',
    scrollBehavior: 'center',
    experimentalStudio: true,
    setupNodeEvents(on, config) {
      codeCoverageTask(on, config);
      return config;
    },
  },
});
