import { ICluster } from '@spinnaker/core';

import { get } from 'lodash';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.kubernetes.serverGroup.artifactExtractor', [])
  .factory('kubernetesServerGroupArtifactExtractor', function() {
    function extractArtifacts(cluster: ICluster): string[] {
      const containers = (cluster.containers || []).concat(cluster.initContainers || []);
      return containers
        .filter(c => c.imageDescription && c.imageDescription.fromArtifact)
        .map(c => c.imageDescription.artifactId);
    }

    function removeArtifact(cluster: ICluster, reference: string): void {
      const artifactMatches = (container: any) =>
        container.imageDescription &&
        container.imageDescription.fromArtifact &&
        container.imageDescription.artifactId === reference;
      cluster.containers = get(cluster, 'containers', []).filter(c => !artifactMatches(c));
      cluster.initContainers = get(cluster, 'initContainers', []).filter(c => !artifactMatches(c));
    }

    return { extractArtifacts, removeArtifact };
  });
