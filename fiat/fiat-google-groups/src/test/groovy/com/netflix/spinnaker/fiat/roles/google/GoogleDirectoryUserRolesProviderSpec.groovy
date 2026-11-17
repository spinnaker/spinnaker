package com.netflix.spinnaker.fiat.roles.google

import com.google.api.client.http.CreateFakeHttpResponse
import com.google.api.client.http.HttpResponse
import com.google.api.client.testing.http.HttpTesting
import com.google.api.client.testing.http.MockHttpTransport
import com.google.api.client.testing.http.MockLowLevelHttpResponse
import com.google.auth.oauth2.GoogleCredentials
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.api.services.directory.DirectoryScopes
import com.google.api.services.directory.model.Group
import com.google.api.services.directory.model.Groups
import com.netflix.spinnaker.fiat.model.resources.Role
import com.netflix.spinnaker.fiat.permissions.ExternalUser
import spock.lang.Specification
import spock.lang.Unroll

class GoogleDirectoryUserRolesProviderSpec extends Specification {
  GoogleDirectoryUserRolesProvider.Config config = new GoogleDirectoryUserRolesProvider.Config()

  @Unroll
  def "should use scoped ADC without delegation when adminUsername is '#adminUsername'"() {
    given:
    config.domain = "example.com"
    config.adminUsername = adminUsername
    // Direct mode must not attempt to open a configured key file.
    config.credentialPath = "/nonexistent/unused-key.json"
    def credentials = Mock(GoogleCredentials)
    def scoped = Mock(GoogleCredentials)
    def provider = new GoogleDirectoryUserRolesProvider() {
      @Override
      protected GoogleCredentials getApplicationDefaultCredentials() {
        return credentials
      }
    }
    provider.setConfig(config)

    when:
    provider.afterPropertiesSet()
    def result = provider.getGoogleCredential()

    then:
    1 * credentials.createScoped(Collections.singleton(DirectoryScopes.ADMIN_DIRECTORY_GROUP_READONLY)) >> scoped
    0 * scoped.createDelegated(_)
    result.is(scoped)

    where:
    adminUsername << [null, "", "  "]
  }

  def "should delegate service-account ADC when adminUsername is set"() {
    given:
    config.adminUsername = "admin@example.com"
    def credentials = Mock(ServiceAccountCredentials)
    def scoped = Mock(ServiceAccountCredentials)
    def delegated = Mock(ServiceAccountCredentials)
    def provider = new GoogleDirectoryUserRolesProvider() {
      @Override
      protected GoogleCredentials getApplicationDefaultCredentials() {
        return credentials
      }
    }
    provider.setConfig(config)

    when:
    def result = provider.getGoogleCredential()

    then:
    1 * credentials.createScoped(Collections.singleton(DirectoryScopes.ADMIN_DIRECTORY_GROUP_READONLY)) >> scoped
    1 * scoped.createDelegated("admin@example.com") >> delegated
    result.is(delegated)
  }

  def "should reject unsupported delegation instead of silently using ADC directly"() {
    given:
    config.adminUsername = "admin@example.com"
    def credentials = Mock(GoogleCredentials)
    def scoped = Mock(GoogleCredentials)
    credentials.createScoped(_) >> scoped
    def provider = new GoogleDirectoryUserRolesProvider() {
      @Override
      protected GoogleCredentials getApplicationDefaultCredentials() {
        return credentials
      }
    }
    provider.setConfig(config)

    when:
    provider.getGoogleCredential()

    then:
    def error = thrown(IllegalStateException)
    error.message.contains("adminUsername requires credentials supporting domain-wide delegation")
  }

  def "should read google groups"() {
    setup:
    config.domain = "test.com"
    def group = new Group()
    group.set("email", "test@test.com")
    group.set("name", "test")
    List<Group> groupList = new ArrayList<>()
    groupList.add(group)
    def groups = new Groups()
    groups.setGroups(groupList)

    GoogleDirectoryUserRolesProvider provider = new GoogleDirectoryUserRolesProvider() {
      @Override
      Groups getGroupsFromEmailRecursively(String email) {
        return groups
      }
    }

    provider.setProperty("config", config)

    when:
    def result1 = provider.loadRoles(ExternalUser.forId("testuser"))

    then:
    result1.name.containsAll(["test"])
    result1.name.size() == 1

    when:
    config.roleSources = [GoogleDirectoryUserRolesProvider.Config.RoleSource.EMAIL, GoogleDirectoryUserRolesProvider.Config.RoleSource.NAME]
    def result2 = provider.loadRoles(ExternalUser.forId("testuser"))

    then:
    result2.name.containsAll(["test@test.com", "test"])
    result2.name.size() == 2

    when:
    config.roleSources = [GoogleDirectoryUserRolesProvider.Config.RoleSource.EMAIL]
    def result3 = provider.loadRoles(ExternalUser.forId("testuser"))

    then:
    result3.name.containsAll(["test@test.com"])
    result3.name.size() == 1

    when:
    //test that a null name does not break the resolution
    group.setName(null)
    groupList.clear()
    groupList.add(group)
    def result4 = provider.loadRoles(ExternalUser.forId("testuser"))

    then:
    result4.name.containsAll(["test@test.com"])
    result4.name.size() == 1

    when:
    //test that a service account does not get groups returned
    def result5 = provider.loadRoles(ExternalUser.forId("testuser@managed-service-account"))

    then:
    result5.name.size() == 0

    when:
    //test that a shared service account does not get groups returned
    def result6 = provider.loadRoles(ExternalUser.forId("testuser@shared-managed-service-account"))

    then:
    result6.name.size() == 0
  }

  @Unroll
  def "should recursively collect all nested groups if expandIndirectGroups is #expandIndirectGroups"() {
    given:
    config.expandIndirectGroups = expandIndirectGroups
    def provider = Spy(GoogleDirectoryUserRolesProvider) {
      getGroupsFromEmail("root@example.com") >> new Groups(groups: [
        new Group(email: "child1@example.com"),
        new Group(email: "child2@example.com")
      ])
      getGroupsFromEmail("child1@example.com") >> new Groups(groups: [
        new Group(email: "grandchild1@example.com")
      ])
      getGroupsFromEmail("child2@example.com") >> new Groups(groups: [
        new Group(email: "grandchild2@example.com"),
        new Group(email: "child1@example.com")
      ])
      getGroupsFromEmail("grandchild1@example.com") >> new Groups(groups: [])
      getGroupsFromEmail("grandchild2@example.com") >> null
    }
    provider.setConfig(config)

    when:
    def result = provider.getGroupsFromEmailRecursively("root@example.com")

    then:
    result.groups*.email.containsAll(groupsContent)
    result.groups.size() == totalEmails

    where:
    expandIndirectGroups | totalEmails | groupsContent
    true                 | 4           | ["child1@example.com", "child2@example.com", "grandchild1@example.com", "grandchild2@example.com"]
    false                | 2           | ["child1@example.com", "child2@example.com"]

  }

  @Unroll
  def "should handle a case where groups is null for some unknown reason"() {
    given:
    config.expandIndirectGroups = expandIndirectGroups
    def provider = Spy(GoogleDirectoryUserRolesProvider) {
      getGroupsFromEmail("root@example.com") >> new Groups(groups: null)
    }
    provider.setConfig(config)

    when:
    def result = provider.loadRoles(ExternalUser.forId("root@example.com"))

    then:
    result.size() == totalEmails
    result.stream().map { it.getName()}.toList().containsAll(groupsContent)

    where:
    expandIndirectGroups | totalEmails | groupsContent
    true                 | 0           | List.of()
    false                | 0           | List.of()

  }


  @Unroll
  def "should handle a case where groups is null for some unknown reason when multi loading roles"() {
    given:
    config.expandIndirectGroups = expandIndirectGroups
    def provider = Spy(GoogleDirectoryUserRolesProvider) {
      getGroupsFromEmailRecursively("root@example.com") >> new Groups(groups: null)
    }
    provider.setConfig(config)

    when:
    // to note hitting roles for email instead of multiloadroles... b/c Mock objects with fork join pools don't work so well together
    def result = provider.getRolesForEmail("root@example.com")

    then:
    result.size() == totalEmails
    result.stream().map { it.getName()}.toList().containsAll(groupsContent)

    where:
    expandIndirectGroups | totalEmails | groupsContent
    true                 | 0           | List.of()
    false                | 0           | List.of()

  }



  @Unroll
  def "verify that 403 and 429 and 500 errors trigger a retry"() {
    given:
    def retryHandler = new GoogleDirectoryUserRolesProvider().getGCPBackoffHandlerForGoogleApiRateLImitedCalls();

     when:
      def httpResponse = CreateFakeHttpResponse.create(mockResponseCode)
      httpResponse.getStatusCode() >> mockResponseCode
    then:
    retryHandler.getBackOffRequired().isRequired(httpResponse) == isRequired
    where:
    mockResponseCode | isRequired
    403              | true
    429              | true
    500              | true
    200              | false
  }

  @Unroll
  def "should parallel load groups as needed"() {
    given:
    config.expandIndirectGroups = expandIndirectGroups
    config.groupLookupConcurrency = 4
    def provider = new GoogleDirectoryUserRolesProvider() {
      // Move from spy to  a real overload due to thread hnadling.
      @Override
      protected Groups getGroupsFromEmail(String email) throws IOException {
        switch (email) {
          case "root@example.com":
            return new Groups(groups: [
              new Group(email: "child1@example.com"),
              new Group(email: "child2@example.com")
            ])
          case "child1@example.com":
            return new Groups(groups: [
              new Group(email: "grandchild1@example.com"),
              new Group(name: "grandchild22")
            ])
          case "child2@example.com":
            return new Groups(groups: [
              new Group(email: "grandchild2@example.com"),
              new Group(email: "child1@example.com")
            ])
          case "grandchild1@example.com":
            return new Groups(groups: [new Group(name: "test1")])
          case "user2@example.com":
            return new Groups(groups: [new Group(name: "test2")])
          case "user3@example.com":
            return new Groups(groups: [new Group(name: "test3")])
          default:
            return null;
        }
      }
    }
    provider.setConfig(config)

    when:
    def result = provider.multiLoadRoles(Set.of(ExternalUser.forId("root@example.com"), ExternalUser.forId("user2@example.com"), ExternalUser.forId("user3@example.com")));

    then:
    result.get(emailAddress).containsAll(groupsContent)

    where:
    expandIndirectGroups | emailAddress        | groupsContent
    true                 | "root@example.com"  | [new Role("test1"), new Role("grandchild22")]
    true                 | "user2@example.com" | [new Role("test2")]
    true                 | "user3@example.com" | [new Role("test3")]

  }


}
