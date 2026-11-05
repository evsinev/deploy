package io.pne.deploy.client.redmine.process.impl;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * ASM is pinned to 9.11.1 in the parent pom (CVE fixes), while nashorn-core 15.7 was built against ASM 7.3.1.
 * Nashorn uses ASM at runtime: every evaluated script is compiled to bytecode with a ClassWriter, and
 * {@code Java.extend} generates adapter classes. These tests check that this still works with the pinned ASM.
 */
public class NashornAsmCompatibilityTest {

    private static final String NASHORN_ARGS = "nashorn.args";

    private String previousNashornArgs;

    @Before
    public void saveNashornArgs() {
        previousNashornArgs = System.getProperty(NASHORN_ARGS);
    }

    @After
    public void restoreNashornArgs() {
        if (previousNashornArgs == null) {
            System.clearProperty(NASHORN_ARGS);
        } else {
            System.setProperty(NASHORN_ARGS, previousNashornArgs);
        }
    }

    @Test
    public void asmFamilyIsPatched() throws Exception {
        // CVE-2026-110257..110263, CVE-2026-110270, CVE-2026-110591 are fixed in 9.11, CVE-2026-112046 in 9.11.1
        String[] asmClasses = {
                "org.objectweb.asm.ClassReader",              // asm
                "org.objectweb.asm.commons.Remapper",         // asm-commons
                "org.objectweb.asm.tree.ClassNode",           // asm-tree
                "org.objectweb.asm.tree.analysis.Analyzer",   // asm-analysis
                "org.objectweb.asm.util.Textifier"            // asm-util
        };
        for (String asmClass : asmClasses) {
            String version = Class.forName(asmClass).getPackage().getImplementationVersion();
            assertNotNull("no implementation version for " + asmClass, version);
            assertTrue(asmClass + " comes from ASM " + version + ", expected 9.11.1+",
                    compareVersions(version, "9.11.1") >= 0);
        }
    }

    @Test
    public void compilesScriptWithFunctionsClosuresLoopsAndExceptions() throws Exception {
        assertEquals("55|3|caught: boom|{\"a\":[1,2,3]}|x-y-z", eval(newEngine(), sampleScript()));
    }

    @Test
    public void compilesScriptWithOptimisticTypes() throws Exception {
        // optimistic types make Nashorn deoptimise and recompile methods (more ASM code generation paths)
        System.setProperty(NASHORN_ARGS, "--optimistic-types=true");
        ScriptEngine engine = newEngine();
        assertEquals("55|3|caught: boom|{\"a\":[1,2,3]}|x-y-z", eval(engine, sampleScript()));
        // int arithmetic overflowing into double forces a deoptimisation and recompilation
        assertEquals("8589934588", eval(engine,
                "function twice(x) { return x + x; } String(twice(2147483647) * 2)"));
    }

    @Test
    public void generatesJavaAdapterClass() throws Exception {
        ScriptEngine engine = newEngine();
        Object supplier = engine.eval(
                "var Supplier = Java.type('java.util.function.Supplier');\n"
                        + "new Supplier({ get: function() { return 'from js'; } })");
        assertTrue("not a Supplier: " + supplier, supplier instanceof Supplier);
        // adapter classes are generated with ASM into this package (JavaAdapterBytecodeGenerator)
        assertTrue("adapter class was not generated: " + supplier.getClass().getName(),
                supplier.getClass().getName().startsWith("org.openjdk.nashorn.javaadapters."));
        assertEquals("from js", ((Supplier<?>) supplier).get());
    }

    private static String sampleScript() {
        return "function sum(n) { var s = 0; for (var i = 1; i <= n; i++) { s += i; } return s; }\n"
                + "function counter() { var c = 0; return function() { return ++c; }; }\n"
                + "var next = counter(); next(); next();\n"
                + "var caught;\n"
                + "try { throw new Error('boom'); } catch (e) { caught = 'caught: ' + e.message; }\n"
                + "var json = JSON.stringify(JSON.parse('{\"a\":[1,2,3]}'));\n"
                + "var joined = 'x,y,z'.split(/,/).join('-');\n"
                + "[sum(10), next(), caught, json, joined].join('|')";
    }

    private static Object eval(ScriptEngine engine, String script) throws Exception {
        return engine.eval(script);
    }

    private static ScriptEngine newEngine() {
        ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");
        assertNotNull("nashorn engine is not on the classpath", engine);
        return engine;
    }

    private static int compareVersions(String left, String right) {
        String[] l = left.split("\\.");
        String[] r = right.split("\\.");
        for (int i = 0; i < Math.max(l.length, r.length); i++) {
            int a = i < l.length ? Integer.parseInt(l[i]) : 0;
            int b = i < r.length ? Integer.parseInt(r[i]) : 0;
            if (a != b) {
                return Integer.compare(a, b);
            }
        }
        return 0;
    }
}
