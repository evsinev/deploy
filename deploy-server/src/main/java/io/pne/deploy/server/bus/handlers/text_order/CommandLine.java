package io.pne.deploy.server.bus.handlers.text_order;

import io.pne.deploy.server.model.Command;

import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

public class CommandLine {

    public final String commandId;
    public final String line;

    public CommandLine(String commandId, String line) {
        this.commandId = commandId;
        this.line = line;
    }

    public Command createCommand() {
        try {
            StringTokenizer tokenizer = new StringTokenizer(line, "\t ");
            String name = tokenizer.nextToken();

            Map<String, String> map = new HashMap<>();
            while (tokenizer.hasMoreTokens()) {
                String text = tokenizer.nextToken();
                StringTokenizer st = new StringTokenizer(text, "=");
                String key = nextToken(st, text, "key");
                String value = nextToken(st, text, "value");
                map.put(key, value);
            }

            return new Command(commandId, name, map);
        } catch (Exception e) {
            throw new IllegalStateException("Could not parse '"+line+"'", e);
        }
    }

    private String nextToken(StringTokenizer st, String text, String key) {
        if (st.hasMoreTokens()) {
            return st.nextToken();
        }

        throw new IllegalStateException("No token for " + key + " in '" + text+"'");
    }

}
