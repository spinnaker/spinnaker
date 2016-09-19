'use strict';

function deleteAppJob ({ account, appName }) {
  return {
    type: 'deleteApplication',
    account: account,
    application: { name: appName },
    user: '[anonymous]'
  }
}

module.exports = deleteAppJob;
