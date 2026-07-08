const apiPrefix = '#';

function buildLink(host, accountName, region, name, taskName = null) {
  const regionParts = region != null ? region.replace('_', '/').split('/') : [];
  let link = host + '/' + apiPrefix + '/services/overview/';

  if (regionParts.length > 1) {
    link =
      link +
      encodeURIComponent('/' + accountName + '/' + regionParts.slice(1, regionParts.length).join('/') + '/') +
      name;
  } else {
    link = link + encodeURIComponent('/' + accountName + '/') + name;
  }

  if (taskName) {
    link = link + '/tasks/' + taskName;
  }

  return link;
}

function buildLoadBalancerLink(host, accountName, name) {
  return host + '/' + apiPrefix + '/services/overview/' + encodeURIComponent('/' + accountName + '/') + name;
}

export const dcosProxyUiService = {
  buildLink,
  buildLoadBalancerLink,
};
