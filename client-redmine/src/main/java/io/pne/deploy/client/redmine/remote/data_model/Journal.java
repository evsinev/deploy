package io.pne.deploy.client.redmine.remote.data_model;

import com.google.gson.annotations.SerializedName;
import com.taskadapter.redmineapi.bean.JournalDetail;
import com.taskadapter.redmineapi.bean.User;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.List;

import static lombok.AccessLevel.PRIVATE;

@Data
@FieldDefaults(makeFinal = true, level = PRIVATE)
@Builder
public class Journal {
    int    id;
    User   user;
    String notes;

    @SerializedName("created_on")
    String  createdOn;

    @SerializedName("private_notes")
    boolean privateNotes;

    List<JournalDetail> details;
}
