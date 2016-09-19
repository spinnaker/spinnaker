'use strict';

let { account, cloudProvider, region } = require('../../config.json');

module.exports = class {
  constructor (appName) {
    this.url = `/#/applications/${appName}/clusters`;
    this.createServerGroupButton = element(by.buttonText('Create Server Group'));
    this.serverGroupActionsButton = element(by.buttonText('Server Group Actions'));
    this.cloneServerGroupButton = element(by.linkText('Clone'));
  }

  getServerGroupDetailUrl (serverGroupName) {
    return `${this.url}/serverGroupDetails/${cloudProvider}/${account}/${region}/${serverGroupName}`;
  }
};
