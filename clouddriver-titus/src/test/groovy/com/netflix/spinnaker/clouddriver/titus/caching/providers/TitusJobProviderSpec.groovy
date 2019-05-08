/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.titus.deploy.converters

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.aws.provider.view.AmazonS3DataProvider
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.titus.TitusClientProvider
import com.netflix.spinnaker.clouddriver.titus.caching.providers.TitusJobProvider
import com.netflix.spinnaker.clouddriver.titus.client.TitusClient
import com.netflix.spinnaker.clouddriver.titus.client.model.Job
import com.netflix.spinnaker.clouddriver.titus.client.model.Task
import com.netflix.spinnaker.clouddriver.titus.credentials.NetflixTitusCredentials
import spock.lang.Specification
import spock.lang.Subject

class TitusJobProviderSpec extends Specification {
  TitusClient titusClient = Stub()
  TitusClientProvider titusClientProvider = Stub()
  AccountCredentialsProvider accountCredentialsProvider = Stub()
  AmazonS3DataProvider amazonS3DataProvider = Stub()
  NetflixTitusCredentials mockCredentials = Stub()

  String account = 'ACCT'
  String location = 'us-best-1'
  String id = '12345-12345'

  Task task = new Task(
    id: 123,
    startedAt: new Date(),
    logLocation: [
      s3: [accountName: account, region: location, bucket: 'coolbucket', key: 'coolkey']
    ]
  )

  Job job = new Job(tasks: [task])

  @Subject
  TitusJobProvider titusJobProvider = new TitusJobProvider(titusClientProvider)

  def setup() {
    titusJobProvider.objectMapper = new ObjectMapper()
    titusJobProvider.accountCredentialsProvider = accountCredentialsProvider
    titusJobProvider.amazonS3DataProvider = amazonS3DataProvider
    accountCredentialsProvider.getCredentials(_) >> mockCredentials
    titusClientProvider.getTitusClient(_, _) >> titusClient
    titusClient.getJobAndAllRunningAndCompletedTasks(_) >> job
  }

  void 'getFileContents should parse json if the file ends in .json'() {
    given:
    String fileName = 'data.json'

    String fileContents = '''
    {
      "foo": "FOO",
      "bar": {
        "baz": "BAR.BAZ",
        "list": [ "one", "two" ]
      }
    }'''

    when:
    amazonS3DataProvider.getAdhocData(_, _, _, _) >> { args ->
      OutputStream outStream = args[3]
      outStream << fileContents
      outStream.close()
    }

    Map contents = titusJobProvider.getFileContents(account, location, id, fileName)

    then:
    contents == [
      foo: 'FOO',
      bar: [
        baz : 'BAR.BAZ',
        list: ['one', 'two']
      ]
    ]
  }

  void 'getFileContents should parse yaml, if the file ends in .yml'() {
    given:
    String fileName = 'data.yml'

    String fileContents = '''
    foo: FOO
    bar: 
      baz: BAR.BAZ
      list:
        - one
        - two
    '''

    when:
    amazonS3DataProvider.getAdhocData(_, _, _, _) >> { args ->
      OutputStream outStream = args[3]
      outStream << fileContents
      outStream.close()
    }

    Map contents = titusJobProvider.getFileContents(account, location, id, fileName)

    then:
    contents == [
      foo: 'FOO',
      bar: [
        baz : 'BAR.BAZ',
        list: ['one', 'two']
      ]
    ]
  }

  void 'getFileContents should parse properties files for all other extensions'() {
    given:
    String fileContents = '''
    foo: FOO
    bar.baz: BAR.BAZ
    '''

    when:
    amazonS3DataProvider.getAdhocData(_, _, _, _) >> { args ->
      OutputStream outStream = args[3]
      outStream << fileContents
      outStream.close()
    }

    Map contents = titusJobProvider.getFileContents(account, location, id, fileName)

    then:
    contents == [foo: 'FOO', 'bar.baz': 'BAR.BAZ']

    where:
    fileName          | _
    'data'            | _
    'data.properties' | _
    'data.asdfadf'    | _
  }
}
