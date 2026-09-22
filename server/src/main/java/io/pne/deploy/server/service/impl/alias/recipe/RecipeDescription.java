package io.pne.deploy.server.service.impl.alias.recipe;

import io.pne.deploy.server.service.impl.alias.AliasParam;
import io.pne.deploy.server.service.impl.alias.AliasStep;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A shared list of steps with the values it expects.
 *
 * <p>A recipe holds what every deployment of a kind does; an alias holds which application, which host and which
 * version. Keeping them apart is what stops the same sequence from being copied once per application and drifting
 * apart afterwards.
 */
public class RecipeDescription {

    public String description;

    /** Values this recipe expects, in the order they are written: a default may use the values above it. */
    public LinkedHashMap<String, AliasParam> params;

    public List<AliasStep> steps;

    /** The declared values as a list, each carrying the name it was written under. */
    public List<AliasParam> getParams() {
        List<AliasParam> result = new ArrayList<>();
        if (params == null) {
            return result;
        }
        for (Map.Entry<String, AliasParam> entry : params.entrySet()) {
            AliasParam param = entry.getValue() == null ? new AliasParam() : entry.getValue();
            param.name = entry.getKey();
            result.add(param);
        }
        return result;
    }
}
