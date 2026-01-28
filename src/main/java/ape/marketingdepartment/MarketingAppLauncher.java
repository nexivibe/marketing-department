package ape.marketingdepartment;

/**
 * Launcher class for the shaded JAR.
 *
 * JavaFX requires that the main class in a shaded/fat JAR does NOT extend
 * Application. This launcher class works around that restriction.
 *
 * To build the shaded JAR:
 *   mvn clean package
 *
 * To run the shaded JAR:
 *   java -jar target/marketing-department-1.0-SNAPSHOT-full.jar
 */
public class MarketingAppLauncher {

    public static void main(String[] args) {
        MarketingApp.main(args);
    }
}
