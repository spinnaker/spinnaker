'use strict';

const angular = require('angular');
import _ from 'lodash';

import { ACCOUNT_SERVICE, NETWORK_READ_SERVICE, SECURITY_GROUP_READER, SUBNET_READ_SERVICE } from '@spinnaker/core';
import { OracleBMCSProviderSettings } from '../../oraclebmcs.settings';

module.exports = angular.module('spinnaker.oraclebmcs.serverGroup.configure.configuration.service', [
  ACCOUNT_SERVICE,
  NETWORK_READ_SERVICE,
  SUBNET_READ_SERVICE,
  SECURITY_GROUP_READER
])
  .factory('oraclebmcsServerGroupConfigurationService', function($q, networkReader, subnetReader, accountService, oraclebmcsImageReader, securityGroupReader) {

    let oracle = 'oraclebmcs';

    let getShapes = (image) => {
      if (!image || !image.compatibleShapes) {
        return [];
      }
      return image.compatibleShapes.map(shape => { return { name: shape }; });
    };

    function configureCommand(application, command) {
      let defaults = command || {};
      let defaultCredentials = defaults.account || application.defaultCredentials.oraclebmcs || OracleBMCSProviderSettings.defaults.account;
      let defaultRegion = defaults.region || application.defaultRegions.oraclebmcs || OracleBMCSProviderSettings.defaults.region;

      return $q.all({
        credentialsKeyedByAccount: accountService.getCredentialsKeyedByAccount(oracle),
        networks: networkReader.listNetworksByProvider(oracle),
        subnets: subnetReader.listSubnetsByProvider(oracle),
        securityGroups: securityGroupReader.getAllSecurityGroups(),
        images: loadImages(),
        availDomains: accountService.getAvailabilityZonesForAccountAndRegion(oracle, defaultCredentials, defaultRegion)
      }).then(function(backingData) {
        backingData.accounts = _.keys(backingData.credentialsKeyedByAccount);
        backingData.filtered = {};
        backingData.filtered.regions = [{name: 'us-phoenix-1'}];
        backingData.filtered.availabilityDomains = _.map(backingData.availDomains, function(zone) {
          return {name: zone};
        });

        backingData.filterSubnets = function() {
          if (command.vpcId && command.availabilityDomain) {
            return _.filter(backingData.subnets, {
              vcnId: command.vpcId,
              availabilityDomain: command.availabilityDomain
            });
          }
          return backingData.subnets;
        };

        backingData.availabilityDomainOnChange = function() {
          command.subnetId = null;
          backingData.seclists = null;
        };

        backingData.vpcOnChange = function() {
          command.subnetId = null;
          backingData.seclists = null;
        };

        backingData.subnetOnChange = function() {
          let subnet = _.find(backingData.subnets, {id : command.subnetId});
          let mySecGroups = backingData.securityGroups[command.account][oracle][command.region];
          let secLists = [];
          _.forEach(subnet.securityListIds, function(sid) {
            let sgRef = _.find(mySecGroups, {id: sid});
            securityGroupReader.getSecurityGroupDetails(
              command.application,
              command.account,
              oracle,
              command.region,
              command.vpcId,
              sgRef.name
            ).then(function(sgd) {
              secLists.push(sgd);
              backingData.seclists = secLists;
            });
          });
        };

        backingData.filtered.images = backingData.images;
        let shapesMap = {};
        _.forEach(backingData.filtered.images, (image) => {
          shapesMap[image.id] = getShapes(image);
        });
        backingData.filtered.shapes = shapesMap;
        backingData.filtered.allShapes = _.uniqBy(_.flatten(shapesMap), 'name');
        command.backingData = backingData;
      });
    }

    function loadImages() {
      return oraclebmcsImageReader.findImages({ provider: oracle });
    }

    return {
      configureCommand: configureCommand
    };
  });
