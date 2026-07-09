import _ from 'lodash';

function normalizeServerGroup(serverGroup) {
  return Promise.resolve(serverGroup);
}

function convertServerGroupCommandToDeployConfiguration(base) {
  // use _.defaults to avoid copying the backingData, which is huge and expensive to copy over
  const command = _.defaults({ backingData: [], viewState: [] }, base);
  if (base.viewState.mode !== 'clone') {
    delete command.source;
  }

  command.availabilityZones = {};
  command.availabilityZones[command.region] = ['default'];

  command.cloudProvider = 'dcos';
  command.credentials = command.account;

  delete command.viewState;
  delete command.viewModel;
  delete command.backingData;
  delete command.selectedProvider;

  return command;
}

export const dcosServerGroupTransformer = {
  convertServerGroupCommandToDeployConfiguration,
  normalizeServerGroup,
};
