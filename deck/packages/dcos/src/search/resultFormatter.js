export const dcosSearchResultFormatter = {
  instances(entry) {
    return Promise.resolve((entry.name || entry.instanceId) + ' (' + entry.namespace + ')');
  },
  serverGroups(entry) {
    return Promise.resolve((entry.name || entry.serverGroup) + ' (' + (entry.namespace || entry.region) + ')');
  },
  loadBalancers(entry) {
    return Promise.resolve(entry.name + ' (' + (entry.namespace || entry.region) + ')');
  },
};
