package com.netflix.front50

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.netflix.front50.exception.NoPrimaryKeyException
import com.netflix.front50.exception.NotFoundException
import groovy.transform.ToString
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Configurable
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

import java.lang.reflect.Field
import java.lang.reflect.Modifier

/**
 * Created by aglover on 4/20/14.
 */
@Component
@Configurable
@ToString
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
    String regions

    @JsonIgnore
    @Autowired
    @Qualifier("SimpleDB")
    ApplicationDAO dao

    Application() {} //forces Groovy to add LinkedHashMap constructor

    @JsonCreator
    Application(@JsonProperty("name") String name,
                @JsonProperty("description") String description,
                @JsonProperty("email") String email,
                @JsonProperty("owner") String owner,
                @JsonProperty("type") String type,
                @JsonProperty("group") String group,
                @JsonProperty("monitorBucketType") String monitorBucketType,
                @JsonProperty("pdApiKey") String pdApiKey,
                @JsonProperty("regions") String regions,
                @JsonProperty("tags") String tags,
                @JsonProperty("createTs") String createdAt,
                @JsonProperty("updateTs") String updatedAt
    ) {
        this.group = group
        this.monitorBucketType = monitorBucketType
        this.pdApiKey = pdApiKey
        this.name = name
        this.description = description
        this.email = email
        this.owner = owner
        this.type = type
        this.tags = tags
        this.regions = regions
        this.createTs = createdAt
        this.updateTs = updatedAt
    }

    void update(Map<String, String> newAttributes) {
        checkForName()
        //must have a name, go ahead and remove it
        newAttributes.remove("name")
        this.dao.update(this.name, newAttributes)
    }

    void delete() {
        checkForName()
        this.dao.delete(this.name)
    }

    Application clear() {
        Application.declaredFields.each { field ->
            if (isColumnProperty(field)) {
                this."$field.name" = null
            }
        }
        return this
    }

    /**
     * Similar to clone but doesn't produce a copy
     */
    Application initialize(Application app) {
        this.clear()
        Application.declaredFields.each { field ->
            if (isColumnProperty(field)) {
                def value = app."$field.name"
                if (value) {
                    this."$field.name" = value
                }
            }
        }
        return this
    }

    Application save() {
        Map<String, String> values = allSetColumnProperties()
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

    Application withName(String name) {
        this.name = name
        return this
    }

    private void checkForName() {
        if (this.name == null || this.name.equals("")) {
            throw new NoPrimaryKeyException("Application lacks a name!")
        }
    }

    private boolean isColumnProperty(Field field) {
        (field.modifiers == Modifier.PRIVATE) && (field.annotatedType.type == String.class)
    }

    protected Map<String, String> allSetColumnProperties() {
        Application.declaredFields.toList().findResults {
            if (isColumnProperty(it)) {
                def value = this."$it.name"
                value ? [it.name, value] : null
            }
        }.collectEntries()
    }
}
