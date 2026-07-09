function params(serverGroup) {
  return {
    dcosCluster: serverGroup.dcosCluster,
    group: serverGroup.group,
    interestingHealthProviderNames: ['DcosService'],
  };
}

export const dcosServerGroupParamsMixin = {
  destroyServerGroup: params,
  enableServerGroup: params,
  disableServerGroup: params,
};
