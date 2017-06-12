import { mock, IControllerService } from 'angular';

import { EDIT_PIPELINE_JSON_MODAL_CONTROLLER, EditPipelineJsonModalCtrl } from './editPipelineJsonModal.controller';
import { IPipeline} from 'core/domain';

describe('Controller: editPipelineJsonModal', () => {

  let $ctrl: IControllerService,
    controller: EditPipelineJsonModalCtrl,
    $uibModalInstance: any;
  beforeEach(mock.module(EDIT_PIPELINE_JSON_MODAL_CONTROLLER));
  beforeEach(mock.inject(($controller: IControllerService) =>  $ctrl = $controller));

  function initializeController(pipeline: IPipeline) {
    $uibModalInstance = {close: () => {}};
    controller = $ctrl(EditPipelineJsonModalCtrl, {$uibModalInstance, pipeline});
  }

  it('controller removes name, application, appConfig, all fields and hash keys', () => {

    const pipeline: any = {
      name: 'foo',
      application: 'myApp',
      appConfig: 'appConfig',
      stage: {
        foo: [{}],
        bar: {},
        baz: '',
        bat: 4
      }
    };
    initializeController(pipeline);

    // sprinkle some hash keys into the pipeline
    pipeline.stage.$$hashKey = '01D';
    pipeline.stage.foo[0].$$hashKey = '01F';
    pipeline.stage.bar.$$hashKey = '01G';
    pipeline.plain = () => pipeline;
    controller.$onInit();

    const converted: any = JSON.parse(controller.command.pipelineJSON);
    expect(converted.name).toBeUndefined();
    expect(converted.application).toBeUndefined();
    expect(converted.appConfig).toBe('appConfig');

    expect(converted.stage.$$hashKey).toBeUndefined();
    expect(converted.stage.foo[0].$$hashKey).toBeUndefined();
    expect(converted.stage.bar.$$hashKey).toBeUndefined();
  });

  it('updatePipeline updates fields, removing name if added', () => {

    const pipeline: any = {
      application: 'myApp',
      name: 'foo',
      stage: {
        foo: [
          {}
        ],
        bar: {},
        baz: '',
        bat: 4
      }
    };
    initializeController(pipeline);
    controller.$onInit();
    spyOn($uibModalInstance, 'close');

    const converted: any = JSON.parse(controller.command.pipelineJSON);
    converted.application = 'someOtherApp';
    converted.name = 'replacedName';
    converted.bar = {updated: true};
    controller.command.pipelineJSON = JSON.stringify(converted);
    controller.updatePipeline();

    expect(pipeline.application).toBe('myApp');
    expect(pipeline.bar.updated).toBe(true);
    expect($uibModalInstance.close).toHaveBeenCalled();
  });

  it('updatePipeline generates an error message when malformed JSON provided', () => {

    const pipeline: any = {};
    initializeController(pipeline);

    controller.command = {pipelineJSON: 'This is not very good JSON', locked: false};
    controller.updatePipeline();

    expect(controller.command.invalid).toBe(true);
    expect(controller.command.errorMessage).not.toBe(null);
  });
});
