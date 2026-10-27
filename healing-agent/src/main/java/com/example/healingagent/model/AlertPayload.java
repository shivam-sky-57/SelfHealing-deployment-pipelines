package com.example.healingagent.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AlertPayload(
        @JsonProperty("version") String version,
        @JsonProperty("status") String status,
        @JsonProperty("receiver") String receiver,
        @JsonProperty("commonLabels") Map<String, String> commonLabels,
        @JsonProperty("commonAnnotations") Map<String, String> commonAnnotations,
        @JsonProperty("groupLabels") Map<String, String> groupLabels,
        @JsonProperty("alerts") List<AlertDetail> alerts,
        @JsonProperty("externalURL") String externalUrl
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AlertDetail(
            @JsonProperty("status") String status,
            @JsonProperty("labels") Map<String, String> labels,
            @JsonProperty("annotations") Map<String, String> annotations,
            @JsonProperty("startsAt") String startsAt,
            @JsonProperty("endsAt") String endsAt,
            @JsonProperty("generatorURL") String generatorUrl
    ) {}
}
