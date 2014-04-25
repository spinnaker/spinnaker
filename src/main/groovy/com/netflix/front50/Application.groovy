package com.netflix.front50

import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.front50.exception.NoPrimaryKeyException
import com.netflix.front50.exception.NotFoundException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Configurable
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

import java.lang.reflect.Modifier

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
    String tags

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

    Application save() {
        Map<String, String> values = Application.declaredFields.toList().findResults {
            if ((it.modifiers == Modifier.PRIVATE) && (it.annotatedType.type == String.class)) {
                def value = this."$it.name"
                value ? [it.name, value] : null
            }
        }.collectEntries()
        if (!values.containsKey('name')) {
            throw new NoPrimaryKeyException("Application lacks a name!")
        }
        return dao.create(values['name'], values)
    }

    Collection<Application> findAll() throws NotFoundException {
        return dao.all()
    }

    Application findByName(String name) throws NotFoundException {
        return dao.findByName(name)
    }
}
