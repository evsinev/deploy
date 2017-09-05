package io.pne.deploy.util.env;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface AStartupParameter {

    String name();

    String defaultValue();

}
