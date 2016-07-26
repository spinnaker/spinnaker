'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.deck.gce.loadBalancer.hostAndPathRules.service', [])
  .factory('hostAndPathRulesService', function() {

    function buildTable(hostRules, defaultService) {
      let defaultRow = buildRow('Any unmatched (default)', 'Any unmatched (default)', defaultService);

      return hostRules.reduce((rows, hostRule) => {
        let [ hostPattern ] = hostRule.hostPatterns;
        let { defaultService, pathRules } = hostRule.pathMatcher;

        rows.push(buildRow(hostPattern, '/*', defaultService));

        return rows.concat(pathRules.reduce((rows, pathRule) => {
          let { backendService, paths } = pathRule;
          return rows.concat(paths.map(path => buildRow(hostPattern, path, backendService)));
        }, []));
      }, [ defaultRow ]);
    }

    function buildRow(hostPattern, path, backend) {
        return { hostPattern, path, backend };
    }

    return { buildTable };
  });
