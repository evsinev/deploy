package io.pne.deploy.client.redmine.remote.data_model;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

/** Body of the deploy-review webhook: {@code {"project","app","instance","old_version","new_version","deployed_at"}}. */
@Data
public class DeployReviewRequest {

    private String project;
    private String app;
    private String instance;

    @SerializedName("old_version")
    private String oldVersion;

    @SerializedName("new_version")
    private String newVersion;

    /** ISO-8601 UTC, second precision (e.g. {@code 2026-08-11T15:07:39Z}); optional for the receiver. */
    @SerializedName("deployed_at")
    private String deployedAt;

}
