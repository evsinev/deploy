package io.pne.deploy.client.redmine.remote.data_model;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class Diff {
    private String diff;
    @SerializedName("new_path")
    private String newPath;
    @SerializedName("old_path")
    private String oldPath;
    @SerializedName("a_mode")
    private String aMode;
    @SerializedName("b_mode")
    private String bMode;
    @SerializedName("new_file")
    private boolean newFile;
    @SerializedName("renamed_file")
    private boolean renamedFile;
    @SerializedName("deleted_file")
    private boolean deletedFile;
}
