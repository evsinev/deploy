package io.pne.deploy.client.redmine.remote.data_model;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import static lombok.AccessLevel.PRIVATE;

@Data
@FieldDefaults(makeFinal = true, level = PRIVATE)
@Builder
public class Journal {
    int                   id;
    RedmineIssueData.User user;
    String                notes;

    @SerializedName("created_on")
    String createdOn;

    @SerializedName("private_notes")
    boolean privateNotes;
}
