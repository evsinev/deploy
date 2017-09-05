package io.pne.deploy.util.env;

import java.lang.reflect.Method;

public class StartupConfig {

    private final Class<? extends IStartupConfig> clazz;

    public <T extends IStartupConfig> StartupConfig(Class<? extends IStartupConfig> aClass) {
        this.clazz = aClass;
    }

    public int getInt(String aMethodName) {
        return Integer.parseInt(get(aMethodName));
    }

    public String get(String aMethodName) {
        Method method = null;
        try {
            method = clazz.getMethod(aMethodName);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("No method " + aMethodName);
        }
        AStartupParameter parameter = method.getAnnotation(AStartupParameter.class);
        if(parameter == null) {
            throw new IllegalStateException("No annotation @AStartupParameter in the method " + method);
        }
        return get(parameter.name(), parameter.defaultValue());
    }

    private String get(String aName, String aDefault) {
        String value = System.getenv(aName);
        if(value == null) {
            value = System.getProperty(aName, aDefault);
        }
        return value;
    }
}
