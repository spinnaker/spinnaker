package com.netflix.spinnaker.fiat.roles.google

import com.netflix.spinnaker.fiat.permissions.ExternalUser
import com.google.api.services.admin.directory.model.Group;
import com.google.api.services.admin.directory.model.Groups;
import spock.lang.Specification

class GoogleDirectoryUserRolesProviderSpec extends Specification {
    GoogleDirectoryUserRolesProvider.Config config = new GoogleDirectoryUserRolesProvider.Config()

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
            Groups getGroupsFromEmail(String email) {
                return groups
            }
        }

        provider.setProperty("config", config)

        when:
        def result1 = provider.loadRoles(externalUser("testuser"))

        then:
        result1.name.containsAll(["test"])
        result1.name.size() == 1

        when:
        config.roleSources = [GoogleDirectoryUserRolesProvider.Config.RoleSource.EMAIL, GoogleDirectoryUserRolesProvider.Config.RoleSource.NAME]
        def result2 = provider.loadRoles(externalUser("testuser"))

        then:
        result2.name.containsAll(["test@test.com", "test"])
        result2.name.size() == 2

        when:
        config.roleSources = [GoogleDirectoryUserRolesProvider.Config.RoleSource.EMAIL]
        def result3 = provider.loadRoles(externalUser("testuser"))

        then:
        result3.name.containsAll(["test@test.com"])
        result3.name.size() == 1

        when:
        //test that a null name does not break the resolution
        group.setName(null)
        groupList.clear()
        groupList.add(group)
        def result4 = provider.loadRoles(externalUser("testuser"))

        then:
        result4.name.containsAll(["test@test.com"])
        result4.name.size() == 1



    }

    private static ExternalUser externalUser(String id) {
        return new ExternalUser().setId(id)
    }
}
