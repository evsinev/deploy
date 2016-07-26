package io.pne.deploy.agent.tasks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import io.pne.deploy.api.tasks.ImmutableShellScriptParameters;
import io.pne.deploy.api.tasks.ShellScriptParameters;
import org.junit.Test;

import java.io.IOException;

public class JsonTest {

    @Test
    public void produceAndParseJson() throws IOException {

        ShellScriptParameters parameters = ImmutableShellScriptParameters.builder()
                .username("username")
                .taskId("123")
                .filename("test.txt")
                .build();

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new Jdk8Module());
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

        ObjectWriter writer = objectMapper.writerFor(ShellScriptParameters.class);

        String json = writer.writeValueAsString(parameters);
        System.out.println("json = " + json);


        ObjectReader reader = objectMapper.readerFor(ShellScriptParameters.class);
//        ShellScriptParameters restoresParameters = reader.readValue(json);

//        ObjectMapper om = new ObjectMapper();
//        om.registerModule(new Jdk8Module());
//        om.enable(SerializationFeature.INDENT_OUTPUT);
        ShellScriptParameters restoresParameters = reader.readValue(json);
        System.out.println("restoresParameters = " + restoresParameters);

//        objectMapper.writerFor()

//        ByteOutputStream out = new ByteOutputStream();
//        JsonFactory factory = new JsonFactory();
//        JsonGenerator generator = factory.createGenerator(out);
//        generator.writeObject(parameters);


    }
}
