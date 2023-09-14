package io.pne.deploy.client.redmine.remote.data_model;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class UpdateIssue {
                                     Integer id;
    @SerializedName("project_id")    Integer projectId;
    @SerializedName("tracker_id")    Integer trackerId;
    @SerializedName("status_id")     Integer statusId;
                                     String  hash;
                                     String  notes;
                                     String  subject;
    @SerializedName("private_notes") Boolean privateNotes;
    @SerializedName("priority_id")   Integer priorityId;
}