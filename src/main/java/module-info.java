module ape.marketingdepartment {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;
    requires java.net.http;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires org.kordamp.bootstrapfx.core;

    requires flexmark;
    requires flexmark.util.ast;
    requires flexmark.util.data;

    // Selenium WebDriver
    requires org.seleniumhq.selenium.api;
    requires org.seleniumhq.selenium.chrome_driver;
    requires org.seleniumhq.selenium.support;
    requires io.github.bonigarcia.webdrivermanager;

    opens ape.marketingdepartment to javafx.fxml;
    opens ape.marketingdepartment.controller to javafx.fxml;

    exports ape.marketingdepartment;
    exports ape.marketingdepartment.model;
    exports ape.marketingdepartment.controller;
    exports ape.marketingdepartment.service;
    exports ape.marketingdepartment.service.ai;
    exports ape.marketingdepartment.service.browser;
    exports ape.marketingdepartment.service.publishing;
}
