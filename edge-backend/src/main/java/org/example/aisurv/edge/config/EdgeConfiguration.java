package org.example.aisurv.edge.config;

import org.example.aisurv.camera.CameraApplicationService;
import org.example.aisurv.camera.LocalCameraApplicationService;
import org.example.aisurv.persistence.ApplicationPersistence;
import org.example.aisurv.persistence.DatabaseSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EdgeConfiguration {
    @Bean(destroyMethod = "close")
    ApplicationPersistence applicationPersistence() {
        return new ApplicationPersistence(DatabaseSettings.fromEnvironment());
    }

    @Bean
    CameraApplicationService cameraApplicationService(ApplicationPersistence persistence) {
        return new LocalCameraApplicationService(persistence);
    }

    @Bean
    EdgeBindingPolicy edgeBindingPolicy(@Value("${server.address:127.0.0.1}") String bindAddress) {
        return new EdgeBindingPolicy(bindAddress);
    }
}
