# Graal VM - Tracing Agent, Reflection and Records

A very simple application that uses the
([Jackson](https://github.com/FasterXML/jackson)) library, which relies on
reflection to convert object from JSON.

In this example the tracer didn't do a great job as further investigation is
required. The `native-image` command fails with the following exception.

```
Fatal error: com.oracle.svm.core.util.VMError$HostedError: com.oracle.svm.core.util.VMError$HostedError: New Method or Constructor found as reachable after static analysis: public java.lang.String demo.Quote.quote()
```

Technology stack used

- GraalVM 22.3.1 and Java 17
- Jackson 2.14.1

## Prerequisites

- GraalVM 22.3.1 and Java 17, or newer, is required to run this example

  ```shell
  $ java --version
  java 17.0.6 2023-01-17 LTS
  Java(TM) SE Runtime Environment GraalVM EE 22.3.1 (build 17.0.6+9-LTS-jvmci-22.3-b11)
  Java HotSpot(TM) 64-Bit Server VM GraalVM EE 22.3.1 (build 17.0.6+9-LTS-jvmci-22.3-b11, mixed mode, sharing)
  ```

  Install [SDKMAN](https://sdkman.io/)

  ```shell
  $ curl -s 'https://get.sdkman.io' | bash
  ```

  Install GraalVM CE Java 17 using SDKMAN

  ```shell
  $ sdk list java
  $ sdk install java 22.3.r17-grl
  ```

  Install GraalVM EE Java 17 using SDKMAN. The GraalVM EE is not available for
  download from SDKMAN and needs to be downloaded manually and added as an
  [SDKMAN local version](https://sdkman.io/usage#localversion).

  ```shell
  $ bash <(curl -sL https://get.graalvm.org/ee-token)
  $ bash <(curl -sL https://get.graalvm.org/jdk)
  $ sdk install java 22.3.r17ee-grl ./graalvm-ee-java17-22.3.1/Contents/Home
  ```

- `native-image` 22.3, or newer, is required to run this example

  ```shell
  $ gu list
  ComponentId              Version             Component name                Stability                     Origin
  ---------------------------------------------------------------------------------------------------------------------------------
  graalvm                  22.3.1              GraalVM Core                  Experimental
  native-image             22.3.1              Native Image                  Experimental                  gds.oracle.com
  ```

  or

  ```shell
  $ native-image --version
  GraalVM 22.3.1 Java 17 EE (Java Version 17.0.6+9-LTS-jvmci-22.3-b11)
  ```

  Install `native-image` using the
  [GraalVM Updater](https://www.graalvm.org/22.3/reference-manual/graalvm-updater/)

  ```shell
  $ gu install native-image
  ```

## Reproduce problem

- Create the application JAR file and copy the dependencies

  ```shell
  $ ./gradlew clean package
  ```

  (_Optional_) Verify that the JAR file was created and dependencies copied

  ```shell
  $ tree './build/libs'
  ./build/libs
  ├── app.jar
  ├── jackson-annotations-2.14.1.jar
  ├── jackson-core-2.14.1.jar
  └── jackson-databind-2.14.1.jar
  ```

- Run JAR file

  ```shell
  $ java \
    --class-path './build/libs/*' \
    demo.Main
  Quote[author=Albert Einstein, quote=Learn from yesterday, live for today, hope for tomorrow. The important thing is not to stop questioning.]
  ```

- Use the
  [assisted configuration with tracing agent](https://www.graalvm.org/22.1/reference-manual/native-image/Agent/) to
  create the reflection configuration (`reflect-config.json`) file

  ```
  -agentlib:native-image-agent=config-output-dir=<OUTPUT-PATH>
  ```

  Complete example

  ```shell
  $ java \
    --class-path './build/libs/*' \
    '-agentlib:native-image-agent=config-output-dir=./build/generated/main/resources/META-INF/native-image' \
    demo.Main
  Quote[author=Albert Einstein, quote=Learn from yesterday, live for today, hope for tomorrow. The important thing is not to stop questioning.]
  ```

  Note that GraalVM is required for the above command to work, otherwise you will get an error similar to the following.

  ```
  Error occurred during initialization of VM
  ...
  ```

  List the generated configuration

  ```shell
  $ tree './build/generated/main/resources'
  ./build/generated/main/resources
  └── META-INF
      └── native-image
          ├── agent-extracted-predefined-classes
          ├── jni-config.json
          ├── predefined-classes-config.json
          ├── proxy-config.json
          ├── reflect-config.json
          ├── resource-config.json
          └── serialization-config.json
  ```

  The `native-image` will look for these configuration at
  `META-INF/native-image`.

  Print the contents of the `reflect-config.json`

  ```shell
  $ more './build/generated/main/resources/META-INF/native-image/reflect-config.json' \
    | jq '[.[]|select(.name=="demo.Quote")][0]'
  ```

  Note that the `Quote` record only has the constructor and missing the accessor
  methods.

  ```json
  {
    "name": "demo.Quote",
    "allDeclaredFields": true,
    "queryAllDeclaredMethods": true,
    "queryAllDeclaredConstructors": true,
    "methods": [
      {
        "name": "<init>",
        "parameterTypes": [
          "java.lang.String",
          "java.lang.String"
        ]
      }
    ]
  }
  ```

- Recreate the application and include the generated configuration file to the
  classpath

  ```
  --class-path './build/libs/*:./build/generated/main/resources'
  ```

  Complete example

  ```shell
  $ native-image \
    '-H:Path=./build/native/nativeCompile' \
    -H:Class=demo.Main \
    -H:Name=app \
    --class-path './build/libs/*:./build/generated/main/resources' \
    --no-fallback \
    demo.Main
  ```

  **This fails, as the tracer didn't do a great job**!!

  ```
  ...
  [7/7] Creating image...
                                                                                    (0,0s @ 3,62GB)
  Fatal error: com.oracle.svm.core.util.VMError$HostedError: com.oracle.svm.core.util.VMError$HostedError: New Method or Constructor found as reachable after static analysis: public java.lang.String demo.Quote.quote()
  	at org.graalvm.nativeimage.builder/com.oracle.svm.core.util.VMError.shouldNotReachHere(VMError.java:72)
  	at org.graalvm.nativeimage.builder/com.oracle.svm.hosted.NativeImageGenerator.doRun(NativeImageGenerator.java:696)
  	at org.graalvm.nativeimage.builder/com.oracle.svm.hosted.NativeImageGenerator.run(NativeImageGenerator.java:535)
  	at org.graalvm.nativeimage.builder/com.oracle.svm.hosted.NativeImageGeneratorRunner.buildImage(NativeImageGeneratorRunner.java:403)
  	at org.graalvm.nativeimage.builder/com.oracle.svm.hosted.NativeImageGeneratorRunner.build(NativeImageGeneratorRunner.java:580)
  	at org.graalvm.nativeimage.builder/com.oracle.svm.hosted.NativeImageGeneratorRunner.main(NativeImageGeneratorRunner.java:128)
  Caused by: com.oracle.svm.core.util.VMError$HostedError: New Method or Constructor found as reachable after static analysis: public java.lang.String demo.Quote.quote()
  	at org.graalvm.nativeimage.builder/com.oracle.svm.core.util.VMError.shouldNotReachHere(VMError.java:68)
  	at org.graalvm.nativeimage.builder/com.oracle.svm.hosted.reflect.ReflectionFeature.getOrCreateAccessor(ReflectionFeature.java:121)
  	at org.graalvm.nativeimage.builder/com.oracle.svm.core.reflect.target.ExecutableAccessorComputer.transform(ExecutableAccessorComputer.java:43)
  	at org.graalvm.nativeimage.builder/com.oracle.svm.hosted.substitute.ComputedValueField.computeValue(ComputedValueField.java:343)
  	at org.graalvm.nativeimage.builder/com.oracle.svm.hosted.substitute.ComputedValueField.readValue(ComputedValueField.java:313)
  	at org.graalvm.nativeimage.builder/com.oracle.svm.core.meta.ReadableJavaField.readFieldValue(ReadableJavaField.java:38)
  	at org.graalvm.nativeimage.builder/com.oracle.svm.hosted.ameta.AnalysisConstantReflectionProvider.readValue(AnalysisConstantReflectionProvider.java:97)
  	at org.graalvm.nativeimage.builder/com.oracle.svm.hosted.meta.HostedField.readValue(HostedField.java:161)
  	at org.graalvm.nativeimage.builder/com.oracle.svm.hosted.image.NativeImageHeap.addObjectToImageHeap(NativeImageHeap.java:439)
  	at org.graalvm.nativeimage.builder/com.oracle.svm.hosted.image.NativeImageHeap.addObject(NativeImageHeap.java:295)
  	at org.graalvm.nativeimage.builder/com.oracle.svm.hosted.image.NativeImageHeap.processAddObjectWorklist(NativeImageHeap.java:598)
  	at org.graalvm.nativeimage.builder/com.oracle.svm.hosted.image.NativeImageHeap.addTrailingObjects(NativeImageHeap.java:198)
  	at org.graalvm.nativeimage.builder/com.oracle.svm.hosted.NativeImageGenerator.doRun(NativeImageGenerator.java:678)
  	... 4 more
  ```

  As mentioned before, the created `reflect-config.json` is missing two methods
  from the `Quote` record.

  ```shell
  $ more './build/generated/main/resources/META-INF/native-image/reflect-config.json' \
    | jq '[.[]|select(.name=="demo.Quote")][0]'
  ```

  Only the constructor is listed

  ```json
  {
    "name": "demo.Quote",
    "allDeclaredFields": true,
    "queryAllDeclaredMethods": true,
    "queryAllDeclaredConstructors": true,
    "methods": [
      {
        "name": "<init>",
        "parameterTypes": [
          "java.lang.String",
          "java.lang.String"
        ]
      }
    ]
  }
  ```

  We need to manually modify this entry and include the missing methods.

  ```diff
  --- ./build/generated/main/resources/META-INF/native-image/reflect-config.json	2023-01-22 19:48:44
  +++ ./src/patch/META-INF/native-image/reflect-config.json	2023-01-22 19:48:26
  @@ -12,7 +12,11 @@
     "allDeclaredFields":true,
     "queryAllDeclaredMethods":true,
     "queryAllDeclaredConstructors":true,
  -  "methods":[{"name":"<init>","parameterTypes":["java.lang.String","java.lang.String"] }]
  +  "methods":[
  +    {"name":"<init>","parameterTypes":["java.lang.String","java.lang.String"] },
  +    {"name":"quote","parameterTypes":[] },
  +    {"name":"author","parameterTypes":[] }
  +  ]
   },
   {
     "name":"java.lang.Class",
  ```

  Either modify the file manually, or apply the patch.

  ```shell
  $ patch \
    -u './build/generated/main/resources/META-INF/native-image/reflect-config.json' \
    -i './src/patch/META-INF/native-image/reflect-config.patch'
  ```

  Verify that the patch is applied

  ```shell
  $ more './build/generated/main/resources/META-INF/native-image/reflect-config.json' \
    | jq '[.[]|select(.name=="demo.Quote")][0]'
  ```

  The accessor methods are both included.

  ```json
  {
    "name": "demo.Quote",
    "allDeclaredFields": true,
    "queryAllDeclaredMethods": true,
    "queryAllDeclaredConstructors": true,
    "methods": [
      {
        "name": "<init>",
        "parameterTypes": [
          "java.lang.String",
          "java.lang.String"
        ]
      },
      {
        "name": "quote",
        "parameterTypes": []
      },
      {
        "name": "author",
        "parameterTypes": []
      }
    ]
  }
  ```

  Recreate the application and include the generated configuration file to the
  classpath

  ```shell
  $ native-image \
    '-H:Path=./build/native/nativeCompile' \
    -H:Class=demo.Main \
    -H:Name=app \
    --class-path './build/libs/*:./build/generated/main/resources' \
    --no-fallback \
    demo.Main
  ```

  Run the application

  ```shell
  $ './build/native/nativeCompile/app'
  Quote[author=Albert Einstein, quote=Learn from yesterday, live for today, hope for tomorrow. The important thing is not to stop questioning.]
  ```

## Using a class instead

Recreated the `Quote` record as a traditional class, with the same method
interface.

```java
package demo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public class Quote {
    private final String author;
    private final String quote;

    @JsonCreator
    public Quote(@JsonProperty("author") String author, @JsonProperty("quote") String quote) {
        this.author = author;
        this.quote = quote;
    }

    public String author() {
        return author;
    }

    public String quote() {
        return quote;
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        final Quote other = (Quote) object;
        return Objects.equals(author, other.author)
                && Objects.equals(quote, other.quote);
    }

    @Override
    public int hashCode() {
        return Objects.hash(author, quote);
    }

    @Override
    public String toString() {
        return "Quote[author=%s, quote=%s]".formatted(author, quote);
    }
}
```

While the same `reflect-config.json` file is created, that is, with just the
constructor, the class version works.
