package com.gtublog.shared.persistence;

import java.util.Arrays;
import java.util.LinkedHashSet;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class JpaInitializationConfiguration {

    @Bean
    static BeanFactoryPostProcessor entityManagerDependsOnFlywayInitializer() {
        return JpaInitializationConfiguration::configureEntityManagerDependency;
    }

    private static void configureEntityManagerDependency(ConfigurableListableBeanFactory beanFactory) {
        if (!beanFactory.containsBeanDefinition("entityManagerFactory")
                || !beanFactory.containsBeanDefinition("flywayInitializer")) {
            return;
        }

        var entityManagerFactory = beanFactory.getBeanDefinition("entityManagerFactory");
        var dependencies = new LinkedHashSet<String>();
        var existingDependencies = entityManagerFactory.getDependsOn();
        if (existingDependencies != null) {
            dependencies.addAll(Arrays.asList(existingDependencies));
        }
        dependencies.add("flywayInitializer");
        entityManagerFactory.setDependsOn(dependencies.toArray(String[]::new));
    }
}
