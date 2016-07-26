package io.pne.deploy.agent.tasks.shellscript;

import io.pne.deploy.agent.tasks.ITaskContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;

public class ShellScriptLoggerThread extends Thread {

    private static final Logger LOG = LoggerFactory.getLogger(ShellScriptLoggerThread.class);

    private final ITaskContext context;
    private final InputStream  inputStream;
    private final String       name;

    public ShellScriptLoggerThread(String aName, ITaskContext aContext, InputStream aInputStream) {
        context = aContext;
        inputStream = aInputStream;
        name = aName;

        setName(name);
    }

    @Override
    public void run() {
        try {
            try (LineNumberReader in = new LineNumberReader(new InputStreamReader(inputStream))) {
                String line;
                while ((line = in.readLine()) != null) {
                    LOG.debug("{}: {}", name, line);
                    context.log(line);
                }
            }
        } catch (Exception e) {
            LOG.error(name + ": Error while reading input stream", e);
        }

    }
}
