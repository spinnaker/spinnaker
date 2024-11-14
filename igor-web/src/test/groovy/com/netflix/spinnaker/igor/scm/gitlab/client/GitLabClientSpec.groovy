/*
 * Copyright 2017 bol.com
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

package com.netflix.spinnaker.igor.scm.gitlab.client

import com.netflix.spinnaker.igor.config.GitLabConfig
import com.netflix.spinnaker.igor.scm.gitlab.client.model.CompareCommitsResponse
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import spock.lang.Shared
import spock.lang.Specification

class GitLabClientSpec extends Specification {

    @Shared
    GitLabClient client

    @Shared
    MockWebServer server

    void setup() {
        server = new MockWebServer()
    }

    void cleanup() {
        server.shutdown()
    }

    private void setResponse(String body) {
        server.enqueue(
            new MockResponse()
                .setBody(body)
                .setHeader('Content-Type', 'text/xml;charset=UTF-8')
        )
        server.start()
        client = new GitLabConfig().gitLabClient(server.url('/').toString(), "token")
    }

    void 'getCompareCommits'() {
        given:
        setResponse getCompareCommitsResponse()

        when:
        CompareCommitsResponse commitsResponse = client.getCompareCommits('gitlab-org', 'gitlab-ce', ['from': 'bacf671a335297e61ad4c470cde49ce4d3fcc009', 'to': 'd41e66cb632cf4a51428c87a07cbdd182e3e0697'])

        then:
        commitsResponse.commits.size() == 2

        with(commitsResponse.commits.get(0)) {
            id == '8922b7b781f241d9afd77f15fad0cfcab14d5205'
            authorName == 'Michael Kozono'
            authoredDate == new Date(1510855401000)
            message == 'Update database_debugging.md'
        }

        with(commitsResponse.commits.get(1)) {
            id == 'd41e66cb632cf4a51428c87a07cbdd182e3e0697'
            authorName == 'Marcia Ramos'
            authoredDate == new Date(1510865144000)
            message == "Merge branch 'docs/refactor-cluster' into 'master'\n\nRefactor  Cluster docs\n\nCloses #39952\n\nSee merge request gitlab-org/gitlab-ce!15418"
        }
    }

    String getCompareCommitsResponse() {
        return '''{
  "commit": {
    "id": "d41e66cb632cf4a51428c87a07cbdd182e3e0697",
    "short_id": "d41e66cb",
    "title": "Merge branch 'docs/refactor-cluster' into 'master'",
    "created_at": "2017-11-16T20:45:44.000+00:00",
    "parent_ids": [
      "8922b7b781f241d9afd77f15fad0cfcab14d5205",
      "bacf671a335297e61ad4c470cde49ce4d3fcc009"
    ],
    "message": "Merge branch 'docs/refactor-cluster' into 'master'\\n\\nRefactor  Cluster docs\\n\\nCloses #39952\\n\\nSee merge request gitlab-org/gitlab-ce!15418",
    "author_name": "Marcia Ramos",
    "author_email": "virtua.creative@gmail.com",
    "authored_date": "2017-11-16T20:45:44.000+00:00",
    "committer_name": "Marcia Ramos",
    "committer_email": "virtua.creative@gmail.com",
    "committed_date": "2017-11-16T20:45:44.000+00:00"
  },
  "commits": [
    {
      "id": "8922b7b781f241d9afd77f15fad0cfcab14d5205",
      "short_id": "8922b7b7",
      "title": "Update database_debugging.md",
      "created_at": "2017-11-16T18:03:21.000+00:00",
      "parent_ids": [
        "3d16f7cd57a5332936d912a46a4ec73e730a825e"
      ],
      "message": "Update database_debugging.md",
      "author_name": "Michael Kozono",
      "author_email": "mkozono@gmail.com",
      "authored_date": "2017-11-16T18:03:21.000+00:00",
      "committer_name": "Michael Kozono",
      "committer_email": "mkozono@gmail.com",
      "committed_date": "2017-11-16T18:03:21.000+00:00"
    },
    {
      "id": "d41e66cb632cf4a51428c87a07cbdd182e3e0697",
      "short_id": "d41e66cb",
      "title": "Merge branch 'docs/refactor-cluster' into 'master'",
      "created_at": "2017-11-16T20:45:44.000+00:00",
      "parent_ids": [
        "8922b7b781f241d9afd77f15fad0cfcab14d5205",
        "bacf671a335297e61ad4c470cde49ce4d3fcc009"
      ],
      "message": "Merge branch 'docs/refactor-cluster' into 'master'\\n\\nRefactor  Cluster docs\\n\\nCloses #39952\\n\\nSee merge request gitlab-org/gitlab-ce!15418",
      "author_name": "Marcia Ramos",
      "author_email": "virtua.creative@gmail.com",
      "authored_date": "2017-11-16T20:45:44.000+00:00",
      "committer_name": "Marcia Ramos",
      "committer_email": "virtua.creative@gmail.com",
      "committed_date": "2017-11-16T20:45:44.000+00:00"
    }
  ],
  "diffs": [
    {
      "old_path": "doc/development/database_debugging.md",
      "new_path": "doc/development/database_debugging.md",
      "a_mode": "100644",
      "b_mode": "100644",
      "new_file": false,
      "renamed_file": false,
      "deleted_file": false,
      "diff": "--- a/doc/development/database_debugging.md\\n+++ b/doc/development/database_debugging.md\\n@@ -9,7 +9,6 @@ An easy first step is to search for your error in Slack or google \\"GitLab <my er\\n \\n Available `RAILS_ENV`\\n \\n- - `production` (not sure if in GDK)\\n  - `development` (this is your main GDK db)\\n  - `test` (used for tests like rspec and spinach)\\n \\n@@ -18,9 +17,9 @@ Available `RAILS_ENV`\\n \\n If you just want to delete everything and start over,\\n \\n- - `bundle exec rake db:drop RAILS_ENV=development`\\n- - `bundle exec rake db:setup RAILS_ENV=development`\\n-\\n+ - `bundle exec rake dev:setup RAILS_ENV=development` : Also runs DB specific stuff and seeds dummy data (slow)\\n+ - `bundle exec rake db:reset RAILS_ENV=development` : Doesn't do the above (fast)\\n+ - `bundle exec rake db:reset RAILS_ENV=test` : Fix the test DB, since it doesn't contain important data.\\n \\n ## Migration wrangling\\n \\n"
    }
  ],
  "compare_timeout": false,
  "compare_same_ref": false
}'''
    }
}
