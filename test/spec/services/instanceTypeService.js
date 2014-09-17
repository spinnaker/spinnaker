/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

'use strict';

describe('Service: InstanceType', function() {

  var service, scope, $http, config, instanceTypes;

  beforeEach(module('deckApp'));

  beforeEach(inject(function(settings, instanceTypeService, $rootScope, $httpBackend) {
    instanceTypes = [];

    service = instanceTypeService;
    scope = $rootScope.$new();
    config = settings;
    $http = $httpBackend;

    $http.when('GET', config.awsMetadataUrl + '/instanceType').respond(200, instanceTypes);
    $http.when('GET', config.credentialsUrl + 'credentials').respond(200, []);
  }));

  it('returns an intersection of instance types, sorted by name', function() {

    // setup:
    instanceTypes.push({ name: 'small', regions: ['a','b']});
    instanceTypes.push({ name: 'medium', regions: ['b','c']});
    instanceTypes.push({ name: 'large', regions: ['c','d']});

    // expect:
    //                 regions     || expectedInstanceTypes
    testAvailableTypes(['a'],         ['small']);
    testAvailableTypes(['b'],         ['medium','small']);
    testAvailableTypes(['c'],         ['large','medium']);
    testAvailableTypes(['a','b'],     ['small']);
    testAvailableTypes(['b','c'],     ['medium']);
    testAvailableTypes(['a','c'],     []);
    testAvailableTypes(['a','b','c'], []);

    $http.flush();

  });


  function testAvailableTypes(regions, expected) {
    service.getAvailableTypesForRegions(regions).then( function(result) {
      expect(result).toEqual(expected);
    });
  }
});
