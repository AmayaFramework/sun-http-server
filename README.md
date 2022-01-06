# sun-http-server [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.github.amayaframework/http-server/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.github.amayaframework/http-server/badge.svg)

A repackaged and refactored sun http server, created in the original form by Oracle and formerly embedded in the jdk. 
Distributed under the GNU v2 license.

This server, built on non-blocking sockets, is a really good lightweight solution that has everything you need 
for full use in various projects. Up to this point, it was ignored by most of the community due to its location 
in sun packages. Now this restriction has been removed.

## Getting Started

To install it, you will need any version of the JDK no older than version 8 and 
possibly an automatic build system (Gradle, Maven, etc) to automatically 
install the dependency.

## Installing

### Gradle dependency

```Groovy
dependencies {
    implementation group: 'io.github.amayaframework', name: 'http-server', version: 'LATEST'
}
```

### Maven dependency
```
<dependency>
    <groupId>io.github.amayaframework</groupId>
    <artifactId>http-server</artifactId>
    <version>LATEST</version>
</dependency>
```

End with an example of getting some data out of the system or using it for a little demo

## Usage example

The code below will start the server associated with the address localhost:8000.
There will be one handler on the server responding to a route starting with /hello. 
The response will contain code 200 and an empty body.
He will respond to all other requests with the code 404.

```Java
public class Server {
    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.createContext("/hello", exchange -> {
            exchange.sendResponseHeaders(HttpCode.OK, 0);
            exchange.close();
        });
        server.start();
    }
}
```

## Built With

* [Gradle](https://gradle.org) - Dependency Management

## Authors
* **Oracle Corporation** - *Main work* - [Oracle](https://www.oracle.com)
* **RomanQed** - *Repackaging and refactoring* - [RomanQed](https://github.com/RomanQed)

See also the list of [contributors](https://github.com/AmayaFramework/sun-http-server/contributors) who participated in this project.

## License

This project is licensed under the GNU GENERAL PUBLIC LICENSE Version 2 - see the [LICENSE](LICENSE) file for details

Also, according to the requirements, the original inserts of Oracle 
license headers are preserved in the original source files.

## Acknowledgments

Thanks to all the sun developers who participated in the creation of this server.
