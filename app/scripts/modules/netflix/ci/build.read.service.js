'use strict';

let angular = require('angular');

import {API_SERVICE} from 'core/api/api.service';
import {CI_FILTER_MODEL} from './ci.filter.model';
import {ORCHESTRATED_ITEM_TRANSFORMER} from 'core/orchestratedItem/orchestratedItem.transformer';
import {SETTINGS} from 'core/config/settings';

module.exports = angular
  .module('spinnaker.netflix.ci.build.read.service', [
    API_SERVICE,
    CI_FILTER_MODEL,
    ORCHESTRATED_ITEM_TRANSFORMER,
  ])
  .factory('buildService', function (API, orchestratedItemTransformer, CiFilterModel) {

    const MAX_LINES = 4095;

    function transformBuild(build) {
      build.startTime = build.startedAt;
      build.endTime = build.completedAt;
      build.isRunning = build.completionStatus === 'INCOMPLETE';
      orchestratedItemTransformer.addRunningTime(build);
    }

    function getBuilds(repoType, projectKey, repoSlug) {
      return builds().get({repoType: repoType, projectKey: projectKey, repoSlug: repoSlug, filter: CiFilterModel.searchFilter}).then((response) => {
        response.data.forEach(transformBuild);
        return response.data;
      });
    }

    function getRunningBuilds(repoType, projectKey, repoSlug) {
      return builds().get({repoType: repoType, projectKey: projectKey, repoSlug: repoSlug, completionStatus: 'INCOMPLETE'}).then((response) => {
        return response.data;
      });
    }

    function getBuildDetails(buildId) {
      return builds().one(buildId).get().then((response) => {
        transformBuild(response);
        return response;
      });
    }

    function getBuildOutput(buildId, start = -1) {
      return builds().one(buildId).one('output').get({start: start, limit: MAX_LINES});
    }

    function getBuildConfig(buildId) {
      return builds().one(buildId).one('config').get();
    }

    function getBuildRawLogLink(buildId) {
      return [SETTINGS.gateUrl, 'ci', 'builds', buildId, 'rawOutput'].join('/');
    }

    function builds() {
      return API.all('ci').all('builds');
    }

    return {
      getBuilds,
      getRunningBuilds,
      getBuildDetails,
      getBuildOutput,
      getBuildConfig,
      getBuildRawLogLink,
      MAX_LINES
    };
  });
