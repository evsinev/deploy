package io.pne.deploy.client.redmine.process.data_model;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class DiffKeyTest {

    @Test
    public void equalIgnoringIdsWhenKeyFieldsMatch() {
        DiffKey a = key(new String[]{"host-1"}, 1, "svc", "1.0.0", "1.1.0");
        DiffKey b = key(new String[]{"host-2"}, 1, "svc", "1.0.0", "1.1.0"); // only ids differ

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void differsWhenVersionDiffers() {
        assertNotEquals(
                key(new String[]{"h"}, 1, "svc", "1.0.0", "1.1.0"),
                key(new String[]{"h"}, 1, "svc", "1.0.0", "1.2.0"));
    }

    @Test
    public void differsWhenProjectDiffers() {
        assertNotEquals(
                key(new String[]{"h"}, 1, "svc", "1.0.0", "1.1.0"),
                key(new String[]{"h"}, 2, "svc", "1.0.0", "1.1.0"));
    }

    @Test
    public void worksAsHashMapKey() {
        Map<DiffKey, String> map = new HashMap<>();
        map.put(key(new String[]{"host-1"}, 1, "svc", "1.0.0", "1.1.0"), "value");
        assertEquals("value", map.get(key(new String[]{"host-2"}, 1, "svc", "1.0.0", "1.1.0")));
    }

    private static DiffKey key(String[] ids, int project, String name, String oldV, String newV) {
        return new DiffKey(new DiffTask(ids, project, name, oldV, newV));
    }
}
