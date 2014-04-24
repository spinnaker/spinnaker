package com.netflix.front50

import com.amazonaws.services.simpledb.AmazonSimpleDB
import com.amazonaws.services.simpledb.model.Item
import com.amazonaws.services.simpledb.model.SelectRequest
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

    private Application mapToApp(Item item) {
        Map<String, String> map = item.attributes.collectEntries { [it.name, it.value] }
        map['name'] = item.name
        return new Application(map)
    }

    private List<Item> query(String query) {
        awsClient.select(new SelectRequest(query)).getItems()
    }

}
