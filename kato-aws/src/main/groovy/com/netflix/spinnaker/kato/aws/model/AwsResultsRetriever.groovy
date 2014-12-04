/*
 * Copyright 2014 Netflix, Inc.
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
package com.netflix.spinnaker.kato.aws.model
/**
 * This class exists as a template for the boilerplate code involved in retrieving results from AWS
 * potentially using multiple requests and tokens. There are protected methods that should be implemented
 * depending on the way that you need to make your request and get results.
 *
 * @param < T >  Type of AWS object to retrieve
 * @param < Q >  Request used to retrieve T from AWS
 * @param < S >  Result returned by AWS for retrieval of T
 */
abstract class AwsResultsRetriever<T, Q, S> {

  final int maxResults

  AwsResultsRetriever(int maxResults = Integer.MAX_VALUE) {
    this.maxResults = maxResults
  }

  List<T> retrieve(Q request) {
    List<T> items = []
    Integer remaining = maxResults
    String nextToken = null
    while (remaining > 0) {
      setNextToken(request, nextToken)
      limitRetrieval(request, remaining)
      S result = makeRequest(request)
      items.addAll(accessResult(result))
      nextToken = getNextToken(result)
      if (nextToken == null) {
        break // There are no more items to collect
      }
      remaining = maxResults - items.size()
    }
    items
  }

  // Methods below may need to be overridden based on the specific request and response types

  protected abstract S makeRequest(Q request)

  protected abstract List<T> accessResult(S result)

  protected void limitRetrieval(Q request, int remaining) {
    // Won't limit the items retrieved by default. There is no standard way to do this for all AWS requests.
    // It can be implemented with something like this:
    // request.withMaxResults(Math.min(maxResultsPerRequest, remaining))
  }

  protected void setNextToken(Q request, String nextToken) {
    request.withNextToken(nextToken)
  }

  protected String getNextToken(S result) {
    result.nextToken
  }
}
