package io.pne.deploy.client.redmine.process.impl;

import io.pne.deploy.client.redmine.remote.model.ImmutableRedmineIssue;
import io.pne.deploy.client.redmine.remote.model.RedmineIssue;
import org.junit.Test;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Collections;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Проверяет механизм ISSUE_VALIDATION_SCRIPT ровно так, как его использует
 * {@link RedmineIssuesProcessServiceImpl#processRedmineIssue(long)}:
 * биндинг переменной {@code issue}, {@code eval} скрипта и приведение результата к {@link Boolean}.
 * Заодно подтверждает, что движок Nashorn доступен на classpath (актуально под JDK 21,
 * где Nashorn убран из JDK и подключается зависимостью org.openjdk.nashorn:nashorn-core).
 */
public class IssueValidationScriptTest {

    private static final String SCRIPT_RESOURCE = "/issue-validation-script.js";

    @Test
    public void nashornEngineIsAvailable() {
        assertNotNull(
                "Движок nashorn не найден — проверь зависимость org.openjdk.nashorn:nashorn-core на classpath",
                newEngine());
    }

    @Test
    public void issueMatchingScriptIsValidated() throws Exception {
        Object result = evalScript(issueBuilder().statusId(3).build());
        assertEquals(Boolean.TRUE, result);
    }

    @Test
    public void issueNotMatchingScriptIsRejected() throws Exception {
        Object result = evalScript(issueBuilder().statusId(1).build());
        assertEquals(Boolean.FALSE, result);
    }

    private Object evalScript(RedmineIssue issue) throws Exception {
        ScriptEngine engine = newEngine();
        assertNotNull(engine);
        engine.put("issue", issue);
        try (InputStream is = getClass().getResourceAsStream(SCRIPT_RESOURCE)) {
            assertNotNull("Не найден тестовый ресурс " + SCRIPT_RESOURCE, is);
            try (Reader reader = new InputStreamReader(is, UTF_8)) {
                return engine.eval(reader);
            }
        }
    }

    private static ScriptEngine newEngine() {
        return new ScriptEngineManager().getEngineByName("nashorn");
    }

    /**
     * Fixture, удовлетворяющий {@code issue-validation-script.js} по всем полям, кроме статуса,
     * который задаётся в конкретном тесте.
     */
    private static ImmutableRedmineIssue.Builder issueBuilder() {
        return ImmutableRedmineIssue.builder()
                .issueId(119126)
                .subject("Deploy request")
                .statusName("Resolved")
                .statusId(3)
                .description("> deploy some-alias")
                .projectId(1)
                .customFields(Collections.singletonMap("Security Approval", "1"))
                .projectName("Some project")
                .assigneeName("Evgeniy Sinev")
                .creatorName("Someone Else");
    }
}
