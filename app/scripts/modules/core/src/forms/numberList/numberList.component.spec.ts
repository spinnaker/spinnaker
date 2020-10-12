import { mock } from 'angular';
import { NUMBER_LIST_COMPONENT, INumberListConstraints } from './numberList.component';

describe('Component: numberList', () => {
  let $compile: ng.ICompileService,
    model: number[],
    stringModel: string,
    $scope: ng.IScope,
    elem: any,
    constraints: INumberListConstraints,
    onChange: () => any;

  beforeEach(mock.module(NUMBER_LIST_COMPONENT));

  beforeEach(
    mock.inject((_$compile_: ng.ICompileService, $rootScope: ng.IScope) => {
      $compile = _$compile_;
      $scope = $rootScope.$new();
    }),
  );

  const initialize = (startModel: number[]) => {
    model = startModel;
    $scope['data'] = {
      model: startModel,
      constraints,
      onChange,
    };
    if (stringModel) {
      $scope['data'].model = stringModel;
    }

    const dom = `<number-list model="data.model" constraints="data.constraints" on-change="data.onChange()"></number-list>`;
    elem = $compile(dom)($scope);
    $scope.$digest();
  };

  describe('initialization', () => {
    it('initializes with an empty number input on an empty list, but does not add empty entry to model', () => {
      initialize([]);
      expect(elem.find('input[type="number"]').length).toBe(1);
      expect(model.length).toBe(0);
    });

    it('initializes with existing numbers', () => {
      initialize([1, 4]);
      expect(elem.find('input[type="number"]').length).toBe(2);
      expect(elem.find('input[type="number"]')[0].value).toBe('1');
      expect(elem.find('input[type="number"]')[1].value).toBe('4');
      expect(model.length).toBe(2);
    });

    it('does not show delete button on first entry', () => {
      initialize([]);
      expect(elem.find('.glyphicon-trash').length).toBe(0);
    });
  });

  describe('model synchronization', () => {
    it('does not add invalid entry to model', () => {
      initialize([]);
      elem.find('input[type="number"]').val('invalid').change();
      elem.find('input[type="number"]').change();
      $scope.$digest();
      expect(model).toEqual([]);

      elem.find('input[type="number"]').val('3').change();
      elem.find('input[type="number"]').change();
      $scope.$digest();
      expect(model).toEqual([3]);
    });

    it('removes an entry when remove button clicked', () => {
      initialize([1, 2]);
      elem.find('.glyphicon-trash').click();
      $scope.$digest();
      expect(model).toEqual([1]);
    });

    it('does not add empty entry to model when add button is clicked', () => {
      initialize([]);
      elem.find('.add-new').click();
      $scope.$digest();
      expect(elem.find('input[type="number"]').length).toBe(2);
      expect(model).toEqual([]);
    });

    it('calls onChange event if present', () => {
      let onChangeCalled = false;
      onChange = () => {
        onChangeCalled = true;
      };
      initialize([1]);
      elem.find('input[type="number"]').val('2').change();
      $scope.$digest();
      expect(onChangeCalled).toBe(true);
    });
  });

  describe('validation', () => {
    it('marks invalid fields', () => {
      constraints = {
        min: 4,
        max: 10,
      };
      initialize([1, 5, 50]);
      expect(elem.find('.ng-invalid').length).toBe(2);
    });
  });

  describe('spEl handling', () => {
    it('shows a text field instead of number fields when spel is detected', () => {
      stringModel = '${parameters.ports}';
      initialize([]);
      expect(elem.find('input[type="number"]').length).toBe(0);
      expect(elem.find('input[type="text"]').length).toBe(1);
    });
  });
});
