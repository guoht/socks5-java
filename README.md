# socks5-java
Simple socks5 proxy of Java.

## socks5-java-bio
Blocking I/O socks5-java with pure Java implementation.

## socks5-java-netty
Non-blocking I/O socks5-java with netty implementation.

# Build
```
# Get source code
git clone https://github.com/Bpazy/socks5-java && cd socks5-java

# Build multiple modules
mvn package

# Run socks5-java-bio
java -jar ./socks5-java-bio/target/socks5-java-bio-0.0.1-jar-with-dependencies.jar

# Run socks5-java-netty
java -jar ./socks5-java-netty/target/socks5-java-netty-0.0.1-jar-with-dependencies.jar
```
