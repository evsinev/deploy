package io.pne.deploy.client.redmine.process.impl;

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
    public void getCurrentVersionTestNull() {
        List<DiffTask> diffTasks = diffService.getCurrentVersion(null);
        Assert.assertEquals(0, diffTasks.size());
    }

    @Test
    @Ignore
    public void aggregateTest1() {
        List<DiffTask> tasks = new ArrayList<>();
        tasks.add(getDiffTaskProdLike(new String[]{"cool-1"}, "1", "3"));
        tasks.add(getDiffTaskProdLike(new String[]{"cool-2"}, "2", "3"));
        tasks.add(getDiffTaskProdLike(new String[]{"cool-3"}, "1", "3"));
        Map<DiffKey, DiffTask> aggregated = diffService.aggregate(tasks);
        Assert.assertEquals(2, aggregated.size());
    }

    @Test
    @Ignore
    public void aggregateTest2() {
        List<DiffTask> tasks = new ArrayList<>();
        tasks.add(getDiffTaskProdLike(new String[]{"cool-1"}, "1", "2"));
        tasks.add(getDiffTaskProdLike(new String[]{"cool-3"}, "1", "2"));
        Map<DiffKey, DiffTask> aggregated = diffService.aggregate(tasks);
        Assert.assertEquals(1, aggregated.size());
    }

    @Test
    @Ignore
    public void aggregateTest3() {
        List<DiffTask> tasks = new ArrayList<>();
        tasks.add(getDiffTaskProdLike(new String[]{"cool-1"}, "1", "2"));
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
        String redmineMessage = diffService.constructRedmineMessage(getDiffTaskProdLike(new String[]{"cool-1", "cool-2", "cool-3"}, "1", "2"), diffLinks);
        System.out.println(redmineMessage);
    }

    @Test
    @Ignore
    public void constructTelegramMessage() {
        Map<Integer, String> subjectCache = new HashMap<>();
        List<String> diffs = getDiffList();
        List<DiffLink> diffLinks = diffService.mapDiffIssues(diffs, subjectCache);
        List<String> telegramMessage = diffService.constructTelegramMessage(getDiffTaskProdLike(new String[]{"cool-1", "cool-2", "cool-3"}, "1", "2"), diffLinks);
        System.out.println(telegramMessage);
    }

    private List<String> getDiffList() {
        String diffStr = ""; //Fill this string for tests
        return Arrays.asList(diffStr.split("\\\\=,\\\\="));
    }

    private DiffTask getDiffTaskProdLike(String[] ids, String oldVersion, String newVersion) {
        return new DiffTask(ids,
                1,
                "cool 2",
                oldVersion,
                newVersion);
    }
}
