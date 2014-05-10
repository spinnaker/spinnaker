package com.netflix.front50

/**
 * Created by aglover on 4/22/14.
 */
public interface ApplicationDAO {
    Application findByName(String name)
    List<Application> all()
    Application create(String id, Map<String, String> attributes)
    void update(String id, Map<String, String> attributes)
    void delete(String id)
    boolean isHealthly()
}