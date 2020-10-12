'use strict';

describe('Directives: projectCluster', function () {
  require('./projectCluster.directive.html');

  beforeEach(window.module(require('./projectCluster.directive').name));

  var $compile, $scope;

  // https://docs.angularjs.org/guide/migration#migrate1.5to1.6-ng-services-$compile
  beforeEach(
    window.module(($compileProvider) => {
      $compileProvider.preAssignBindingsEnabled(true);
    }),
  );

  beforeEach(
    window.inject(function ($rootScope, _$compile_) {
      $scope = $rootScope.$new();
      $compile = _$compile_;
    }),
  );

  beforeEach(function () {
    $scope.project = {
      name: 'foo',
    };
  });

  it('includes a column for each region and a row for each application, with build number for application', function () {
    let cluster1 = { region: 'us-east-1', builds: [] },
      cluster2 = { region: 'us-west-1', builds: [{ buildNumber: 1 }] },
      cluster3 = { region: 'us-east-1', builds: [{ buildNumber: 1 }] };

    $scope.cluster = {
      account: 'prod',
      stack: '',
      detail: '',
      applications: [
        { application: 'app1', clusters: [cluster1] },
        { application: 'app2', clusters: [cluster2, cluster3] },
      ],
    };

    var html = '<project-cluster cluster="cluster" project="project"></project-cluster>';

    var elem = $compile(html)($scope);
    $scope.$digest();

    // columns for each region
    expect(elem.find('.rollup-details').length).toBe(1);
    expect(elem.find('th').length).toBe(5);
    expect(elem.find('th:eq(3)').html().trim()).toBe('us-east-1');
    expect(elem.find('th:eq(4)').html().trim()).toBe('us-west-1');

    // application rows
    let app1Row = elem.find('tbody tr:eq(0)');
    let app2Row = elem.find('tbody tr:eq(1)');
    expect(elem.find('tbody tr').length).toBe(2);
    expect(app1Row.find('td:eq(0)').text().trim()).toBe('APP1');
    expect(app2Row.find('td:eq(0)').text().trim()).toBe('APP2');
    expect(app1Row.find('td:eq(1)').text().trim()).toBe('');
    expect(app2Row.find('td:eq(1)').text().trim()).toBe('#1');
  });

  it('omits last push entry if none found for an application', function () {
    let cluster1 = { region: 'us-east-1', builds: [] },
      cluster2 = { region: 'us-west-1', builds: [{ buildNumber: 1 }] };

    $scope.cluster = {
      account: 'prod',
      stack: '',
      detail: '',
      applications: [
        { application: 'app1', clusters: [cluster1] },
        { application: 'app2', lastPush: 3, clusters: [cluster2] },
      ],
    };

    var html = '<project-cluster cluster="cluster" project="project"></project-cluster>';

    var elem = $compile(html)($scope);
    $scope.$digest();

    // application rows
    let app1Row = elem.find('tbody tr:eq(0)');
    let app2Row = elem.find('tbody tr:eq(1)');
    expect(app1Row.find('td:eq(2)').text().trim()).toBe('-');
    // dates are fun to test - let's just verify it's not "-"
    expect(app2Row.find('td:eq(2)').text().trim()).not.toBe('-');
  });

  it('includes application count in header, instance counts in header and each region', function () {
    let cluster1 = { region: 'us-east-1', builds: [], instanceCounts: { total: 3, up: 3, down: 0 } },
      cluster2 = {
        region: 'us-west-1',
        builds: [{ buildNumber: 1 }],
        instanceCounts: { total: 3, up: 1, down: 2, starting: 1 },
      };

    $scope.cluster = {
      account: 'prod',
      stack: 'foo',
      detail: '*',
      instanceCounts: { up: 4, down: 2, unknown: 1 },
      applications: [
        { application: 'app1', clusters: [cluster1] },
        { application: 'app2', lastPush: 3, clusters: [cluster2] },
      ],
    };

    var html = '<project-cluster cluster="cluster" project="project"></project-cluster>';

    var elem = $compile(html)($scope);
    $scope.$digest();

    expect(elem.find('.cluster-name').text().trim()).toBe('foo-*');
    expect(elem.find('.cluster-health:eq(0)').text().trim()).toBe('2 Applications');

    // first app: dash in us-west-1, 3 up in us-east-1
    expect(elem.find('tbody tr:eq(0) td:eq(4)').text().trim()).toBe('-');

    // second app: dash in us-east-1, 1 up in us-west-1
    expect(elem.find('tbody tr:eq(1) td:eq(3)').text().trim()).toBe('-');
  });
});
