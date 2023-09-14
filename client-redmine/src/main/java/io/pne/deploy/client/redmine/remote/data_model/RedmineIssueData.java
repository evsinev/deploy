package io.pne.deploy.client.redmine.remote.data_model;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.List;

import static lombok.AccessLevel.PRIVATE;

@Data
@FieldDefaults(makeFinal = true, level = PRIVATE)
@Builder
public class RedmineIssueData {
    int      id;
    Project  project;
    Tracker  tracker;
    Status   status;
    Priority priority;
    User     author;
    User     assigned_to;
    String   subject;
    String   description;

    @SerializedName("start_date")    String             startDate;
    @SerializedName("due_date")      String             dueDate;
    @SerializedName("is_private")    Boolean            isPrivate;
    @SerializedName("custom_fields") List<CustomFields> customFields;
    @SerializedName("created_on")    String             createdOn;
    @SerializedName("updated_on")    String             updatedOn;
    @SerializedName("closed_on")     String             closeOn;

    List<ChangeSet> changesets;
    List<Journal>   journals;

    @Data
    @FieldDefaults(makeFinal = true, level = PRIVATE)
    @Builder
    public static class Tracker {
        int    id;
        String name;
    }

    @Data
    @FieldDefaults(makeFinal = true, level = PRIVATE)
    @Builder
    public static class Priority {
        int    id;
        String name;
    }

    @Data
    @FieldDefaults(makeFinal = true, level = PRIVATE)
    @Builder
    public static class ChangeSet {
        String revision;
        User   user;
        String comments;
        @SerializedName("committed_on")
        String committedOn;
    }

    @Data
    @FieldDefaults(makeFinal = true, level = PRIVATE)
    @Builder
    public static class User {
        int    id;
        String name;
    }

    @Data
    @FieldDefaults(makeFinal = true, level = PRIVATE)
    @Builder
    public static class Project {
        int    id;
        String name;
    }
}