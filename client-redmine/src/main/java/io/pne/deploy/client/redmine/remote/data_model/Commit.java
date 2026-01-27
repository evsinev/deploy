package io.pne.deploy.client.redmine.remote.data_model;

import lombok.Data;
import com.google.gson.annotations.SerializedName;

import java.util.List;

@Data
public class Commit {
    private String id;
    @SerializedName("short_id")
    private String shortId;
    @SerializedName("created_at")
    private String createdAt;
    @SerializedName("parent_ids")
    private List<String> parentIds;
    private String title;
    private String message;
    @SerializedName("author_name")
    private String authorName;
    @SerializedName("author_email")
    private String authorEmail;
    @SerializedName("authored_date")
    private String authoredDate;
    @SerializedName("committer_name")
    private String committerName;
    @SerializedName("committer_email")
    private String committerEmail;
    @SerializedName("committer_date")
    private String committerDate;
    @SerializedName("web_url")
    private String webUrl;
}
