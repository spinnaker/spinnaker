'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.application.create.modal.controller', [
    require('angular-ui-router'),
    require('../service/applications.write.service.js'),
    require('../service/applications.read.service.js'),
    require('../../utils/lodash.js'),
    require('../../account/account.service.js'),
  ])
  .controller('CreateApplicationModalCtrl', function($scope, $q, $log, $state, $modalInstance, accountService, applicationWriter, applicationReader, _) {
    var vm = this;

    let applicationLoader = applicationReader.listApplications();
    applicationLoader.then((applications) => vm.data.appNameList = _.pluck(applications, 'name'));

    let accountLoader = accountService.listAccounts();
    accountLoader.then((accounts) => vm.data.accounts = accounts);

    let providerLoader = accountService.listProviders();
    providerLoader.then((providers) => vm.data.cloudProviders = providers);

    $q.all([accountLoader, applicationLoader, providerLoader]).then(() => vm.state.initializing = false);

    vm.state = {
      initializing: true,
      submitting: false,
      errorMsgs: [],
      emailErrorMsg: []
    };
    vm.data = {

    };
    vm.submitting = false;
    vm.application = {
      cloudProviders: [],
    };

    vm.clearEmailMsg = function() {
      vm.state.emailErrorMsg = '';
    };


    vm.createAppForAccount = function(application, accounts, deferred) {
      if(!deferred) {
        deferred = $q.defer();
      }

      var account = _.head(accounts);

      if (account) {
        applicationWriter.createApplication(application, account)
          .then(
            function(taskResponse){
              taskResponse
                .watchForTaskComplete()
                .then(() => {
                  var tailAccounts = _.tail(accounts);
                  vm.createAppForAccount(application, tailAccounts, deferred);
                },
                (error) => {
                  vm.state.errorMsgs.push('Could not create application in ' + account + ': ' + error.failureMessage);
                  goIdle();
                  deferred.reject();
                });
            },
            function() {
              vm.state.errorMsgs.push('Could not create application');
              goIdle();
              return deferred.reject();
            }
          );
      } else {
        deferred.resolve();
      }

      deferred.notify();
      return deferred.promise;
    };


    vm.submit = function() {
      submitting();

      vm.application.name = vm.application.name.toLowerCase();

      if (vm.data.cloudProviders.length === 1) {
        vm.application.cloudProviders = vm.data.cloudProviders;
      }

      vm.createAppForAccount(vm.application, vm.application.account).then(
        routeToApplication,
        extractErrorMsg
      );

    };


    function routeToApplication() {
      _.delay( function() {
          $state.go(
            'home.applications.application.insight.clusters', {
              application: vm.application.name,
            }
          );
      }, 1000 );
    }

    function submitting() {
      vm.errorMsgs = [];
      vm.submitting = true;
    }

    function goIdle() {
      vm.submitting = false;
    }

    function assignErrorMsgs() {
      vm.emailErrorMsg = vm.errorMsgs.filter(function(msg){
        return msg
          .toLowerCase()
          .indexOf('email') > -1;
      });
    }


    function extractErrorMsg(error) {
      $log.debug('extract error', error);
      assignErrorMsgs();
      goIdle();

    }

    return vm;
  });
