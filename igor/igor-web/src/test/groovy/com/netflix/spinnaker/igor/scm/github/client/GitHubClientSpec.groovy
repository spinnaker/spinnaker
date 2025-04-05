/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.igor.scm.github.client

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.igor.config.GitHubConfig
import com.netflix.spinnaker.igor.helpers.TestUtils
import com.netflix.spinnaker.igor.scm.github.client.model.CompareCommitsResponse
import com.netflix.spinnaker.igor.scm.github.client.model.GetRepositoryContentResponse
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import retrofit2.mock.Calls
import spock.lang.Shared
import spock.lang.Specification

import java.time.Instant

import static com.netflix.spinnaker.igor.helpers.TestUtils.createObjectMapper

/**
 * Tests that GitHubClient correctly binds to underlying model as expected
 */
class GitHubClientSpec extends Specification {

  @Shared
  GitHubClient client

  @Shared
  MockWebServer server

  @Shared
  ObjectMapper mapper

  void setup() {
    server = new MockWebServer()
    mapper = createObjectMapper()
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
    client = new GitHubConfig().gitHubClient(TestUtils.makeOkHttpClientConfig(), server.url('/').toString(), 'token', mapper)
  }

  void 'getDirectoryContent'() {
    given:
    setResponse getDirectoryContentResponse()

    when:
    List<GetRepositoryContentResponse> response = Retrofit2SyncCall.execute(client.listDirectory('foo', 'repo', 'test', 'master'))

    then:
    response.size() == 2
    response.get(0).type == 'file'
    response.get(0).path == 'lib/octokit.rb'

    response.get(1).type == 'dir'
    response.get(1).path == 'lib/octokit'
  }

  String getDirectoryContentResponse() {
    return '[\n' +
      '  {\n' +
      '    "type": "file",\n' +
      '    "size": 625,\n' +
      '    "name": "octokit.rb",\n' +
      '    "path": "lib/octokit.rb",\n' +
      '    "sha": "fff6fe3a23bf1c8ea0692b4a883af99bee26fd3b",\n' +
      '    "url": "https://api.github.com/repos/octokit/octokit.rb/contents/lib/octokit.rb",\n' +
      '    "git_url": "https://api.github.com/repos/octokit/octokit.rb/git/blobs/fff6fe3a23bf1c8ea0692b4a883af99bee26fd3b",\n' +
      '    "html_url": "https://github.com/octokit/octokit.rb/blob/master/lib/octokit.rb",\n' +
      '    "download_url": "https://raw.githubusercontent.com/octokit/octokit.rb/master/lib/octokit.rb",\n' +
      '    "_links": {\n' +
      '      "self": "https://api.github.com/repos/octokit/octokit.rb/contents/lib/octokit.rb",\n' +
      '      "git": "https://api.github.com/repos/octokit/octokit.rb/git/blobs/fff6fe3a23bf1c8ea0692b4a883af99bee26fd3b",\n' +
      '      "html": "https://github.com/octokit/octokit.rb/blob/master/lib/octokit.rb"\n' +
      '    }\n' +
      '  },\n' +
      '  {\n' +
      '    "type": "dir",\n' +
      '    "size": 0,\n' +
      '    "name": "octokit",\n' +
      '    "path": "lib/octokit",\n' +
      '    "sha": "a84d88e7554fc1fa21bcbc4efae3c782a70d2b9d",\n' +
      '    "url": "https://api.github.com/repos/octokit/octokit.rb/contents/lib/octokit",\n' +
      '    "git_url": "https://api.github.com/repos/octokit/octokit.rb/git/trees/a84d88e7554fc1fa21bcbc4efae3c782a70d2b9d",\n' +
      '    "html_url": "https://github.com/octokit/octokit.rb/tree/master/lib/octokit",\n' +
      '    "download_url": null,\n' +
      '    "_links": {\n' +
      '      "self": "https://api.github.com/repos/octokit/octokit.rb/contents/lib/octokit",\n' +
      '      "git": "https://api.github.com/repos/octokit/octokit.rb/git/trees/a84d88e7554fc1fa21bcbc4efae3c782a70d2b9d",\n' +
      '      "html": "https://github.com/octokit/octokit.rb/tree/master/lib/octokit"\n' +
      '    }\n' +
      '  }\n' +
      ']';
  }

  void 'getFileContent'() {
    given:
    setResponse getFileContentResponse()

    when:
    GetRepositoryContentResponse response = Retrofit2SyncCall.execute(client.getFileContent('foo', 'repo', 'README.md', 'master'))

    then:
    response.type == 'file'
    response.content == 'dGVzdA=='
  }

  String getFileContentResponse() {
    return '{\n' +
      '  "type": "file",\n' +
      '  "encoding": "base64",\n' +
      '  "size": 5362,\n' +
      '  "name": "README.md",\n' +
      '  "path": "README.md",\n' +
      '  "content": "dGVzdA==",\n' +
      '  "sha": "3d21ec53a331a6f037a91c368710b99387d012c1",\n' +
      '  "url": "https://api.github.com/repos/octokit/octokit.rb/contents/README.md",\n' +
      '  "git_url": "https://api.github.com/repos/octokit/octokit.rb/git/blobs/3d21ec53a331a6f037a91c368710b99387d012c1",\n' +
      '  "html_url": "https://github.com/octokit/octokit.rb/blob/master/README.md",\n' +
      '  "download_url": "https://raw.githubusercontent.com/octokit/octokit.rb/master/README.md",\n' +
      '  "_links": {\n' +
      '    "git": "https://api.github.com/repos/octokit/octokit.rb/git/blobs/3d21ec53a331a6f037a91c368710b99387d012c1",\n' +
      '    "self": "https://api.github.com/repos/octokit/octokit.rb/contents/README.md",\n' +
      '    "html": "https://github.com/octokit/octokit.rb/blob/master/README.md"\n' +
      '  }\n' +
      '}';
  }

  void 'getCompareCommits'() {
    given:
    setResponse getCompareCommitsResponse()

    when:
    CompareCommitsResponse commitsResponse = Retrofit2SyncCall.execute(client.getCompareCommits('foo', 'repo', 'abcd', 'defg'))

    then:
    commitsResponse.html_url == 'https://github.com/my-project/module/compare/0a7c0c17992b15c73de25b2a94abb4c88862b53f...7890bc148475432b9e537e03d37f22d9018ef9c8'
    commitsResponse.url == 'https://api.github.com/repos/my-project/module/compare/0a7c0c17992b15c73de25b2a94abb4c88862b53f...7890bc148475432b9e537e03d37f22d9018ef9c8'

    commitsResponse.commits.size() == 2

    with(commitsResponse.commits.get(0)) {
      sha == '83bdadb570db40cab995e1f402dea2b096d0c1a1'
      commitInfo.author.name == 'Joe Coder'
      commitInfo.author.email == 'joecoder@company.com'
      commitInfo.message == "bug fix"
      html_url == "https://github.com/my-project/module/commit/83bdadb570db40cab995e1f402dea2b096d0c1a1"
      commitInfo.author.date == Instant.ofEpochMilli(1433192015000)
    }

    with(commitsResponse.commits.get(1)) {
      sha == '7890bc148475432b9e537e03d37f22d9018ef9c8'
      commitInfo.author.name == 'Joe Coder'
      commitInfo.author.email == 'joecoder@company.com'
      commitInfo.message == "new feature"
      html_url == "https://github.com/my-project/module/commit/7890bc148475432b9e537e03d37f22d9018ef9c8"
      commitInfo.author.date == Instant.ofEpochMilli(1433192281000)
    }
  }

  String getCompareCommitsResponse() {
    return '\n' +
      '{\n' +
      '  "url": "https://api.github.com/repos/my-project/module/compare/0a7c0c17992b15c73de25b2a94abb4c88862b53f...7890bc148475432b9e537e03d37f22d9018ef9c8",\n' +
      '  "html_url": "https://github.com/my-project/module/compare/0a7c0c17992b15c73de25b2a94abb4c88862b53f...7890bc148475432b9e537e03d37f22d9018ef9c8",\n' +
      '  "permalink_url": "https://github.com/my-project/module/compare/my-project:0a7c0c1...my-project:7890bc1",\n' +
      '  "diff_url": "https://github.com/my-project/module/compare/0a7c0c17992b15c73de25b2a94abb4c88862b53f...7890bc148475432b9e537e03d37f22d9018ef9c8.diff",\n' +
      '  "patch_url": "https://github.com/my-project/module/compare/0a7c0c17992b15c73de25b2a94abb4c88862b53f...7890bc148475432b9e537e03d37f22d9018ef9c8.patch",\n' +
      '  "base_commit": {\n' +
      '    "sha": "0a7c0c17992b15c73de25b2a94abb4c88862b53f",\n' +
      '    "commit": {\n' +
      '      "author": {\n' +
      '        "name": "Jane Coder",\n' +
      '        "email": "janecoder@company.com",\n' +
      '        "date": "2015-06-01T20:44:52Z"\n' +
      '      },\n' +
      '      "committer": {\n' +
      '        "name": "Jane Coder",\n' +
      '        "email": "janecoder@company.com",\n' +
      '        "date": "2015-06-01T20:44:52Z"\n' +
      '      },\n' +
      '      "message": "Merge pull request #398 from my-project/deterministic-parallel-stage-id\\n\\nEnsure the initialization stage receives a deterministic stage id",\n' +
      '      "tree": {\n' +
      '        "sha": "36fed076acfd54be79ce31f4f774eddf2a22ef19",\n' +
      '        "url": "https://api.github.com/repos/my-project/module/git/trees/36fed076acfd54be79ce31f4f774eddf2a22ef19"\n' +
      '      },\n' +
      '      "url": "https://api.github.com/repos/my-project/module/git/commits/0a7c0c17992b15c73de25b2a94abb4c88862b53f",\n' +
      '      "comment_count": 0\n' +
      '    },\n' +
      '    "url": "https://api.github.com/repos/my-project/module/commits/0a7c0c17992b15c73de25b2a94abb4c88862b53f",\n' +
      '    "html_url": "https://github.com/my-project/module/commit/0a7c0c17992b15c73de25b2a94abb4c88862b53f",\n' +
      '    "comments_url": "https://api.github.com/repos/my-project/module/commits/0a7c0c17992b15c73de25b2a94abb4c88862b53f/comments",\n' +
      '    "author": {\n' +
      '      "login": "jcoder",\n' +
      '      "id": 388652,\n' +
      '      "avatar_url": "https://avatars.githubusercontent.com/u/11111?v=3",\n' +
      '      "gravatar_id": "",\n' +
      '      "url": "https://api.github.com/users/jcoder",\n' +
      '      "html_url": "https://github.com/jcoder",\n' +
      '      "followers_url": "https://api.github.com/users/jcoder/followers",\n' +
      '      "following_url": "https://api.github.com/users/jcoder/following{/other_user}",\n' +
      '      "gists_url": "https://api.github.com/users/jcoder/gists{/gist_id}",\n' +
      '      "starred_url": "https://api.github.com/users/jcoder/starred{/owner}{/repo}",\n' +
      '      "subscriptions_url": "https://api.github.com/users/jcoder/subscriptions",\n' +
      '      "organizations_url": "https://api.github.com/users/jcoder/orgs",\n' +
      '      "repos_url": "https://api.github.com/users/jcoder/repos",\n' +
      '      "events_url": "https://api.github.com/users/jcoder/events{/privacy}",\n' +
      '      "received_events_url": "https://api.github.com/users/jcoder/received_events",\n' +
      '      "type": "User",\n' +
      '      "site_admin": false\n' +
      '    },\n' +
      '    "committer": {\n' +
      '      "login": "jcoder",\n' +
      '      "id": 388652,\n' +
      '      "avatar_url": "https://avatars.githubusercontent.com/u/11111?v=3",\n' +
      '      "gravatar_id": "",\n' +
      '      "url": "https://api.github.com/users/jcoder",\n' +
      '      "html_url": "https://github.com/jcoder",\n' +
      '      "followers_url": "https://api.github.com/users/jcoder/followers",\n' +
      '      "following_url": "https://api.github.com/users/jcoder/following{/other_user}",\n' +
      '      "gists_url": "https://api.github.com/users/jcoder/gists{/gist_id}",\n' +
      '      "starred_url": "https://api.github.com/users/jcoder/starred{/owner}{/repo}",\n' +
      '      "subscriptions_url": "https://api.github.com/users/jcoder/subscriptions",\n' +
      '      "organizations_url": "https://api.github.com/users/jcoder/orgs",\n' +
      '      "repos_url": "https://api.github.com/users/jcoder/repos",\n' +
      '      "events_url": "https://api.github.com/users/jcoder/events{/privacy}",\n' +
      '      "received_events_url": "https://api.github.com/users/jcoder/received_events",\n' +
      '      "type": "User",\n' +
      '      "site_admin": false\n' +
      '    },\n' +
      '    "parents": [\n' +
      '      {\n' +
      '        "sha": "ca58ebfd0e74370246c328bdb61bceabdf9ea506",\n' +
      '        "url": "https://api.github.com/repos/my-project/module/commits/ca58ebfd0e74370246c328bdb61bceabdf9ea506",\n' +
      '        "html_url": "https://github.com/my-project/module/commit/ca58ebfd0e74370246c328bdb61bceabdf9ea506"\n' +
      '      },\n' +
      '      {\n' +
      '        "sha": "e108baa8022059b68cafe182759b1f224155ff80",\n' +
      '        "url": "https://api.github.com/repos/my-project/module/commits/e108baa8022059b68cafe182759b1f224155ff80",\n' +
      '        "html_url": "https://github.com/my-project/module/commit/e108baa8022059b68cafe182759b1f224155ff80"\n' +
      '      }\n' +
      '    ]\n' +
      '  },\n' +
      '  "merge_base_commit": {\n' +
      '    "sha": "0a7c0c17992b15c73de25b2a94abb4c88862b53f",\n' +
      '    "commit": {\n' +
      '      "author": {\n' +
      '        "name": "Jane Coder",\n' +
      '        "email": "janecoder@company.com",\n' +
      '        "date": "2015-06-01T20:44:52Z"\n' +
      '      },\n' +
      '      "committer": {\n' +
      '        "name": "Jane Coder",\n' +
      '        "email": "janecoder@company.com",\n' +
      '        "date": "2015-06-01T20:44:52Z"\n' +
      '      },\n' +
      '      "message": "Merge pull request #398 from my-project/deterministic-parallel-stage-id\\n\\nEnsure the initialization stage receives a deterministic stage id",\n' +
      '      "tree": {\n' +
      '        "sha": "36fed076acfd54be79ce31f4f774eddf2a22ef19",\n' +
      '        "url": "https://api.github.com/repos/my-project/module/git/trees/36fed076acfd54be79ce31f4f774eddf2a22ef19"\n' +
      '      },\n' +
      '      "url": "https://api.github.com/repos/my-project/module/git/commits/0a7c0c17992b15c73de25b2a94abb4c88862b53f",\n' +
      '      "comment_count": 0\n' +
      '    },\n' +
      '    "url": "https://api.github.com/repos/my-project/module/commits/0a7c0c17992b15c73de25b2a94abb4c88862b53f",\n' +
      '    "html_url": "https://github.com/my-project/module/commit/0a7c0c17992b15c73de25b2a94abb4c88862b53f",\n' +
      '    "comments_url": "https://api.github.com/repos/my-project/module/commits/0a7c0c17992b15c73de25b2a94abb4c88862b53f/comments",\n' +
      '    "author": {\n' +
      '      "login": "jcoder",\n' +
      '      "id": 388652,\n' +
      '      "avatar_url": "https://avatars.githubusercontent.com/u/11111?v=3",\n' +
      '      "gravatar_id": "",\n' +
      '      "url": "https://api.github.com/users/jcoder",\n' +
      '      "html_url": "https://github.com/jcoder",\n' +
      '      "followers_url": "https://api.github.com/users/jcoder/followers",\n' +
      '      "following_url": "https://api.github.com/users/jcoder/following{/other_user}",\n' +
      '      "gists_url": "https://api.github.com/users/jcoder/gists{/gist_id}",\n' +
      '      "starred_url": "https://api.github.com/users/jcoder/starred{/owner}{/repo}",\n' +
      '      "subscriptions_url": "https://api.github.com/users/jcoder/subscriptions",\n' +
      '      "organizations_url": "https://api.github.com/users/jcoder/orgs",\n' +
      '      "repos_url": "https://api.github.com/users/jcoder/repos",\n' +
      '      "events_url": "https://api.github.com/users/jcoder/events{/privacy}",\n' +
      '      "received_events_url": "https://api.github.com/users/jcoder/received_events",\n' +
      '      "type": "User",\n' +
      '      "site_admin": false\n' +
      '    },\n' +
      '    "committer": {\n' +
      '      "login": "jcoder",\n' +
      '      "id": 388652,\n' +
      '      "avatar_url": "https://avatars.githubusercontent.com/u/11111?v=3",\n' +
      '      "gravatar_id": "",\n' +
      '      "url": "https://api.github.com/users/jcoder",\n' +
      '      "html_url": "https://github.com/jcoder",\n' +
      '      "followers_url": "https://api.github.com/users/jcoder/followers",\n' +
      '      "following_url": "https://api.github.com/users/jcoder/following{/other_user}",\n' +
      '      "gists_url": "https://api.github.com/users/jcoder/gists{/gist_id}",\n' +
      '      "starred_url": "https://api.github.com/users/jcoder/starred{/owner}{/repo}",\n' +
      '      "subscriptions_url": "https://api.github.com/users/jcoder/subscriptions",\n' +
      '      "organizations_url": "https://api.github.com/users/jcoder/orgs",\n' +
      '      "repos_url": "https://api.github.com/users/jcoder/repos",\n' +
      '      "events_url": "https://api.github.com/users/jcoder/events{/privacy}",\n' +
      '      "received_events_url": "https://api.github.com/users/jcoder/received_events",\n' +
      '      "type": "User",\n' +
      '      "site_admin": false\n' +
      '    },\n' +
      '    "parents": [\n' +
      '      {\n' +
      '        "sha": "ca58ebfd0e74370246c328bdb61bceabdf9ea506",\n' +
      '        "url": "https://api.github.com/repos/my-project/module/commits/ca58ebfd0e74370246c328bdb61bceabdf9ea506",\n' +
      '        "html_url": "https://github.com/my-project/module/commit/ca58ebfd0e74370246c328bdb61bceabdf9ea506"\n' +
      '      },\n' +
      '      {\n' +
      '        "sha": "e108baa8022059b68cafe182759b1f224155ff80",\n' +
      '        "url": "https://api.github.com/repos/my-project/module/commits/e108baa8022059b68cafe182759b1f224155ff80",\n' +
      '        "html_url": "https://github.com/my-project/module/commit/e108baa8022059b68cafe182759b1f224155ff80"\n' +
      '      }\n' +
      '    ]\n' +
      '  },\n' +
      '  "status": "ahead",\n' +
      '  "ahead_by": 2,\n' +
      '  "behind_by": 0,\n' +
      '  "total_commits": 2,\n' +
      '  "commits": [\n' +
      '    {\n' +
      '      "sha": "83bdadb570db40cab995e1f402dea2b096d0c1a1",\n' +
      '      "commit": {\n' +
      '        "author": {\n' +
      '          "name": "Joe Coder",\n' +
      '          "email": "joecoder@company.com",\n' +
      '          "date": "2015-06-01T20:53:35Z"\n' +
      '        },\n' +
      '        "committer": {\n' +
      '          "name": "Joe Coder",\n' +
      '          "email": "joecoder@company.com",\n' +
      '          "date": "2015-06-01T20:53:35Z"\n' +
      '        },\n' +
      '        "message": "bug fix",\n' +
      '        "tree": {\n' +
      '          "sha": "9804641ea18f9dcba331ffacdc654578be611e72",\n' +
      '          "url": "https://api.github.com/repos/my-project/module/git/trees/9804641ea18f9dcba331ffacdc654578be611e72"\n' +
      '        },\n' +
      '        "url": "https://api.github.com/repos/my-project/module/git/commits/83bdadb570db40cab995e1f402dea2b096d0c1a1",\n' +
      '        "comment_count": 0\n' +
      '      },\n' +
      '      "url": "https://api.github.com/repos/my-project/module/commits/83bdadb570db40cab995e1f402dea2b096d0c1a1",\n' +
      '      "html_url": "https://github.com/my-project/module/commit/83bdadb570db40cab995e1f402dea2b096d0c1a1",\n' +
      '      "comments_url": "https://api.github.com/repos/my-project/module/commits/83bdadb570db40cab995e1f402dea2b096d0c1a1/comments",\n' +
      '      "author": null,\n' +
      '      "committer": null,\n' +
      '      "parents": [\n' +
      '        {\n' +
      '          "sha": "0a7c0c17992b15c73de25b2a94abb4c88862b53f",\n' +
      '          "url": "https://api.github.com/repos/my-project/module/commits/0a7c0c17992b15c73de25b2a94abb4c88862b53f",\n' +
      '          "html_url": "https://github.com/my-project/module/commit/0a7c0c17992b15c73de25b2a94abb4c88862b53f"\n' +
      '        }\n' +
      '      ]\n' +
      '    },\n' +
      '    {\n' +
      '      "sha": "7890bc148475432b9e537e03d37f22d9018ef9c8",\n' +
      '      "commit": {\n' +
      '        "author": {\n' +
      '          "name": "Joe Coder",\n' +
      '          "email": "joecoder@company.com",\n' +
      '          "date": "2015-06-01T20:58:01Z"\n' +
      '        },\n' +
      '        "committer": {\n' +
      '          "name": "Joe Coder",\n' +
      '          "email": "joecoder@company.com",\n' +
      '          "date": "2015-06-01T20:58:01Z"\n' +
      '        },\n' +
      '        "message": "new feature",\n' +
      '        "tree": {\n' +
      '          "sha": "4467002945b6cd53132e7e185f91c33e288b854d",\n' +
      '          "url": "https://api.github.com/repos/my-project/module/git/trees/4467002945b6cd53132e7e185f91c33e288b854d"\n' +
      '        },\n' +
      '        "url": "https://api.github.com/repos/my-project/module/git/commits/7890bc148475432b9e537e03d37f22d9018ef9c8",\n' +
      '        "comment_count": 0\n' +
      '      },\n' +
      '      "url": "https://api.github.com/repos/my-project/module/commits/7890bc148475432b9e537e03d37f22d9018ef9c8",\n' +
      '      "html_url": "https://github.com/my-project/module/commit/7890bc148475432b9e537e03d37f22d9018ef9c8",\n' +
      '      "comments_url": "https://api.github.com/repos/my-project/module/commits/7890bc148475432b9e537e03d37f22d9018ef9c8/comments",\n' +
      '      "author": null,\n' +
      '      "committer": null,\n' +
      '      "parents": [\n' +
      '        {\n' +
      '          "sha": "83bdadb570db40cab995e1f402dea2b096d0c1a1",\n' +
      '          "url": "https://api.github.com/repos/my-project/module/commits/83bdadb570db40cab995e1f402dea2b096d0c1a1",\n' +
      '          "html_url": "https://github.com/my-project/module/commit/83bdadb570db40cab995e1f402dea2b096d0c1a1"\n' +
      '        }\n' +
      '      ]\n' +
      '    }\n' +
      '  ],\n' +
      '  "files": [\n' +
      '    {\n' +
      '      "sha": "e90c98aad552c2827f3a2465ec383db5a8690805",\n' +
      '      "filename": "gradle.properties",\n' +
      '      "status": "modified",\n' +
      '      "additions": 1,\n' +
      '      "deletions": 1,\n' +
      '      "changes": 2,\n' +
      '      "blob_url": "https://github.com/my-project/module/blob/7890bc148475432b9e537e03d37f22d9018ef9c8/gradle.properties",\n' +
      '      "raw_url": "https://github.com/my-project/module/raw/7890bc148475432b9e537e03d37f22d9018ef9c8/gradle.properties",\n' +
      '      "contents_url": "https://api.github.com/repos/my-project/module/contents/gradle.properties?ref=7890bc148475432b9e537e03d37f22d9018ef9c8",\n' +
      '      "patch": "@@ -1,2 +1,2 @@\\n-version=0.308-SNAPSHOT\\n+version=0.309-SNAPSHOT\\n "\n' +
      '    }\n' +
      '  ]\n' +
      '}'
  }
}
