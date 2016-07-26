package io.pne.deploy.server.task;

import org.immutables.value.Value;

import java.util.List;
import java.util.Map;

@Value.Immutable
public interface Task {

    long issueId();

    List<String> hosts();

    String command();

    Map<String, String> parameters();

}
