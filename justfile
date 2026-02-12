# Build and release the marketing department JAR

release:
    mvn clean package
    mkdir -p /home/ubuntu/releases
    cp target/marketing.jar /home/ubuntu/releases/
