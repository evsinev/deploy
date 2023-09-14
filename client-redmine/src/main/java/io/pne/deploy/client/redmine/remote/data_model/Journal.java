package io.pne.deploy.client.redmine.remote.data_model;

import com.google.gson.annotations.SerializedName;
import com.taskadapter.redmineapi.bean.JournalDetail;
import com.taskadapter.redmineapi.bean.User;
import lombok.Data;

import java.util.List;

@Data
public class Journal {
    private int id;
    private User user;
    private String notes;
    @SerializedName("created_on")
    private String createdOn;
    @SerializedName("private_notes")
    private boolean privateNotes;
    private List<JournalDetail> details;
}
