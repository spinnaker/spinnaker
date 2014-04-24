package com.netflix.front50

import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.front50.exception.NotFoundException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Configurable
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

/**
 * Created by aglover on 4/20/14.
 */
@Component
@Configurable
class Application {
    String name
    String description
    String email
    String owner
    String type
    String group
    String monitorBucketType
    String pdApiKey
    String updateTs
    String createTs

    @JsonIgnore
    @Autowired
    @Qualifier("SimpleDB")
    ApplicationDAO dao

    Application() {} //forces Groovy to add LinkedHashMap constructor

    Application(String name, String description, String email, String owner, String type,
                String group, String monitorBucketType, String pdApiKey, long createdAt, long updatedAt) {
        this.group = group
        this.monitorBucketType = monitorBucketType
        this.pdApiKey = pdApiKey
        this.name = name
        this.description = description
        this.email = email
        this.owner = owner
        this.type = type
        this.createTs = createdAt
        this.updateTs = updatedAt
    }

    Collection<Application> findAll() throws NotFoundException {
        return dao.all()
    }

    Application findByName(String name) throws NotFoundException {
        return dao.findByName(name)
    }
}
