package io.pne.deploy.server.vertx.dashboard;

import com.payneteasy.startup.parameters.AStartupParameter;
import io.pne.deploy.util.env.IStartupConfig;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Builds a masked, display-friendly report of all {@code @AStartupParameter} settings for the config
 * screen. Reflects over each config interface's methods (the annotations live on the interface, not on
 * the resolved dynamic proxy) and invokes each to read the effective value. Secrets are never shown.
 */
public final class StartupConfigReport {

    /** Extra safety net: mask credential-like names even if the interface forgot {@code maskVariable=true}. */
    private static final Pattern SECRET_NAME = Pattern.compile("(?i).*(KEY|TOKEN|SECRET|PASSWORD).*");

    private StartupConfigReport() {
    }

    /** One config interface plus its resolved instance. */
    public record Group(String label, Class<? extends IStartupConfig> iface, IStartupConfig instance) {
    }

    /** One rendered parameter row. {@code value} is already masked when {@code masked} is true. */
    public record Entry(String group, String name, String value, String def, boolean masked, boolean isDefault) {
    }

    public static List<Entry> of(List<Group> aGroups) {
        List<Entry> out = new ArrayList<>();
        for (Group group : aGroups) {
            List<Entry> groupEntries = new ArrayList<>();
            for (Method method : group.iface().getMethods()) {
                AStartupParameter annotation = method.getAnnotation(AStartupParameter.class);
                if (annotation == null) {
                    continue;
                }
                String def       = annotation.value();
                String effective = invoke(method, group.instance());
                boolean masked   = annotation.maskVariable() || SECRET_NAME.matcher(annotation.name()).matches();
                boolean isDefault = effective.equals(def);
                String display   = masked ? maskValue(effective) : effective;
                groupEntries.add(new Entry(group.label(), annotation.name(), display, def, masked, isDefault));
            }
            groupEntries.sort(Comparator.comparing(Entry::name));
            out.addAll(groupEntries);
        }
        return out;
    }

    private static String invoke(Method aMethod, Object aInstance) {
        try {
            return String.valueOf(aMethod.invoke(aInstance));
        } catch (ReflectiveOperationException e) {
            return "(error)";
        }
    }

    private static String maskValue(String aValue) {
        return aValue == null || aValue.isEmpty() ? "(not set)" : "•••• (set)";
    }
}
