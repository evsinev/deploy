package io.pne.deploy.server.service.impl.alias;

import io.pne.deploy.agent.api.command.AgentCommandType;
import io.pne.deploy.agent.api.command.AgentStep;
import io.pne.deploy.server.api.task.Task;
import io.pne.deploy.server.api.task.TaskCommand;

import java.util.Map;

/**
 * Writes out what a task line would do, without doing any of it.
 *
 * <p>This is what replaces watching a script trace by: every step with the values already filled in, in the order
 * they will run. It is meant to be read next to the script it replaces, line by line, while a deployment is being
 * moved over.
 */
public class PlanRenderer {

    private PlanRenderer() {
    }

    public static String render(Task aTask) {
        StringBuilder text = new StringBuilder(aTask.taskLine).append(System.lineSeparator());

        for (int i = 0; i < aTask.commands.size(); i++) {
            TaskCommand command  = aTask.commands.get(i);
            String      position = "[" + (i + 1) + "/" + aTask.commands.size() + "]";

            text.append(position)
                .append(" agents=").append(String.join(",", command.agents.getIds()))
                .append(System.lineSeparator());

            if (command.command.type == AgentCommandType.STEPS) {
                renderSteps(text, command);
            } else {
                text.append("      run ").append(command.command.name)
                    .append(' ').append(String.join(" ", command.command.arguments))
                    .append(System.lineSeparator());
            }
        }
        return text.toString();
    }

    private static void renderSteps(StringBuilder aText, TaskCommand aCommand) {
        int number = 1;
        for (AgentStep step : aCommand.command.getSteps()) {
            aText.append("      ").append(number++).append(". ").append(pad(step.getType()));
            for (Map.Entry<String, String> param : step.getParams().entrySet()) {
                aText.append(' ').append(param.getKey()).append('=').append(param.getValue());
            }
            aText.append(System.lineSeparator());
        }
    }

    private static String pad(String aType) {
        return aType.length() >= 16 ? aType : aType + " ".repeat(16 - aType.length());
    }
}
