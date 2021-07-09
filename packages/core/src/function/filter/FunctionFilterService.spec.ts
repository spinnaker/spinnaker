import { Application } from '../../application/application.model';
import { ApplicationModelBuilder } from '../../application/applicationModel.builder';
import { FunctionState } from '../../state';
import { groupBy, Dictionary } from 'lodash';
import { IFunction } from '@spinnaker/core';
// Most of this logic has been moved to filter.model.service.js, so these act more as integration tests
describe('Service: functionFilterService', function () {
  const debounceTimeout = 30;

  let app: Application, resultJson: any;

  beforeEach(() => {
    FunctionState.filterModel.asFilterModel.groups = [];
  });

  beforeEach(function () {
    app = ApplicationModelBuilder.createApplicationForTests('app', { key: 'functions', lazy: true, defaultData: [] });
    app.getDataSource('functions').data = [
      {
        functionName: 'function1',
        region: 'us-east-1',
        account: 'test',
      },
      {
        functionName: 'function2',
        region: 'us-west-1',
        account: 'prod',
      },
      {
        functionName: 'function3',
        region: 'us-east-1',
        account: 'test',
      },
    ];

    resultJson = [
      { heading: 'us-east-1', functionDef: app.functions.data[0] },
      { heading: 'us-west-1', functionDef: app.functions.data[1] },
      { heading: 'us-east-1', functionDef: app.functions.data[2] },
    ];
    FunctionState.filterModel.asFilterModel.clearFilters();
  });

  describe('Updating the function group', function () {
    it('no filter: should be transformed', function (done) {
      const expected = [
        {
          heading: 'prod',
          subgroups: [{ heading: 'function2', subgroups: [resultJson[1]] }],
        },
        {
          heading: 'test',
          subgroups: [
            { heading: 'function1', subgroups: [resultJson[0]] },
            { heading: 'function3', subgroups: [resultJson[2]] },
          ],
        },
      ];
      FunctionState.filterService.updateFunctionGroups(app);
      setTimeout(() => {
        expect(FunctionState.filterModel.asFilterModel.groups).toEqual(expected);
        done();
      }, debounceTimeout);
    });

    describe('filtering by account type', function () {
      it('1 account filter: should be transformed showing only prod accounts', function (done) {
        FunctionState.filterModel.asFilterModel.sortFilter.account = { prod: true };
        FunctionState.filterService.updateFunctionGroups(app);

        setTimeout(() => {
          expect(FunctionState.filterModel.asFilterModel.groups).toEqual([
            {
              heading: 'prod',
              subgroups: [{ heading: 'function2', subgroups: [resultJson[1]] }],
            },
          ]);
          done();
        }, debounceTimeout);
      });

      it('All account filters: should show all accounts', function (done) {
        FunctionState.filterModel.asFilterModel.sortFilter.account = { prod: true, test: true };
        FunctionState.filterService.updateFunctionGroups(app);

        setTimeout(() => {
          expect(FunctionState.filterModel.asFilterModel.groups).toEqual([
            {
              heading: 'prod',
              subgroups: [{ heading: 'function2', subgroups: [resultJson[1]] }],
            },
            {
              heading: 'test',
              subgroups: [
                { heading: 'function1', subgroups: [resultJson[0]] },
                { heading: 'function3', subgroups: [resultJson[2]] },
              ],
            },
          ]);
          done();
        }, debounceTimeout);
      });
    });
  });

  describe('filter by region', function () {
    it('1 region: should filter by that region', function (done) {
      FunctionState.filterModel.asFilterModel.sortFilter.region = { 'us-east-1': true };
      FunctionState.filterService.updateFunctionGroups(app);

      setTimeout(() => {
        expect(FunctionState.filterModel.asFilterModel.groups).toEqual([
          {
            heading: 'test',
            subgroups: [
              { heading: 'function1', subgroups: [resultJson[0]] },
              { heading: 'function3', subgroups: [resultJson[2]] },
            ],
          },
        ]);
        done();
      }, debounceTimeout);
    });

    it('All regions: should show all functions', function (done) {
      FunctionState.filterModel.asFilterModel.sortFilter.region = { 'us-east-1': true, 'us-west-1': true };
      FunctionState.filterService.updateFunctionGroups(app);

      setTimeout(() => {
        expect(FunctionState.filterModel.asFilterModel.groups).toEqual([
          {
            heading: 'prod',
            subgroups: [{ heading: 'function2', subgroups: [resultJson[1]] }],
          },
          {
            heading: 'test',
            subgroups: [
              { heading: 'function1', subgroups: [resultJson[0]] },
              { heading: 'function3', subgroups: [resultJson[2]] },
            ],
          },
        ]);
        done();
      }, debounceTimeout);
    });
  });

  it('Filter by region: filterFunctionsForDisplay', function (done) {
    FunctionState.filterModel.asFilterModel.sortFilter.region = { 'us-west-1': true };
    const functionsToDisplay = FunctionState.filterService.filterFunctionsForDisplay(app.functions.data);
    setTimeout(() => {
      expect(functionsToDisplay).toEqual([resultJson[1].functionDef]);
      done();
    }, debounceTimeout);
  });

  describe('function with same name and different regions ', function () {
    it('grouped with region in heading', function (done) {
      const newFunction = {
        functionName: 'function1',
        account: 'test',
        region: 'eu-west-1',
      };
      app.functions.data.push(newFunction);
      const groupedByAccount: Dictionary<IFunction[]> = groupBy(app.functions.data, 'account');
      const groups = FunctionState.filterService.getFunctionGroups(groupedByAccount);
      setTimeout(() => {
        expect(groups).toEqual([
          {
            heading: 'test',
            subgroups: [
              { heading: 'function1 (eu-west-1)', subgroups: [{ heading: 'eu-west-1', functionDef: newFunction }] },
              { heading: 'function1 (us-east-1)', subgroups: [resultJson[0]] },
              { heading: 'function3', subgroups: [resultJson[2]] },
            ],
          },
          {
            heading: 'prod',
            subgroups: [{ heading: 'function2', subgroups: [resultJson[1]] }],
          },
        ]);
        done();
      }, debounceTimeout);
    });
  });
});
