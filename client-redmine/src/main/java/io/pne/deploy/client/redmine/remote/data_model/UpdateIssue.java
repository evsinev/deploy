package io.pne.deploy.client.redmine.remote.data_model;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class UpdateIssue {
    private Integer id;
    @SerializedName("project_id")
    private Integer projectId;
    @SerializedName("tracker_id")
    private Integer trackerId;
    @SerializedName("status_id")
    private Integer statusId;
    private String hash;
    private String notes;
    private String subject;
    @SerializedName("private_notes")
    private Boolean privateNotes;
    @SerializedName("priority_id")
    private Integer priorityId;
}