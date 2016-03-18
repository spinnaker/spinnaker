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

package com.netflix.spinnaker.front50.pipeline

import com.netflix.spinnaker.front50.model.pipeline.Pipeline
import com.netflix.spinnaker.front50.utils.CassandraTestHelper
import spock.lang.Shared
import spock.lang.Specification

class PipelineRepositorySpec extends Specification {

    @Shared
    CassandraTestHelper cassandraTestHelper = new CassandraTestHelper()

    @Shared
    PipelineRepository repo

    @Shared
    Pipeline pipeline = [
        application: 'myapp',
        name       : 'my pipeline',
        randomField: '42'
    ] as Pipeline

    void setupSpec() {
        repo = new PipelineRepository(keyspace: cassandraTestHelper.keyspace)
        repo.init()
    }

    void setup() {
        repo.runQuery '''TRUNCATE pipeline;'''
    }

    void cleanup() {
        repo.runQuery '''TRUNCATE pipeline;'''
    }

    void 'can save and retrieve a pipeline'() {
        given:
        repo.create(pipeline.getId(), pipeline)

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
        repo.create(pipeline.getId(), pipeline)
        def pipeline2 = pipeline.clone()
        pipeline2.name = 'new pipeline'
        repo.create(pipeline2.id, pipeline2)
        def pipeline3 = pipeline.clone()
        pipeline3.application = 'new pipeline name'
        repo.create(pipeline3.id, pipeline3)

        when:
        List<Map> retrieved = repo.all()

        then:
        retrieved.size() == 3
    }

    void 'saving the same pipeline again preserves id'() {
        given:
        repo.create(pipeline.getId(), pipeline)

        when:
        Map retrievedPipeline = repo.getPipelinesByApplication('myapp').first()
        String pipelineId = retrievedPipeline.id

        and:
        repo.update(retrievedPipeline.getId(), retrievedPipeline)

        then:
        pipelineId == repo.getPipelinesByApplication('myapp').first().id
    }

    void 'can get the id of a pipeline via name and application'() {
        given:
        repo.create(pipeline.getId(), pipeline)
        Map retrievedPipeline = repo.getPipelinesByApplication('myapp').first()
        String pipelineId = retrievedPipeline.id

        expect:
        repo.getPipelineId(pipeline.application, pipeline.name) == pipelineId
        repo.getPipelineId(pipeline.application, 'invalidName') == null
        repo.getPipelineId('badApp', pipeline.name) == null
    }

    void 'can batch insert pipelines'() {
        given:
        def pipeline2 = pipeline.clone()
        pipeline2.name = 'new pipeline'
        repo.bulkImport([pipeline, pipeline2])

        expect:
        repo.all().size() == 2
    }

    void "can delete a pipeline by id"() {
        given:
        def pipeline = repo.create(pipeline.getId(), pipeline)
        String pipelineId = pipeline.id

        expect:
        repo.all().size() == 1

        when:
        repo.delete(pipelineId)

        then:
        repo.all().empty
    }

    void "looks up pipeline by name if an id is not provided"(){
        given:
        repo.create(pipeline.getId(), pipeline)

        expect:
        repo.all().size() == 1

        when:
        repo.create(null, [
            application: pipeline.application,
            name       : pipeline.name
        ] as Pipeline)

        then:
        repo.all().size() == 1
    }

}
