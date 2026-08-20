package br.com.ecad.captacao.sgastatussync;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = { MongoAutoConfiguration.class, MongoDataAutoConfiguration.class })
@EnableScheduling
public class EcadSgaStatusSyncApplication {
    public static void main(String[] args) {
        SpringApplication.run(EcadSgaStatusSyncApplication.class, args);
    }
}
