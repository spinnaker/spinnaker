import { IComponentController, IComponentOptions, module } from 'angular';

import { PIPELINE_CONFIG_PROVIDER } from 'core/pipeline/config/pipelineConfigProvider';
import { FieldType, IArtifactField, IExpectedArtifact, MissingArtifactPolicy } from 'core/domain/IExpectedArtifact';
import { IPipeline } from 'core/domain/IPipeline';

class ArtifactController implements IComponentController {
  public artifact: IExpectedArtifact;
  public pipeline: IPipeline;
  public missingPolicies: string[] = Object.keys(MissingArtifactPolicy).map(key => MissingArtifactPolicy[key as any]);
  public fieldNames: string[] = ['type', 'name', 'version', 'location', 'reference', 'artifactAccount', 'provenance', 'uuid'];
  public fieldTypes: string[] = Object.keys(FieldType).map(key => FieldType[key as any]);

  public removeArtifact(): void {
    const artifactIndex = this.pipeline.expectedArtifacts.indexOf(this.artifact);
    this.pipeline.expectedArtifacts.splice(artifactIndex, 1);

    this.pipeline.triggers
      .forEach(t => t.expectedArtifacts = t.expectedArtifacts.filter(a => !this.equals(a, this.artifact)));
  }

  public addField(): void {
    const newField: IArtifactField = {
      fieldName: '',
      fieldType: FieldType.MustMatch,
      value: '',
      missingPolicy: MissingArtifactPolicy.FailPipeline
    };

    if (!this.artifact.fields) {
      this.artifact.fields = [];
    }
    this.artifact.fields.push(newField);
  }

  public removeField(fieldIndex: number): void {
    this.artifact.fields.splice(fieldIndex, 1);
  }

  private equals(first: IExpectedArtifact, other: IExpectedArtifact): boolean {
    const fieldIsEqual = (firstField: IArtifactField, otherField: IArtifactField): boolean => {
      return firstField.fieldName === otherField.fieldName
        && firstField.fieldType === otherField.fieldType
        && firstField.value === otherField.value
        && firstField.missingPolicy === otherField.missingPolicy
        && firstField.expression === otherField.expression;
    };
    if (first.fields.length !== other.fields.length) {
      return false;
    }

    return first.fields.every(firstField => {
      return other.fields.some(otherField => fieldIsEqual(firstField, otherField));
    });
  }
}

class ArtifactComponent implements IComponentOptions {
  public bindings = { artifact: '<', pipeline: '<' };
  public templateUrl = require('./artifact.html');
  public controller = ArtifactController;
}

export const ARTIFACT = 'spinnaker.core.pipeline.trigger.artifact';
module(ARTIFACT, [
  PIPELINE_CONFIG_PROVIDER,
]).component('artifact', new ArtifactComponent());
