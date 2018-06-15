'use strict';

const angular = require('angular');

import _ from 'lodash';

import {
  AccountService,
  AuthenticationService,
  BakeryReader,
  NetworkReader,
  Registry,
  SubnetReader,
} from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.oracle.pipeline.stage.bakeStage', [require('./bakeExecutionDetails.controller.js').name])
  .config(function() {
    Registry.pipeline.registerStage({
      provides: 'bake',
      cloudProvider: 'oracle',
      label: 'Bake',
      description: 'Bakes an image in the specified region',
      templateUrl: require('./bakeStage.html'),
      executionDetailsUrl: require('./bakeExecutionDetails.html'),
      executionLabelTemplateUrl: require('core/pipeline/config/stages/bake/BakeExecutionLabel'),
      defaultTimeoutMs: 60 * 60 * 1000, // 60 minutes
      validators: [
        { type: 'requiredField', fieldName: 'baseOs' },
        { type: 'requiredField', fieldName: 'package' },
        { type: 'requiredField', fieldName: 'upgrade' },
        { type: 'requiredField', fieldName: 'region' },
        { type: 'requiredField', fieldName: 'account' },
        { type: 'requiredField', fieldName: 'user' },
        { type: 'requiredField', fieldName: 'cloudProviderType' },
        {
          type: 'requiredField',
          fieldName: 'extended_attributes.subnet_ocid',
          message: '<b>Subnet ocid</b> is a required field for Bake stages.',
        },
        {
          type: 'requiredField',
          fieldName: 'extended_attributes.availability_domain',
          message: '<b>Availability Domain</b> is a required field for Bake stages.',
        },
      ],
      restartable: true,
    });
  })
  .controller('oracleBakeStageCtrl', function($scope, $q) {
    const provider = 'oracle';

    if (!$scope.stage.cloudProvider) {
      $scope.stage.cloudProvider = provider;
    }

    if (!$scope.stage) {
      $scope.stage = {};
    }

    if (!$scope.stage.extended_attributes) {
      $scope.stage.extended_attributes = {};
    }

    if (!$scope.stage.user) {
      $scope.stage.user = AuthenticationService.getAuthenticatedUser().name;
    }

    function initialize() {
      $scope.viewState.providerSelected = true;

      $q
        .all({
          baseOsOptions: BakeryReader.getBaseOsOptions(provider),
          regions: BakeryReader.getRegions(provider),
          availabilityDomains: $scope.getZones(provider),
          networks: $scope.getNetworks(provider),
          subnets: $scope.getSubNetworks(provider),
        })
        .then(results => {
          if (!$scope.account && $scope.application.defaultCredentials.oracle) {
            $scope.account = $scope.application.defaultCredentials.oracle;
          }
          if (results.baseOsOptions.baseImages.length > 0) {
            $scope.baseOsOptions = results.baseOsOptions;
          }
          if (results.regions.length > 0) {
            $scope.regionOptions = results.regions;
          }
          if (results.availabilityDomains && Object.keys(results.availabilityDomains).length > 0) {
            $scope.zoneOptions = results.availabilityDomains;
          }
          if (results.networks.length > 0) {
            $scope.networkOptions = results.networks;
          }
          if (results.subnets.length > 0) {
            $scope.subnetOptions = results.subnets;
          }
          if (!$scope.stage.user) {
            $scope.stage.user = AuthenticationService.getAuthenticatedUser().name;
          }
          if (!$scope.stage.account) {
            $scope.stage.account = $scope.account;
            $scope.stage.extended_attributes.access_cfg_file_account = $scope.account;
          }
          if (!$scope.stage.baseOs) {
            $scope.stage.baseOs = $scope.baseOsOptions.baseImages[0].id;
          }
          if (!$scope.stage.region) {
            $scope.stage.region = $scope.regionOptions[0];
          }
          if (!$scope.stage.upgrade) {
            $scope.stage.upgrade = true;
          }

          $scope.constrainNetworkOptions();
          $scope.constrainAvailabilityDomainOptions();
          $scope.constrainSubnetOptions();

          $scope.viewState.loading = false;
        });
    }

    this.getBaseOsDescription = function(baseOsOption) {
      return baseOsOption.id + (baseOsOption.shortDescription ? ' (' + baseOsOption.shortDescription + ')' : '');
    };

    $scope.getZones = function(provider) {
      return AccountService.getPreferredZonesByAccount(provider);
    };

    $scope.getNetworks = function(provider) {
      return NetworkReader.listNetworksByProvider(provider).then(networks => networks.sort());
    };

    $scope.getSubNetworks = function(provider) {
      return SubnetReader.listSubnetsByProvider(provider).then(subnets => subnets.sort());
    };

    /**
     * Calculate the constrained set of networkOptions w.r.t the selected region and attempt to select a default.
     */
    $scope.constrainNetworkOptions = function() {
      if ($scope.networkOptions && $scope.account && $scope.stage.region) {
        $scope.constrainedNetworkOptions = _.filter($scope.networkOptions, networkOption => {
          return networkOption.region === $scope.stage.region;
        });
      }
      // if an applicable default is available set it; else clear.
      if ($scope.constrainedNetworkOptions && $scope.constrainedNetworkOptions.length > 0) {
        $scope.stage.extended_attributes.network_ocid = $scope.constrainedNetworkOptions[0].id;
      } else {
        delete $scope.stage.extended_attributes.network_ocid;
      }
      return $scope.constrainedNetworkOptions;
    };

    /**
     * Calculate the constrained set of availabilityDomainOptions w.r.t the selected network and attempt to select a default.
     */
    $scope.constrainAvailabilityDomainOptions = function() {
      let selectedNetwork_ocid = $scope.stage.extended_attributes.network_ocid;
      if ($scope.networkOptions && $scope.account && $scope.stage.region) {
        let networkSubnets = _.filter($scope.subnetOptions, subnetOption => {
          return subnetOption.region === $scope.stage.region && subnetOption.vcnId === selectedNetwork_ocid;
        });
        let networkAvailabilityDomains = _.map(networkSubnets, networkSubnet => {
          return networkSubnet.availabilityDomain;
        });
        $scope.constrainedAvailabilityDomainOptions = _.chain(networkAvailabilityDomains)
          .uniq()
          .sort()
          .value();
        // set default
        if ($scope.constrainedAvailabilityDomainOptions && $scope.constrainedAvailabilityDomainOptions.length > 0) {
          $scope.stage.extended_attributes.availability_domain = $scope.constrainedAvailabilityDomainOptions[0];
        } else {
          delete $scope.stage.extended_attributes.availability_domain;
        }
        return $scope.constrainedAvailabilityDomainOptions;
      }
    };

    /**
     * Calculate the constrained set of subnetOptions w.r.t the selected network/ad and attempt to select a default.
     */
    $scope.constrainSubnetOptions = function() {
      if ($scope.subnetOptions && $scope.account && $scope.stage.region) {
        $scope.constrainedSubnetOptions = _.filter($scope.subnetOptions, subnetOption => {
          return (
            subnetOption.region == $scope.stage.region &&
            subnetOption.vcnId == $scope.stage.extended_attributes.network_ocid &&
            subnetOption.availabilityDomain == $scope.stage.extended_attributes.availability_domain
          );
        });
      }
      // if an applicable default is available set it; else clear.
      if ($scope.constrainedSubnetOptions && $scope.constrainedSubnetOptions.length > 0) {
        $scope.stage.extended_attributes.subnet_ocid = $scope.constrainedSubnetOptions[0].id;
      } else {
        delete $scope.stage.extended_attributes.subnet_ocid;
      }

      return $scope.constrainedSubnetOptions;
    };

    initialize();
  });
