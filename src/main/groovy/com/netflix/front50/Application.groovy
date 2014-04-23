package com.netflix.front50

import com.fasterxml.jackson.annotation.JsonIgnore

/**
 * Created by aglover on 4/20/14.
 */
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
    static ApplicationDAO dao

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

    static Collection<Application> findAll() {
        return dao.all()
    }

    static Application findByName(String name) {
        return dao.findByName(name)
    }
}
