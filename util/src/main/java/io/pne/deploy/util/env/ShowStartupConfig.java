package io.pne.deploy.util.env;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

public class ShowStartupConfig<T> {

    private static final Logger LOG = LoggerFactory.getLogger(ShowStartupConfig.class);

    private final T target;

    public ShowStartupConfig(T target) {
        this.target = target;
    }

    public T get() {
        LOG.info("Startup config for {}:", target.getClass().getInterfaces()[0].getSimpleName());

        Method[] methods = target.getClass().getInterfaces()[0].getDeclaredMethods();
        for (Method method : methods) {
            AStartupParameter param = method.getAnnotation(AStartupParameter.class);
            if(param != null) {
               LOG.info("    {} = {}", param.name(), get(param.name(), param.defaultValue()));
            }
        }
        return target;
    }

    private String get(String aName, String aDefault) {
        String value = System.getenv(aName);
        if(value == null) {
            value = System.getProperty(aName, aDefault);
        }
        return value;
    }

}
