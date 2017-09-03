package io.pne.deploy.server.service.impl;

import org.junit.Assert;
import org.junit.Test;

public class InputTextCheckerTest {
    @Test
    public void checkAlias() throws Exception {
        InputTextChecker checker = new InputTextChecker();
        checker.checkAlias("proc");
        checker.checkAlias("proc 3.33-40");
        checker.checkAlias("proc 3.33-40");

        try {
            checker.checkAlias("proc $");
            Assert.fail();
        } catch (IllegalArgumentException e) {
            Assert.assertEquals("Alias contains illegal characters", e.getMessage());
        }
    }

}