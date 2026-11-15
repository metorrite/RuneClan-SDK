package com.runeclans.sdk;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** An answer that is just {@code {"data": [...]}} (the spec gives it no name). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Data<T>(List<T> data) {
    public Data {
        data = data == null ? List.of() : data;
    }
}
