package com.netflix.front50

import com.amazonaws.services.simpledb.AmazonSimpleDB
import com.amazonaws.services.simpledb.model.*
import com.netflix.front50.exception.NotFoundException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

/**
 * Created by aglover on 4/22/14.
 */
@Qualifier("SimpleDB")
@Component
class SimpleDBDAO implements ApplicationDAO {

    @Autowired
    AmazonSimpleDB awsClient

    final String DOMAIN = "RESOURCE_REGISTRY"

    @Override
    boolean isHealthly() {
       (this.awsClient == null || listDomains().size() <= 0) ? false : true
    }

    private List<String> listDomains() {
        awsClient.listDomains(new ListDomainsRequest().withMaxNumberOfDomains(1)).getDomainNames()
    }

    @Override
    Application create(String id, Map<String, String> properties) {
        properties['createTs'] = System.currentTimeMillis() as String
        awsClient.putAttributes(new PutAttributesRequest().withDomainName(DOMAIN).
                withItemName(id).withAttributes(buildAttributes(properties, false)))
        Application application = new Application(properties)
        application.name = id
        return application
    }

    @Override
    void delete(String id) {
        awsClient.deleteAttributes(
                new DeleteAttributesRequest().withDomainName(DOMAIN).withItemName(id))
    }

    @Override
    void update(String id, Map<String, String> properties) {
        properties['updateTs'] = System.currentTimeMillis() as String
        awsClient.putAttributes(new PutAttributesRequest().withDomainName(DOMAIN).
                withItemName(id).withAttributes(buildAttributes(properties, true)))
    }

    @Override
    Application findByName(String name) {
        def items = query "select * from `${DOMAIN}` where itemName()='${name}'"
        if (items.size() > 0) {
            return mapToApp(items[0])
        } else {
            throw new NotFoundException("No Application found by name of ${name} in domain ${DOMAIN}")
        }
    }

    @Override
    List<Application> all() {
        def items = query "select * from `${DOMAIN}` limit 2500"
        if (items.size() > 0) {
            return items.collect { mapToApp(it) }
        } else {
            throw new NotFoundException("No Applications found in domain ${DOMAIN}")
        }
    }

    Collection<ReplaceableAttribute> buildAttributes(def properties, boolean replace) {
        properties.collectMany { key, value -> [new ReplaceableAttribute(key, value, replace)] }
    }

    private Application mapToApp(Item item) {
        Map<String, String> map = item.attributes.collectEntries { [it.name, it.value] }
        map['name'] = item.name
        return new Application(map)
    }

    private List<Item> query(String query) {
        awsClient.select(new SelectRequest(query)).getItems()
    }
}
