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

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.raml.model.Action;
import org.raml.model.ActionType;
import org.raml.model.Raml;
import org.raml.model.Resource;
import org.raml.model.Response;
import org.raml.model.parameter.QueryParameter;
import org.raml.parser.visitor.RamlDocumentBuilder;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import joptsimple.OptionParser;
import joptsimple.OptionSet;

/**
 * RAML is a programmer-friendly representation of an API, whose implementations
 * can be easily tested with RAML-tester To get a correct REST representation of
 * it, we convert it to the Apidoc format. This converter assumes that: 
 * - 1 there are no Resource Types defined in the source RAML file 
 * - 2 no type in Actions (it would be a resource type if there was one) 
 * - 3 there are no Traits defined in the source RAML file 
 * - 4 there is a top level schema defined for every root resource and return type 
 * in the RAML file. 
 * - 5 base URI must not end with a / 
 * - 6 responses must contain a schema: with the name of a previously defined schema
 * (see 4)
 *
 * Created by piero on 4/18/16.
 */
public class RAMLToApidocConverter {

    /**
     * This converter assumes that: 
     * - 1 there are no Resource Types defined in the source RAML file 
     * - 2 there are no Traits defined in the source RAML file 
     * - 3 there is a top level schema defined for every root resource in the RAML
     * file.
     *
     * @param URI a file: or http: resource
     * @return a Json Apidoc string
     */
    public String convert(String URI) {

        Raml raml = new RamlDocumentBuilder().build(URI); // new StringWriter(data), new File("").getPath() for a String

        version = raml.getVersion();

        Apidoc apidoc = new Apidoc();

        apidoc.name = raml.getTitle();
        apidoc.base_url = raml.getBaseUri();
        apidoc.description = raml.getDocumentation().toString();

        List<Map<String, String>> schemas = raml.getSchemas();

        apidoc.models = getModels(schemas);

        Map<String, Resource> ramlResources = raml.getResources();

        apidoc.resources = getResources(ramlResources);

        String result = gson.toJson(apidoc);

        return result;
    }

    public String getVersion(String uriString) throws IOException, URISyntaxException {

        String data;

        if (uriString.startsWith("http")) {

            CloseableHttpClient client = HttpClients.createDefault();
            HttpGet get = new HttpGet(uriString);
            CloseableHttpResponse response = client.execute(get);

            data = response.getEntity().toString();
        } else {
            URI uri = new URI(uriString);
            data = new String(Files.readAllBytes(Paths.get(uri.getRawPath())));
        }

        Yaml yaml = new Yaml();

        Map raml = (Map) yaml.load(new StringReader(data));

        return (String) raml.get("version");
    }

    private class JsonSchema {

        String description;
        List<String> required;
        Map<String, Map<String, String>> properties;
    }

    private Map<String, Apidoc.Model> getModels(List<Map<String, String>> schemas) {

        Map<String, Apidoc.Model> result = new HashMap<>();

        for (Map<String, String> schema : schemas) {

            String modelName = schema.keySet().iterator().next();

            String schemaString = schema.get(modelName);

            JsonSchema jsonSchema = gson.fromJson(schemaString, JsonSchema.class);

            Apidoc.Model model = new Apidoc().new Model();

            model.description = jsonSchema.description;

            List<String> requiredFields = jsonSchema.required;

            Set<String> fieldNames = jsonSchema.properties.keySet();

            List<Apidoc.Field> fields = new ArrayList<>();

            for (String fieldName : fieldNames) {

                Apidoc.Field field = new Apidoc().new Field();

                field.name = fieldName;
                field.type = jsonSchema.properties.get(fieldName).get("type");
                if (requiredFields.contains(fieldName)) {
                    field.required = true;
                }
                fields.add(field);
            }
            model.fields = fields;
            result.put(modelName, model);
        }
        return result;
    }

    /**
     * Scans RAML resources, in the form of /xxx:GET, /xxx/yyy:GET/POST,
     * /zzz:GET, /zzz/jjjj:POST/DELETE into an Apidoc RESTful representation of
     * it, i.e resources/xxx: ops GET path /xxx, GET path /xxx/yyy, POST path
     * /xxx/yyy, resources/zzz: ops GET path /zzz, ops POST path /zzz/jjjj,
     * DELETE path /zzz/jjjj
     *
     * @return a Map of Apidoc resources (top-level resources)
     */
    private Map<String, Apidoc.Resource> getResources(Map<String, Resource> ramlResources) {

        Map<String, Apidoc.Resource> apidocResources = new HashMap<>();

        Set<String> ramlResourceNames = ramlResources.keySet();

        for (String ramlResourceName : ramlResourceNames) {

            Resource ramlResource = ramlResources.get(ramlResourceName);

            String resourceName = ramlResourceName.replaceFirst("/", "");

            Apidoc.Resource resource = new Apidoc().new Resource();

            resource.description = ramlResource.getDescription();
            resource.path = ramlResourceName;
            resource.operations = walkSubresources(ramlResource);

            apidocResources.put(resourceName, resource);
        }
        return apidocResources;
    }

    private List<Apidoc.Operation> walkSubresources(Resource rootResource) {

        List<Apidoc.Operation> operations = new ArrayList<>();

        class NameAndResource {

            public NameAndResource(String name, Resource resource) {
                this.name = name;
                this.resource = resource;
            }

            public String name;
            public Resource resource;
        }

        Queue<NameAndResource> bfsAccumulator = new LinkedList<>();

        // path is specified only once for the resource. Subpaths will be only specified if parameters (:xxx), see getOperations()
        bfsAccumulator.add(new NameAndResource("", rootResource));

        while (!bfsAccumulator.isEmpty()) {

            NameAndResource nr = bfsAccumulator.remove();

            operations.addAll(getOperations(nr.name, nr.resource));

            Map<String, Resource> subresources = nr.resource.getResources();

            for (String resourceName : subresources.keySet()) {
                bfsAccumulator.add(new NameAndResource(nr.name + resourceName, subresources.get(resourceName)));
            }

        }

        operations.sort((operation1, operation2)
                -> {
            if (operation1.path == null) {
                return 1;
            }
            if (operation2.path == null) {
                return -1;
            }

            return operation1.path.compareTo(operation2.path);
        }
        );

        return operations;
    }

    private List<Apidoc.Operation> getOperations(String resourceName, Resource ramlResource) {

        List<Apidoc.Operation> operations = new ArrayList<>();

        Map<ActionType, Action> ramlActions = ramlResource.getActions();

        Set<ActionType> ramlActionTypes = ramlActions.keySet();

        for (ActionType ramlActionType : ramlActionTypes) {

            Action ramlAction = ramlActions.get(ramlActionType);

            Apidoc.Operation operation = new Apidoc().new Operation();

            operation.method = ramlActionType.name();
            operation.description = ramlAction.getDescription();

            // only specify a path for operation if it is a parameter
            if (resourceName.contains("{")) {
                operation.path = resourceName.replaceAll("\\{", ":").replaceAll("}", "");
            }

            Map<String, QueryParameter> ramlQueryParameters = ramlAction.getQueryParameters();

            operation.parameters = getQueryParameters(ramlQueryParameters);

            operation.responses = getResponses(ramlAction);

            operations.add(operation);
        }

        return operations;
    }

    private Map<String, Apidoc.Response> getResponses(Action ramlAction) {

        Map<String, Response> ramlResponses = ramlAction.getResponses();

        Map<String, Apidoc.Response> responses = new HashMap<>();

        for (String responseCode : ramlResponses.keySet()) {

            Apidoc.Response response = new Apidoc().new Response();

            Response ramlResponse = ramlResponses.get(responseCode);

            response.description = ramlResponse.getDescription();
            response.type = ramlResponse.getBody().get("application/json").getSchema();

            responses.put(responseCode, response);
        }

        return responses;
    }

    private List<Apidoc.Parameter> getQueryParameters(Map<String, QueryParameter> ramlQueryParameters) {

        List<Apidoc.Parameter> parameters = new ArrayList<>();

        Set<String> ramlParameterNames = ramlQueryParameters.keySet();

        for (String ramlParameterName : ramlParameterNames) {

            QueryParameter ramlQueryParameter = ramlQueryParameters.get(ramlParameterName);
            Apidoc.Parameter parameter = new Apidoc().new Parameter();

            parameter.name = ramlParameterName;
            parameter.description = ramlQueryParameter.getDescription();
            parameter.example = ramlQueryParameter.getExample();
            parameter.type = ramlQueryParameter.getType().name();
            parameter.required = ramlQueryParameter.isRequired();

            parameters.add(parameter);
        }

        return parameters;
    }

    public static void main(String[] args) throws IOException, URISyntaxException {

        OptionParser optionParser = new OptionParser("h");

        optionParser.accepts("raml").withRequiredArg();
        optionParser.accepts("apidoc").withRequiredArg();
        optionParser.accepts("version");
        optionParser.accepts("help");

        OptionSet optionSet = optionParser.parse(args);

        if (optionSet.has("help") || optionSet.has("h")) {
            System.out.println("Usage: -raml uri of raml "
                    + "-apidoc name of apidoc file /n"
                    + "or /"
                    + "-raml uri of raml -version");
            System.exit(0);
        }

        if (!optionSet.has("raml")) {
            System.out.println("Missing raml option");
            System.exit(-1);
        }

        URI uri = new URI((String) optionSet.valueOf("raml"));

        if (uri.getScheme() == null) {
            System.out.println("Bad raml uri - should be file:// or http:// ");
            System.exit(-1);
        }

        RAMLToApidocConverter instance = new RAMLToApidocConverter();

        if (optionSet.has("version")) {
            System.out.print(instance.getVersion((String) optionSet.valueOf("raml")));
            return;
        }

        String data = instance.convert((String) optionSet.valueOf("raml"));
        if (optionSet.has("apidoc")) {
            Files.write(Paths.get((String) optionSet.valueOf("apidoc")), data.getBytes());
        } else {
            System.out.print(data);
        }

    }

    private Gson gson = new Gson();
    private String version;
}
