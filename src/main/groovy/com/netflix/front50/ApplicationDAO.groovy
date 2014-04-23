package com.netflix.front50

/**
 * Created by aglover on 4/22/14.
 */
public interface ApplicationDAO {
    Application findByName(String name)
    List<Application> all()
}