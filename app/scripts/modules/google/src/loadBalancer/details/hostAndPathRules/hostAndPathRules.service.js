'use strict';

import { module } from 'angular';

export const GOOGLE_LOADBALANCER_DETAILS_HOSTANDPATHRULES_HOSTANDPATHRULES_SERVICE =
  'spinnaker.deck.gce.loadBalancer.hostAndPathRules.service';
export const name = GOOGLE_LOADBALANCER_DETAILS_HOSTANDPATHRULES_HOSTANDPATHRULES_SERVICE; // for backwards compatibility
module(GOOGLE_LOADBALANCER_DETAILS_HOSTANDPATHRULES_HOSTANDPATHRULES_SERVICE, []).factory(
  'hostAndPathRulesService',
  function () {
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
              return rows.concat(paths.map((path) => buildRow(hostPattern, path, backendService.name)));
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
  },
);
