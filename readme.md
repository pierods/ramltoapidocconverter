
# A RAML to Apidoc converter.

It has the following limitations:

 -  there are no Resource Types defined in the source RAML file 
 -  no type in Actions (it would be a resource type if there was one) 
 -  there are no Traits defined in the source RAML file 
 -  there is a top level schema defined for every root resource and return type  in the RAML file. 
 -  base URI must not end with a / 
 -  responses must contain a schema: with the name of a previously defined schema (see 4)
 
 Usage:
 
 java -jar ramltoapidocconverter-1.0.jar -raml <uri of raml (file)> [-apidoc <name of apidoc output file>]
 
 if output file is omitted, it will write to stdout
 
 or
 
 java -jar ramltoapidocconverter-1.0.jar -raml <uri of raml (file)> -version
 
 will output the version of the raml file.
 
 Possible improvements:
 - more test coverage
 - support for Resource Types...
