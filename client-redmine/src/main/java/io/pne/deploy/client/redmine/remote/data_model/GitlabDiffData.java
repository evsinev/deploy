package io.pne.deploy.client.redmine.remote.data_model;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;

@Data
public class GitlabDiffData {
    private Commit commit;
    private List<Commit> commits;
    private List<Diff> diffs;
    @SerializedName("compare_timeout")
    private boolean compareTimeout;
    @SerializedName("compare_same_ref")
    private boolean compareSameRef;
    private String web_url;
}
