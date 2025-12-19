package io.pne.deploy.client.redmine.process.impl;

import io.pne.deploy.agent.api.command.AgentCommand;
import io.pne.deploy.agent.api.command.AgentCommandParameters;
import io.pne.deploy.agent.api.command.AgentCommandType;
import io.pne.deploy.client.redmine.process.data_model.DiffKey;
import io.pne.deploy.client.redmine.process.data_model.DiffLink;
import io.pne.deploy.client.redmine.process.data_model.DiffTask;
import io.pne.deploy.client.redmine.remote.impl.IRedmineRemoteConfig;
import io.pne.deploy.server.api.task.*;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.util.*;

import static com.payneteasy.startup.parameters.StartupParametersFactory.getStartupParameters;

public class DiffServiceImplTest {
    private final DiffServiceImpl diffService = new DiffServiceImpl(getStartupParameters(IRedmineRemoteConfig.class));

    @Test
    @Ignore
    public void getCurrentVersionTest() {
        List<DiffTask> diffTasks = diffService.getCurrentVersion(getTaskProdLike());
        Assert.assertEquals(3, diffTasks.size());
    }

    @Test
    @Ignore
    public void getCurrentVersionTestNull() {
        List<DiffTask> diffTasks = diffService.getCurrentVersion(null);
        Assert.assertEquals(0, diffTasks.size());
    }

    @Test
    @Ignore
    public void aggregateTest1() {
        List<DiffTask> tasks = new ArrayList<>();
        tasks.add(getDiffTaskProdLike(new String[]{"ipro-1"}, "3.36.145-179", "3.36.145-219"));
        tasks.add(getDiffTaskProdLike(new String[]{"ipro-2"}, "3.36.145-200", "3.36.145-219"));
        tasks.add(getDiffTaskProdLike(new String[]{"ipro-3"}, "3.36.145-179", "3.36.145-219"));
        Map<DiffKey, DiffTask> aggregated = diffService.aggregate(tasks);
        Assert.assertEquals(2, aggregated.size());
    }

    @Test
    @Ignore
    public void aggregateTest2() {
        List<DiffTask> tasks = new ArrayList<>();
        tasks.add(getDiffTaskProdLike(new String[]{"ipro-1"}, "3.36.145-179", "3.36.145-219"));
        tasks.add(getDiffTaskProdLike(new String[]{"ipro-3"}, "3.36.145-179", "3.36.145-219"));
        Map<DiffKey, DiffTask> aggregated = diffService.aggregate(tasks);
        Assert.assertEquals(1, aggregated.size());
    }

    @Test
    @Ignore
    public void aggregateTest3() {
        List<DiffTask> tasks = new ArrayList<>();
        tasks.add(getDiffTaskProdLike(new String[]{"ipro-1"}, "3.36.145-179", "3.36.145-219"));
        Map<DiffKey, DiffTask> aggregated = diffService.aggregate(tasks);
        Assert.assertEquals(1, aggregated.size());
    }

    @Test
    @Ignore
    public void aggregateTestEmpty() {
        List<DiffTask> tasks = new ArrayList<>();
        Map<DiffKey, DiffTask> aggregated = diffService.aggregate(tasks);
        Assert.assertEquals(0, aggregated.size());
    }

    @Test
    @Ignore
    public void aggregateTestNull() {
        List<DiffTask> tasks = new ArrayList<>();
        Map<DiffKey, DiffTask> aggregated = diffService.aggregate(tasks);
        Assert.assertEquals(0, aggregated.size());
    }

    @Test
    @Ignore
    public void mapDiffIssues() {
        Map<Integer, String> subjectCache = new HashMap<>();
        List<String> diffs = getDiffList();
        List<DiffLink> diffLinks = diffService.mapDiffIssues(diffs, subjectCache);
        Assert.assertNotNull(diffLinks);
        Assert.assertNotNull(subjectCache);
    }

    @Test
    @Ignore
    public void mapDiffIssuesNull() {
        List<DiffLink> diffLinks = diffService.mapDiffIssues(null, new HashMap<>());
        Assert.assertNotNull(diffLinks);
        Assert.assertEquals(0, diffLinks.size());
    }

    @Test
    @Ignore
    public void constructRedmineMessage() {
        Map<Integer, String> subjectCache = new HashMap<>();
        List<String> diffs = getDiffList();
        List<DiffLink> diffLinks = diffService.mapDiffIssues(diffs, subjectCache);
        String redmineMessage = diffService.constructRedmineMessage(getDiffTaskProdLike(new String[]{"ipro-1", "ipro-2", "ipro-3"}, "3.36.145-179", "3.36.145-219"), diffLinks);
        System.out.println(redmineMessage);
    }

    @Test
    @Ignore
    public void constructTelegramMessage() {
        Map<Integer, String> subjectCache = new HashMap<>();
        List<String> diffs = getDiffList();
        List<DiffLink> diffLinks = diffService.mapDiffIssues(diffs, subjectCache);
        List<String> telegramMessage = diffService.constructTelegramMessage(getDiffTaskProdLike(new String[]{"ipro-1", "ipro-2", "ipro-3"}, "3.36.145-179", "3.36.145-219"), diffLinks);
        System.out.println(telegramMessage);
    }

    private List<String> getDiffList() {
        String diffStr = ""; //Fill this string for tests
        return Arrays.asList(diffStr.split("\\\\=,\\\\="));
    }

    private DiffTask getDiffTaskProdLike(String[] ids, String oldVersion, String newVersion) {
        return new DiffTask(ids,
                114,
                "msk-paynet-proc 3.36.145-219",
                oldVersion,
                newVersion);
    }

    private Task getTaskProdLike() {
        TaskId aId = TaskId.generateTaskId();
        TaskParameters aParameters = new TaskParameters();
        List<TaskCommand> aCommands = new ArrayList<>();
        aCommands.add(getTaskCommand(new String[]{"ipro-1"},
                "./bin/redeploy-sandbox-proc.sh",
                Arrays.asList("3.36.145-219", "https://sandbox.payneteasy.eu/paynet-ui/management/version.txt?filter=version", "gitlab=569")));
        aCommands.add(getTaskCommand(new String[]{"ipro-2"},
                "./bin/redeploy-proc.sh",
                Arrays.asList("3.36.145-219", "https://gate.payneteasy.eu/paynet-ui/management/version.txt?filter=version", "gitlab=569")));
        aCommands.add(getTaskCommand(new String[]{"ipro-3"},
                "./bin/redeploy-proc.sh",
                Arrays.asList("3.36.145-219", "https://gate.payneteasy.eu/paynet-ui/management/version.txt?filter=version", "gitlab=569")));
        aCommands.add(getTaskCommand(new String[]{"ipro-1"},
                "./bin/redeploy-proc.sh",
                Arrays.asList("3.36.145-219", "https://gate.payneteasy.eu/paynet-ui/management/version.txt?filter=version", "gitlab=569")));
        return new Task(aId,
                aParameters,
                aCommands,
                "msk-paynet-proc 3.36.145-219",
                158402);
    }

    private TaskCommand getTaskCommand(String[] ids, String name, List<String> arguments) {
        AgentFinder agents = new AgentFinder(ids);
        AgentCommandParameters parameters = new AgentCommandParameters();
        AgentCommand command = new AgentCommand(parameters,
                AgentCommandType.SHELL,
                name, arguments);
        return new TaskCommand(agents, command);
    }


}
