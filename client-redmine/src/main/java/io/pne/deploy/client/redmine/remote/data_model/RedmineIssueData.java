package io.pne.deploy.client.redmine.remote.data_model;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;

@Data
public class RedmineIssueData {
    private int id;
    private Project project;
    private Tracker tracker;
    private Status status;
    private Priority priority;
    private User author;
    private User assigned_to;
    private String subject;
    private String description;
    @SerializedName("start_date")
    private String startDate;
    @SerializedName("due_date")
    private String dueDate;
    @SerializedName("is_private")
    private Boolean isPrivate;
    @SerializedName("custom_fields")
    private List<CustomFields> customFields;
    @SerializedName("created_on")
    private String createdOn;
    @SerializedName("updated_on")
    private String updatedOn;
    @SerializedName("closed_on")
    private String closeOn;
    private List<ChangeSet> changesets;
    private List<Journal> journals;

    @Data
    public static class Tracker {
        private int id;
        private String name;
    }

    @Data
    public static class Priority {
        private int id;
        private String name;
    }

    @Data
    public static class ChangeSet {
        private String revision;
        private User user;
        private String comments;
        @SerializedName("committed_on")
        private String committedOn;
    }

    @Data
    public static class User {
        private int id;
        private String name;
    }

    @Data
    public static class Project {
        private int id;
        private String name;
    }
}