# Modernisation markers

This file holds the static marker knowledge of the modernisation analysis. It names the namespaces that mark a
legacy dependency, the version ranges that mark a dependency at the end of its life, the annotations that mark an
entry point, the interfaces that mark a service provider interface, and the catalogue of the modernisation patterns
an anchor may be matched against.

Two statements hold for the whole file:

* The legacy namespaces and the version ranges are a snapshot of a date. They describe the state of the ecosystem
  on 22 October 2026 and the analysis never refreshes them. A namespace that was renamed, or a version range that
  reached the end of its life after that date, is not noticed, and the analysis states this limit in its result.
* The catalogue of the modernisation patterns is the only source of the patterns an anchor may be matched against. A
  pattern that is not named in the catalogue is not used, and an anchor for which no pattern of the catalogue fits
  carries an empty list of candidates and the reason.

## Legacy namespaces

The specifications of the Jakarta EE platform moved from the `javax` namespace to the `jakarta` namespace. The rename
came with Jakarta EE 10 in 2022; Jakarta EE 9 of 2020 still used the `javax` namespace. A dependency that is imported
under one of the `javax` namespaces of the table below is a legacy dependency.

| Legacy namespace | Jakarta counterpart | Specification | Generation of the Jakarta counterpart |
|------------------|---------------------|---------------|-----------------------------------------|
| javax.annotation | jakarta.annotation | Common Annotations | 1.3 to 2.x |
| javax.activation | jakarta.activation | Activation | 1.2 to 2.x |
| javax.batch | jakarta.batch | Batch | 1.0 to 2.0 |
| javax.decorator | jakarta.decorator | Decorators | 1.2 to 2.0 |
| javax.ejb | jakarta.ejb | Enterprise Beans | 3.2 to 4.0 |
| javax.el | jakarta.el | Expression Language | 3.0 to 5.0 |
| javax.enterprise | jakarta.enterprise | Contexts and Dependency Injection | 3.1 to 4.0 |
| javax.faces | jakarta.faces | Faces | 2.3 to 4.0 |
| javax.inject | jakarta.inject | Dependency Injection | 1.0 to 2.0 |
| javax.jms | jakarta.jms | Messaging | 2.0 to 3.1 |
| javax.json | jakarta.json | JSON Processing | 1.1 to 2.1 |
| javax.mail | jakarta.mail | Mail | 1.6 to 2.1 |
| javax.persistence | jakarta.persistence | Persistence | 2.2 to 3.1 |
| javax.resource | jakarta.resource | Resource Handling | 1.7 to 2.0 |
| javax.security.auth | jakarta.security.auth | Authentication | 1.x to 2.0 |
| javax.security.enterprise | jakarta.security.enterprise | Authorization | 1.x to 2.0 |
| javax.servlet | jakarta.servlet | Servlet | 3.1 to 5.0 |
| javax.transaction | jakarta.transaction | Transactions | 1.2 to 2.0 |
| javax.validation | jakarta.validation | Bean Validation | 2.0 to 3.1 |
| javax.websocket | jakarta.websocket | WebSocket | 1.1 to 2.1 |
| javax.ws.rs | jakarta.ws.rs | RESTful Web Services | 2.1 to 3.1 |
| javax.xml.bind | jakarta.xml.bind | XML Binding | 2.3 to 4.0 |
| javax.xml.soap | jakarta.xml.soap | SOAP | 1.2 to 3.0 |
| javax.xml.ws | jakarta.xml.ws | Web Services | 2.3 to 4.0 |

Only the namespaces of this table mark a legacy dependency. The `javax` packages that belong to the Java SE platform,
among them `javax.crypto`, `javax.imageio`, `javax.lang.model`, `javax.naming`, `javax.net`, `javax.script`,
`javax.security`, `javax.sound`, `javax.sql`, `javax.tools` and `javax.xml` of the XML processing api, are part of
the platform and stay where they are, and a `javax` import of such a package is not a marker of a legacy
dependency. The two exceptions are the sub packages `javax.security.auth` and `javax.security.enterprise`, which
belong to the Jakarta EE platform and are in the table above.

## Version ranges at the end of the life of a dependency

A dependency whose version lies in one of the ranges below is at the end of its life. The range names the version
the dependency is pinned to, the note names what ended, and the replacement names the version to move to. The ranges
are part of the snapshot of 22 October 2026.

| Dependency | Version range at end of life | Note | Replacement |
|------------|-----------------------------|------|-------------|
| Java SE | 8 | the public updates of the vendor ended in March 2022, other vendors still ship free updates | 11 or later |
| Spring Framework | 4.x | the open source support ended on 31 December 2020 | 5.x or later |
| Spring Framework | 5.0 to 5.2 | these lines no longer receive open source updates | 5.3 or later |
| Spring Framework | 5.3.x | the open source support ended on 31 December 2024 | 6.x, which needs Java 17 |
| Spring Boot | 1.x | the open source support ended on 31 August 2019 | 2.x or later |
| Spring Boot | 2.0 to 2.6 | these lines no longer receive open source updates, the last of them ended in November 2022 | 2.7 or later |
| Spring Boot | 2.7.x | the open source support ended on 24 November 2023 | 3.x, which needs Java 17 |
| Spring Security | 4.x | these lines no longer receive open source updates | 5.x or later |
| Hibernate ORM | 5.0 to 5.6 | the last open source update of the 5.x line came in 2023 | 6.x or later |
| Log4j | 1.2.x | the project announced the end of life of the 1.2 line for August 2021 | 2.x |
| Log4j | 2.0 to 2.16 | the versions before 2.17.1 carry known unfixed vulnerabilities | 2.17.1 or later |
| Mockito | 1.x and 2.x | these lines no longer receive open source updates | 3.x or later |
| JUnit | 4.x | the line is in maintenance and receives no new features | JUnit 5 |
| Apache Commons Collections | 3.x | the line was superseded by the 4.x line | 4.x |
| Jakarta EE, the javax generation | Servlet 3.0 to 3.1, Persistence 2.0 to 2.2, Bean Validation 2.0, RESTful Web Services 2.0 to 2.1, Dependency Injection 3.0 to 3.1 | the generation was superseded by the jakarta generation from Jakarta EE 10 in 2022 | the jakarta.* generation |
| Jersey with javax.ws.rs | 2.x | the javax generation was superseded by the 3.x line | 3.x or later |

## Annotations that mark an entry point

A class that carries one of the annotations below is an entry point, because a caller of the system reaches it
through the framework. The annotation is the marker, the version of the framework behind it is not.

| Annotation | Framework | What the entry point is |
|------------|-----------|--------------------------|
| org.springframework.boot.autoconfigure.SpringBootApplication | Spring Boot | the application itself, the place the process starts |
| org.springframework.boot.autoconfigure.EnableAutoConfiguration | Spring Boot | the auto configuration of the application |
| org.springframework.stereotype.Controller | Spring MVC | a class that handles web requests |
| org.springframework.web.servlet.Controller | Spring MVC, up to Spring 5 | a class that handles web requests, the interface was removed in Spring 6 |
| org.springframework.web.bind.annotation.RestController | Spring MVC | a class that serves a REST api |
| org.springframework.web.bind.annotation.RequestMapping | Spring MVC | a mapped request path, on the class or on the method |
| org.springframework.stereotype.Component | Spring | a managed bean, a weaker marker than the ones above |
| org.springframework.stereotype.Service | Spring | a managed bean, a weaker marker than the ones above |
| org.springframework.stereotype.Repository | Spring | a managed bean, a weaker marker than the ones above |
| org.springframework.scheduling.annotation.Scheduled | Spring | a job that the container starts on a schedule |
| org.springframework.context.event.EventListener | Spring | a handler the container calls when an event arrives |
| org.springframework.amqp.rabbit.annotation.RabbitListener | Spring AMQP | a consumer of a queue |
| org.springframework.kafka.annotation.KafkaListener | Spring Kafka | a consumer of a topic |
| org.springframework.batch.core.configuration.annotation.EnableBatchProcessing | Spring Batch | the job launcher of the batch |
| jakarta.ws.rs.Path | Jakarta REST | the resource class that serves the endpoint |
| javax.ws.rs.Path | RESTful Web Services, the javax generation | the resource class that serves the endpoint |
| jakarta.servlet.annotation.WebServlet | Jakarta Servlet | a servlet that the container routes to |
| javax.servlet.annotation.WebServlet | Servlet, the javax generation | a servlet that the container routes to |
| jakarta.websocket.server.ServerEndpoint | Jakarta WebSocket | an endpoint the container opens a connection to |
| javax.websocket.server.ServerEndpoint | WebSocket, the javax generation | an endpoint the container opens a connection to |

## Interfaces that mark a service provider interface

A class that implements one of the interfaces below provides a service that the platform or the container loads
through a service file or through a registry. Such a class is reached by the platform rather than by a caller of the
project, which makes it an entry point that no import of the project shows.

| Interface | Kind of service | What the provider supplies |
|-----------|-----------------|----------------------------|
| java.util.spi.ToolProvider | Tool chain | a compiler or another tool of the platform |
| java.util.spi.ResourceBundleProvider | Platform service | the resource bundles of a locale |
| java.util.spi.ResourceBundleControlProvider | Platform service | the control of the resource bundle loading |
| java.sql.Driver | Database access | a driver for a database |
| javax.annotation.processing.Processor | Compile time processing | an annotation processor that runs during the build |
| javax.script.ScriptEngineFactory | Scripting | an engine for a scripting language |
| javax.naming.spi.InitialContextFactory | Naming and directory | the initial context of a naming service |
| javax.naming.spi.ObjectFactory | Naming and directory | an object that a naming service builds |
| javax.naming.spi.DirObjectFactory | Naming and directory | an object that a directory service builds |
| javax.naming.spi.StateFactory | Naming and directory | the state of an object of a naming service |
| javax.print.PrintService | Printing | a print service of the platform |
| java.lang.instrument.ClassFileTransformer | Byte code instrumentation | a transformer of the class files at runtime |
| javax.xml.crypto.dsig.XMLValidateContext | XML signature | the validation of a signed document |

A few well known service providers of the platform extend an abstract base class instead of implementing an
interface, and they are therefore found as a super class and not as an implemented interface. The base classes are
`java.security.Provider`, `javax.net.ssl.SSLContextSpi`, `java.util.spi.LocaleServiceProvider`,
`java.util.spi.CurrencyNameProvider`, `java.util.spi.TimeZoneNameProvider`, `java.util.spi.CalendarDataProvider`,
`java.nio.charset.spi.CharsetProvider`, `java.nio.file.spi.FileSystemProvider`,
`java.net.spi.InetAddressResolverProvider`, `java.net.spi.URLStreamHandlerProvider`,
`javax.xml.transform.TransformerFactory`, `javax.xml.parsers.SAXParserFactory`,
`javax.xml.parsers.DocumentBuilderFactory`, `javax.xml.stream.XMLInputFactory`,
`javax.xml.validation.SchemaFactory`, `javax.xml.datatype.DatatypeFactory`,
`javax.imageio.spi.ImageReaderSpi`, `javax.imageio.spi.ImageWriterSpi` and
`javax.sound.sampled.spi.AudioFileReader`.

## Catalogue of the modernisation patterns

The catalogue holds the six patterns an anchor may be matched against. The name of a pattern is the name to use in
the result, and the three columns beside it tell the reader what the pattern is, when it fits and what would make
it the wrong choice.

| Name | What it is | When it fits | What would make it the wrong choice |
|------|------------|--------------|--------------------------------------|
| strangler-fig | The behaviour of a legacy part is replaced piece by piece, and every call is routed to the new implementation until the old one has no caller left. | A subsystem that is reached through a boundary that can be redirected, such as a request path, a message or a table, and that stays in use for a long time. | The legacy part cannot be isolated behind a routing point, its callers sit inside the same process without a seam, or the part is small enough to be replaced in one go. |
| branch-by-abstraction | The abstract behaviour of the legacy part is named as an interface, the legacy code implements that interface first, and the new code then replaces it behind the same abstraction. | A part with an abstraction that can be named, tests around it, and a behaviour that has to change without the callers changing. | No abstraction can be named that the callers would use, the callers are spread everywhere, or the change cannot be written twice, once as an adapter and once as the new implementation. |
| module-extraction | A cohesive part of a module moves into a module of its own, behind an interface, and is built, tested and released on its own. | A cohesive area with few dependencies in both directions, with its own tests, and a life of its own. | The area shares its data and its code with the rest of the module, the coupling runs in both directions, or there are no tests to catch the cut. |
| anticorruption-layer | A layer in front of a foreign or a legacy model translates it into the language of the own domain, so that the foreign model does not spread inwards. | An external system or a legacy model with a vocabulary of its own that has to stay for a long time. | The foreign model is the part that is being replaced, the translation stays a pass through, or the own domain does not need a model of its own. |
| horizontal-lift | A capability that several modules need moves up into a shared component or a shared service, so that it is built once and changed once. | The same capability is implemented in several modules, or several modules need the same change. | The uses differ in more than the implementation, the shared component would need a variant per caller, or the coupling it creates is larger than the duplication it removes. |
| retirement | A capability is taken out of the system, together with its data, its configuration and its callers. | A capability with no caller left, a duplicate of another one, or a need that is gone. | The callers are only apparently unused, the data has to be kept for audit or for legal reasons, or the removal is only a step inside a larger replacement. |
