package com.netflix.spinnaker.fiat.roles

import com.netflix.spinnaker.fiat.config.UserRolesProviderCacheConfig
import com.netflix.spinnaker.fiat.model.resources.Role
import com.netflix.spinnaker.fiat.permissions.ExternalUser
import com.netflix.spinnaker.fiat.providers.ProviderException
import spock.lang.Specification

class BaseUserRolesProviderSpec extends Specification {

    /** Simple implementation of {@link BaseUserRolesProvider} for testing of loading cache semantics. */
    class TestBaseUserRolesProvider extends BaseUserRolesProvider {

        /** Returns {@code true} if caching is enabled. */
        protected boolean checkCacheEnabled() {
            return cacheEnabled;
        }

        /**
         * Returns the number of active entries in the cache. This method is intended for testing purposes
         * only as a cache clean up is manually invoked prior to the size calculation. If caching is not
         * enabled then returns -1.
         *
         * @return Size of the cache or -1 if caching is not enabled.
         */
        protected long size() {
            if (cacheEnabled) {
                loadingCache.cleanUp();
                return loadingCache.size();
            } else {
                return -1L;
            }
        }

        @Override
        protected List<Role> loadRolesForUser(ExternalUser user) throws ProviderException {
            return loadRolesForUserDelegate(user);
        }

        /**
         * Support mocking of returned responses by the test layer.
         * This method added to avoid cases where explicit mock of {@link #loadRolesForUser} was not registering with Spock.
         */
        public List<Role> loadRolesForUserDelegate(ExternalUser user) throws ProviderException {
            return Collections.emptyList();
        }
    }

    private UserRolesProviderCacheConfig baseCacheConfig() {
        return baseCacheConfig(true, 100)
    }

    private UserRolesProviderCacheConfig baseCacheConfig(boolean cacheEnabled, int expireAfterWriteSeconds) {
        def cacheConfig = new UserRolesProviderCacheConfig()
        cacheConfig.setEnabled(cacheEnabled)
        cacheConfig.setExpireAfterWriteSeconds(expireAfterWriteSeconds)

        return cacheConfig
    }

    private ExternalUser externalUser(String id) {
        return new ExternalUser().setId(id)
    }

    void "basic cache load - load()"() {
        given:
        def eu1 = externalUser("user1")
        def role1 = new Role("group1")

        def cacheConfig = baseCacheConfig()
        def provider = Spy(TestBaseUserRolesProvider) {
            loadRolesForUserDelegate(eu1) >> [role1]
        }
        provider.setProviderCacheConfig(cacheConfig)

        when:
        def roles = provider.loadRoles(eu1)

        then:
        roles == [role1]
        1 * provider.loadRolesForUser(eu1)
        provider.size() == 1
    }

    void "basic cache load - loadAll()"() {
        given:
        def eu1 = externalUser("user1")
        def eu2 = externalUser("user2")
        def users = [eu1, eu2]

        def role1 = new Role("group1")
        def role2 = new Role("group2")

        def cacheConfig = baseCacheConfig()
        def provider = Spy(TestBaseUserRolesProvider) {
            loadRolesForUserDelegate(eu1) >> [role1]
            loadRolesForUserDelegate(eu2) >> [role2]
        }
        provider.setProviderCacheConfig(cacheConfig)

        when:
        def roles = provider.multiLoadRoles(users)

        then:
        roles == [user1: [role1], user2: [role2]]

        // loadAll() called with all users.
        1 * provider.loadRolesForUsers(_)

        // load() called once per user by loadAll().
        1 * provider.loadRolesForUser(eu1)
        1 * provider.loadRolesForUser(eu2)

        // Cache is not empty.
        provider.size() == users.size()
    }

    void "cache returns previously calculated entries by only calling loadAll() once"() {
        given:
        def users = [externalUser("user1"), externalUser("user2")]
        def role1 = new Role("group1")
        def role2 = new Role("group2")

        def cacheConfig = baseCacheConfig()
        def provider = Spy(TestBaseUserRolesProvider){
            loadRolesForUserDelegate(_ as ExternalUser) >>> [[role1], [role2]]
        }
        provider.setProviderCacheConfig(cacheConfig)

        // Load the cache.
        when:
        def roles = provider.multiLoadRoles(users)

        then:
        roles == [user1: [role1], user2: [role2]]
        1 * provider.loadRolesForUsers(users)
        provider.size() == users.size()

        // Return cached entries, i.e. no cache loading involved.
        when:
        roles = provider.multiLoadRoles(users)

        then:
        roles == [user1: [role1], user2: [role2]]
        0 * provider.loadRolesForUsers(_)
        provider.size() == users.size()
    }

    void "cache expires entries based on write time"() {
        given:
        def users = [externalUser("user1"), externalUser("user2")]

        int expireAfterWriteSeconds = 1
        def cacheConfig = baseCacheConfig(true, expireAfterWriteSeconds)
        def provider = Spy(TestBaseUserRolesProvider)
        provider.setProviderCacheConfig(cacheConfig)

        // Cache is loaded.
        def roles = provider.multiLoadRoles(users)

        // Cached entries still valid prior to expiry.
        when:
        sleep((long) 0.9 * 1000 * expireAfterWriteSeconds)

        then:
        provider.size() == users.size()

        // Cached entries invalid after expiry.
        when:
        sleep((long) 1.0 * 1000 * expireAfterWriteSeconds)

        then:
        provider.size() == 0

        // Cache is reloaded for expired entries.
        when:
        roles = provider.multiLoadRoles(users)

        then:
        roles == [user1: [], user2: []]
        1 * provider.loadRolesForUsers(users)
        provider.size() == users.size()
    }

    void "multiLoadRoles() only calls loadAll() for users not already in the cache"() {
        given:
        def eu1 = externalUser("user1")
        def eu2 = externalUser("user2")
        def users1 = [eu1, eu2]

        def eu3 = externalUser("user3")
        def eu4 = externalUser("user4")
        def users2 = [eu3, eu4]

        def usersAll = [eu1, eu2, eu3, eu4]

        def role1 = new Role("group1")
        def role2 = new Role("group2")
        def role3 = new Role("group3")
        def role4 = new Role("group4")

        def cacheConfig = baseCacheConfig()
        def provider = Spy(TestBaseUserRolesProvider){
            loadRolesForUserDelegate(_ as ExternalUser) >>> [[role1], [role2], [role3], [role4]]
        }
        provider.setProviderCacheConfig(cacheConfig)

        // Cache is loaded with the first group of users only.
        when:
        def roles = provider.multiLoadRoles(users1)

        then:
        roles == [user1: [role1], user2: [role2]]
        1 * provider.loadRolesForUsers(users1)
        provider.size() == 2

        // Cache is loaded with only the second group of users when multiLoadRoles() for all users performed.
        when:
        roles = provider.multiLoadRoles(usersAll)

        then:
        roles == [user1: [role1], user2: [role2], user3: [role3], user4: [role4]]
        // Existing users in the cache are not reloaded.
        0 * provider.loadRolesForUsers(users1)
        // loadAll() is only called for users not yet in the cache.
        1 * provider.loadRolesForUsers(users2)
        provider.size() == 4
    }

    void "loadRoles() returns already cached entries when available"() {
        given:
        def eu1 = externalUser("user1")
        def eu2 = externalUser("user2")
        def users1 = [eu1, eu2]

        def eu3 = externalUser("user3")

        def role1 = new Role("group1")
        def role2 = new Role("group2")
        def role3 = new Role("group3")

        def cacheConfig = baseCacheConfig(true, 1000)
        def provider = Spy(TestBaseUserRolesProvider) {
            loadRolesForUserDelegate(eu1) >> [role1]
            loadRolesForUserDelegate(eu2) >> [role2]
            loadRolesForUserDelegate(eu3) >> [role3]
        }
        provider.setProviderCacheConfig(cacheConfig)

        // Cache is loaded with the first group of users only.
        when:
        def roles = provider.multiLoadRoles(users1)

        then:
        roles == [user1: [role1], user2: [role2]]
        1 * provider.loadRolesForUsers(users1)
        1 * provider.loadRolesForUser(eu1)
        1 * provider.loadRolesForUser(eu2)
        provider.size() == 2

        // loadRoles() for single, non-cached user loads that user explicitly.
        when:
        def singleUserRoles = provider.loadRoles(eu3)

        then:
        singleUserRoles == [role3]
        0 * provider.loadRolesForUsers(_)
        1 * provider.loadRolesForUser(eu3)
        provider.size() == 3

        // loadRoles() for already cached users returns cached values.
        when:
        def singleUserRoles1 = provider.loadRoles(eu1)
        def singleUserRoles2 = provider.loadRoles(eu2)
        def singleUserRoles3 = provider.loadRoles(eu3)

        then:
        singleUserRoles1 == [role1]
        singleUserRoles2 == [role2]
        singleUserRoles3 == [role3]
        0 * provider.loadRolesForUsers(_)
        0 * provider.loadRolesForUser(eu1)
        0 * provider.loadRolesForUser(eu2)
        0 * provider.loadRolesForUser(eu3)
        provider.size() == 3
    }

    void "cache invalidation"() {
        given:
        def eu1 = externalUser("user1")
        def eu2 = externalUser("user2")
        def eu3 = externalUser("user3")
        def eu4 = externalUser("user4")

        def usersAll = [eu1, eu2, eu3, eu4]

        def role1 = new Role("group1")
        def role2 = new Role("group2")
        def role3 = new Role("group3")
        def role4 = new Role("group4")

        def cacheConfig = baseCacheConfig()
        def provider = Spy(TestBaseUserRolesProvider){
            loadRolesForUserDelegate(eu1) >> [role1]
            loadRolesForUserDelegate(eu2) >> [role2]
            loadRolesForUserDelegate(eu3) >> [role3]
            loadRolesForUserDelegate(eu4) >> [role4]
        }
        provider.setProviderCacheConfig(cacheConfig)

        // Cache is loaded with all users.
        when:
        def roles = provider.multiLoadRoles(usersAll)

        then:
        roles == [user1: [role1], user2: [role2], user3: [role3], user4: [role4]]
        1 * provider.loadRolesForUsers(usersAll)
        provider.size() == 4

        // Invalidate a single user.
        when:
        provider.invalidate(eu1)
        roles = provider.multiLoadRoles([eu2, eu3, eu4])

        then:
        roles == [user2: [role2], user3: [role3], user4: [role4]]
        0 * provider.loadRolesForUsers(_)
        0 * provider.loadRolesForUser(_)
        provider.size() == 3

        // Invalidate multiple users.
        when:
        provider.invalidate([eu2, eu3])
        roles = provider.multiLoadRoles([eu4])

        then:
        roles == [user4: [role4]]
        0 * provider.loadRolesForUsers(_)
        0 * provider.loadRolesForUser(_)
        provider.size() == 1

        // Invalidate all users.
        when:
        provider.invalidateAll()

        then:
        provider.size() == 0

        // Cache is properly reloaded with all users.
        when:
        roles = provider.multiLoadRoles(usersAll)

        then:
        roles == [user1: [role1], user2: [role2], user3: [role3], user4: [role4]]
        1 * provider.loadRolesForUsers(usersAll)
        provider.size() == 4
    }

    void "cache not enabled"() {
        given:
        def eu1 = externalUser("user1")
        def eu2 = externalUser("user2")
        def users = [eu1, eu2]

        def role1 = new Role("group1")
        def role2 = new Role("group2")

        def cacheConfig = baseCacheConfig(false, 100)
        def provider = Spy(TestBaseUserRolesProvider) {
            loadRolesForUserDelegate(eu1) >> [role1]
            loadRolesForUserDelegate(eu2) >> [role2]
        }
        provider.setProviderCacheConfig(cacheConfig)

        when:
        def roles = provider.multiLoadRoles(users)

        then:
        roles == [user1: [role1], user2: [role2]]

        // loadRolesForUsers() called with all users.
        1 * provider.loadRolesForUsers(_)

        // loadRolesForUser() called once per user by loadRolesForUsers().
        1 * provider.loadRolesForUser(eu1)
        1 * provider.loadRolesForUser(eu2)

        // Cache is not enabled.
        provider.checkCacheEnabled() == false
        provider.size() == -1L

        when:
        roles = provider.multiLoadRoles(users)

        then:
        roles == [user1: [role1], user2: [role2]]

        // loadRolesForUsers() called with all users; nothing in the previous response was cached.
        1 * provider.loadRolesForUsers(_)

        // load() called once per user by loadAll(); nothing in the previous response was cached.
        1 * provider.loadRolesForUser(eu1)
        1 * provider.loadRolesForUser(eu2)

        // Cache is not enabled.
        provider.size() == -1L
    }
}
