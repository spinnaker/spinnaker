'use strict';

describe('fastPropertyRollout Controller:', function () {

  let controller;
  let $timeout;
  let fastPropertyReader;
  let $scope;
  let $q;

  let promotionList = [
    {
      'id': '8',
      'key': 'simianarmy.calendar.isMonkeyTime',
      'value': 'true',
      'cmcTicket': '00000',
      'description': 'force monkey time',
      'constraints': 'boolean',
      'email': 'ebukoski@netflix.com',
      'updatedBy': 'ebukoski@netflix.com',
      'scopes': {
        'from': {
          'env': 'test',
          'appId': 'janitormonkey',
          'stack': 'janitor',
          'region': 'us-west-1',
          'cluster': null,
          'asg': null,
          'zone': null,
          'serverId': null
        },
        'to': {
          'env': 'test',
          'appId': 'janitormonkey',
          'stack': null,
          'region': null,
          'cluster': null,
          'asg': null,
          'zone': null,
          'serverId': null
        },
        'current': {
          'env': 'test',
          'appId': 'janitormonkey',
          'stack': 'janitor',
          'region': null,
          'cluster': null,
          'asg': null,
          'zone': null,
          'serverId': null
        },
        'steps': 3,
        'stepNo': 2
      },
      'state': 'Running',
      'history': [
        {
          'when': '2015-09-10T23:49:18.345Z',
          'message': 'Requested'
        },
        {
          'when': '2015-09-10T23:49:19.910Z',
          'message': 'Proceeded to Scope(test,janitormonkey,janitor,us-west-1)'
        },
        {
          'when': '2015-09-11T00:04:49.541Z',
          'message': 'Proceeded to Scope(test,janitormonkey,janitor)'
        }
      ],
      'appId': 'janitormonkey'
    }
  ];

  beforeEach(
    window.module(
      require('./fastPropertyRollouts.controller.js')
    )
  );


  beforeEach(
    window.inject(function($rootScope, $controller, _$q_, _$timeout_, _fastPropertyReader_) {
      $timeout = _$timeout_;
      $q = _$q_;
      $scope = $rootScope.$new();
      fastPropertyReader = _fastPropertyReader_;

      spyOn(fastPropertyReader, 'loadPromotions').and.callFake( function() {
        return $q.when(promotionList);
      });

      controller = $controller('FastPropertyRolloutController', {
        '$scope': $scope,
        'fastPropertyReader': fastPropertyReader
      });
    })
  );

  describe('Promotions', function () {
    it('should fetch promotions and assign to controller variables', function () {
      controller.loadPromotions();

      $scope.$digest();

      expect(controller.promotions.length).toEqual(1);
      expect(controller.filteredPromotions.length).toEqual(1);
      expect(controller.promotions).toEqual(controller.filteredPromotions);
    });
  });

});
