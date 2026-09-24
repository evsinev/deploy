package io.pne.deploy.server.service.impl.alias;

import java.util.List;

public class AliasDescription {

    /** Values this alias expects on the task line. Present only in the newer, checked form of an alias file. */
    public List<AliasParam> params;

    public List<AliasCommand> commands;
    public AliasDiff          diff;
}
