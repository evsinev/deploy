package io.pne.deploy.agent;

import com.beust.jcommander.Parameter;

import java.io.File;

public class AgentParameters {

    @Parameter(names = "--server", description = "Server URL")
    public String serverUrl = "http://localhost:9020/";

    @Parameter(names = "--scripts-dir", description = "Directory for scripts")
    public String scriptDir = "scripts";

    public File getScriptsDir() {
        File dir = new File(scriptDir);
        if(!dir.exists()) {
            throw new IllegalStateException("Directory " + dir.getAbsolutePath() + " does not exist");
        }
        if(!dir.isDirectory()) {
            throw new IllegalStateException(dir.getAbsolutePath() + " is not a directory");
        }
        return dir;
    }
}
