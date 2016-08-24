/**
    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.

 */
package com.github.pierods.ramltoapidocconverter;

import com.google.gson.Gson;

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