/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.mayo.pipeline

import com.netflix.astyanax.Keyspace
import com.netflix.spinnaker.mayo.utils.AbstractCassandraBackedSpec
import spock.lang.Shared

class PipelineRepositorySpec extends AbstractCassandraBackedSpec {

    @Shared
    PipelineRepository repo

    @Shared
    Keyspace keyspace

    @Shared
    Map pipeline = [
        application: 'myapp',
        name       : 'my pipeline',
        randomField: '42'
    ]

    void setupSpec() {
        repo = new PipelineRepository()
        repo.keyspace = keyspace
        repo.runQuery '''DROP TABLE pipeline;'''
        repo.onApplicationEvent(null)
    }

    void setup() {
        repo.runQuery '''TRUNCATE pipeline;'''
    }

    void cleanup() {
        repo.runQuery '''TRUNCATE pipeline;'''
    }

    void 'can save and retrieve a pipeline'() {
        given:
        repo.save(pipeline)

        when:
        List<Map> retrieved = repo.getPipelinesByApplication('myapp')
        Map retrivedPipeline = retrieved.first()

        then:
        retrieved.size() == 1
        retrivedPipeline.name == 'my pipeline'
        retrivedPipeline.randomField == '42'
        retrivedPipeline.id != null
    }

    void 'can get list of pipelines'() {
        given:
        repo.save(pipeline)
        def pipeline2 = pipeline.clone()
        pipeline2.name = 'new pipeline'
        repo.save(pipeline2)
        def pipeline3 = pipeline.clone()
        pipeline3.application = 'new pipeline name'
        repo.save(pipeline3)

        when:
        List<Map> retrieved = repo.list()

        then:
        retrieved.size() == 3
    }

    void 'saving the same pipeline again preserves id'() {
        given:
        repo.save(pipeline)

        when:
        Map retrievedPipeline = repo.getPipelinesByApplication('myapp').first()
        String pipelineId = retrievedPipeline.id

        and:
        repo.save(retrievedPipeline)

        then:
        pipelineId == repo.getPipelinesByApplication('myapp').first().id
    }

    void 'can get the id of a pipeline via name and application'() {
        given:
        repo.save(pipeline)
        Map retrievedPipeline = repo.getPipelinesByApplication('myapp').first()
        String pipelineId = retrievedPipeline.id

        expect:
        repo.get(pipeline.application, pipeline.name) == pipelineId
        repo.get(pipeline.application, 'invalidName') == null
        repo.get('badApp', pipeline.name) == null
    }

    void 'can batch insert pipelines'() {
        given:
        def pipeline2 = pipeline.clone()
        pipeline2.name = 'new pipeline'
        repo.batchUpdate([pipeline, pipeline2])

        expect:
        repo.list().size() == 2
    }

    void 'can delete pipelines by name'() {
        given:
        repo.save(pipeline)

        expect:
        repo.list().size() == 1

        when:
        repo.delete(pipeline.application, pipeline.name)

        then:
        repo.list().empty
    }

    void "renaming a pipeline preserves its id"() {
        given:
        repo.save(pipeline)
        String pipelineId = repo.get(pipeline.application, pipeline.name)

        expect:
        pipelineId != null

        when:
        repo.rename(pipeline.application, pipeline.name, 'new name')

        then:
        pipelineId == repo.get(pipeline.application, 'new name')
    }

    void "renaming a pipeline returns the new name in the pipeline definition"(){
        given:
        repo.save(pipeline)

        when:
        repo.rename(pipeline.application, pipeline.name, 'new name')

        then:
        repo.getPipelinesByApplication(pipeline.application).first().name == 'new name'
    }

    void "rename pipeline does nothing if pipeline does not exist"() {
        given:
        repo.save(pipeline)

        when:
        repo.rename(pipeline.application, 'nothing', 'nothing more')

        then:
        repo.get(pipeline.application, 'nothing more') == null
        repo.get(pipeline.application, 'nothing') == null
    }

    void "can delete a pipeline by id"() {
        given:
        repo.save(pipeline)
        String pipelineId = repo.get(pipeline.application, pipeline.name)

        expect:
        repo.list().size() == 1

        when:
        repo.deleteById(pipelineId)

        then:
        repo.list().empty
    }

    void "looks up pipeline by name if an id is not provided"(){
        given:
        repo.save(pipeline)

        expect:
        repo.list().size() == 1

        when:
        repo.save([
            application: pipeline.application,
            name       : pipeline.name
        ])

        then:
        repo.list().size() == 1
    }

}
