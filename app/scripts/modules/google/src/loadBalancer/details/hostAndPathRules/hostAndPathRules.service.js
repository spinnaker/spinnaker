'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.deck.gce.loadBalancer.hostAndPathRules.service', [])
  .factory('hostAndPathRulesService', function() {
    function buildTable(hostRules, defaultService) {
      const defaultRow = buildRow('Any unmatched (default)', 'Any unmatched (default)', defaultService.name);
      if (hostRules.length === 0) {
        return [defaultRow];
      }

      return hostRules.reduce(
        (rows, hostRule) => {
          const [hostPattern] = hostRule.hostPatterns;
          const { defaultService, pathRules } = hostRule.pathMatcher;

          rows.push(buildRow(hostPattern, '/*', defaultService.name));

          return rows.concat(
            pathRules.reduce((rows, pathRule) => {
              const { backendService, paths } = pathRule;
              return rows.concat(paths.map(path => buildRow(hostPattern, path, backendService.name)));
            }, []),
          );
        },
        [defaultRow],
      );
    }

    function buildRow(hostPattern, path, backend) {
      return { hostPattern, path, backend };
    }

    return { buildTable };
  });
