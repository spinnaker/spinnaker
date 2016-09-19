'use strict';

function createAppJob ({ account, cloudProvider, appName, email }) {
  return {
    type: 'createApplication',
    account: account,
    application: {
      cloudProviders: cloudProvider,
      instancePort: 80,
      name: appName,
      email: email,
      platformHealthOnly: true,
    },
    user: '[anonymous]'
  };
}

module.exports = createAppJob;
