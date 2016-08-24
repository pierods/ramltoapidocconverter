package com.github.pierods.ramltoapidocconverter;

import com.google.gson.Gson;

import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;


/**
 * Created by piero on 4/25/16.
 */
public class RAMLToApidocConverterTest {
    @Test
    public void convert() throws Exception {

        RAMLToApidocConverter converter = new RAMLToApidocConverter();

        Gson gson = new Gson();

        Apidoc apidoc = gson.fromJson(converter.convert("file://" + Paths.get(new java.io.File(".").getCanonicalPath(), "src/test/resources/hello.raml")), Apidoc.class);

        Set<String> models = new HashSet<>();
        models.add("helloes");

        assertEquals(models, apidoc.models.keySet());

    }

    @Test
    public void getVersion() throws Exception {

        RAMLToApidocConverter converter = new RAMLToApidocConverter();

        assertEquals("0.1.1", converter.getVersion("file://" + Paths.get(new java.io.File(".").getCanonicalPath(), "src/test/resources/version.raml")));

    }

}