package io.pne.deploy.server.service.impl.alias;

import io.pne.deploy.server.api.task.Task;
import io.pne.deploy.server.api.task.TaskCommand;
import org.junit.Test;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.*;

public class AliasParserTest {
    @Test
    public void parseAlias() throws Exception {
        AliasParser parser = new AliasParser(new File("src/test/resources/aliases"));
        Task task = parser.parseAlias("proc 3.33-40");
        System.out.println("task = " + task);
        assertNotNull(task.commands);
        assertEquals(2, task.commands.size());
        {
            TaskCommand taskCommand = task.commands.get(0);
            assertEquals("localhost", taskCommand.agents.getIds()[0]);
        }
    }

    @Test
    public void dump() {
        final DumperOptions dumperOptions = new DumperOptions();
        dumperOptions.setPrettyFlow(true);
        Yaml yaml = new Yaml(dumperOptions);

        AliasCommand command = new AliasCommand();
        command.agents = "localhost";
        command.name = "echo";
        command.arguments = Arrays.asList("alias", "$1");

        AliasDescription description = new AliasDescription();
        description.commands = new ArrayList<>();
        description.commands.add(command);

        String text = yaml.dump(description);
        System.out.println("text = " + text);

    }
}