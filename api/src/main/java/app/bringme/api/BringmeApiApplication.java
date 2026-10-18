package app.bringme.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BringmeApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(BringmeApiApplication.class, args);
    }
}
