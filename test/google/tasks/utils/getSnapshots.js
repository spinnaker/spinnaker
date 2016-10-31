'use strict';

let request = require('request-promise'),
  { gateUrl, account } = require('../../config.json');

function buildRequestConfig (appName) {
  return {
    method: 'GET',
    json: true,
    uri: `${gateUrl}/applications/${appName}/snapshots/${account}`,
  };
}

function getLastSnapshot (appName) {
  return request(buildRequestConfig(appName));
}

function getAllSnapshots (appName) {
  let requestConfig = buildRequestConfig(appName);
  requestConfig.uri += '/history';
  return request(requestConfig);
}

module.exports = { getLastSnapshot, getAllSnapshots };
