package io.pne.deploy.client.redmine.remote.queue;

import io.pne.deploy.client.redmine.remote.queue.PersistentSpool.Stored;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class PersistentSpoolTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void appendKeepsFifoOrderAndRemoveDrops() {
        PersistentSpool spool = new PersistentSpool(tmp.getRoot());
        String first  = spool.append("{\"a\":1}");
        spool.append("{\"a\":2}");

        List<Stored> all = spool.loadAll();
        assertEquals(2, all.size());
        assertEquals("{\"a\":1}", all.get(0).getJson());
        assertEquals("{\"a\":2}", all.get(1).getJson());

        spool.remove(first);
        assertEquals(1, spool.loadAll().size());
    }

    @Test
    public void putOverwritesByKey() {
        PersistentSpool spool = new PersistentSpool(tmp.getRoot());
        spool.put("edit-1-100", "{\"t\":\"a\"}");
        spool.put("edit-1-100", "{\"t\":\"b\"}");

        List<Stored> all = spool.loadAll();
        assertEquals(1, all.size());
        assertEquals("{\"t\":\"b\"}", all.get(0).getJson());
    }

    @Test
    public void reopenSeesPendingAndContinuesSequence() {
        PersistentSpool first = new PersistentSpool(tmp.getRoot());
        first.append("{\"a\":1}");

        PersistentSpool reopened = new PersistentSpool(tmp.getRoot());
        assertEquals(1, reopened.loadAll().size());
        reopened.append("{\"a\":2}");

        List<Stored> all = reopened.loadAll();
        assertEquals(2, all.size());
        assertEquals("{\"a\":1}", all.get(0).getJson());
        assertEquals("{\"a\":2}", all.get(1).getJson());
    }
}
